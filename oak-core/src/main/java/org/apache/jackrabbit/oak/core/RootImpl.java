/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.core;

import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.BlobFactory;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.QueryEngine;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.TreeLocation;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.plugins.index.diffindex.UUIDDiffIndexProviderWrapper;
import org.apache.jackrabbit.oak.query.QueryEngineImpl;
import org.apache.jackrabbit.oak.security.authentication.SystemSubject;
import org.apache.jackrabbit.oak.spi.commit.CommitHook;
import org.apache.jackrabbit.oak.spi.commit.CompositeEditorProvider;
import org.apache.jackrabbit.oak.spi.commit.CompositeHook;
import org.apache.jackrabbit.oak.spi.commit.EditorHook;
import org.apache.jackrabbit.oak.spi.commit.EmptyHook;
import org.apache.jackrabbit.oak.spi.commit.PostValidationHook;
import org.apache.jackrabbit.oak.spi.commit.ValidatorProvider;
import org.apache.jackrabbit.oak.spi.observation.ChangeExtractor;
import org.apache.jackrabbit.oak.spi.query.CompositeQueryIndexProvider;
import org.apache.jackrabbit.oak.spi.query.QueryIndexProvider;
import org.apache.jackrabbit.oak.spi.security.OpenSecurityProvider;
import org.apache.jackrabbit.oak.spi.security.SecurityConfiguration;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.PermissionProvider;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStateDiff;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.spi.state.NodeStoreBranch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.jackrabbit.oak.commons.PathUtils.getName;
import static org.apache.jackrabbit.oak.commons.PathUtils.getParentPath;
import static org.apache.jackrabbit.oak.plugins.memory.EmptyNodeState.EMPTY_NODE;

public class RootImpl implements Root {

    /**
     * Disable checks for invalid trees.
     * FIXME: remove once OAK-690 and dependencies are fixed
     */
    static final boolean OAK_690 = Boolean.getBoolean("OAK-690");

    /**
     * Number of {@link #updated} calls for which changes are kept in memory.
     */
    private static final int PURGE_LIMIT = Integer.getInteger("oak.root.purgeLimit", 100);

    /**
     * The underlying store to which this root belongs
     */
    private final NodeStore store;

    private final String workspaceName;

    private final CommitHook hook;

    private final Subject subject;

    private final SecurityProvider securityProvider;

    private final QueryIndexProvider indexProvider;

    /**
     * Current branch this root operates on
     */
    private NodeStoreBranch branch;

    /** Sentinel for the next move operation to take place on the this root */
    private Move lastMove = new Move();

    /**
     * Current root {@code Tree}
     */
    private TreeImpl rootTree;

    /**
     * Number of {@link #updated} occurred so since the last
     * purge.
     */
    private int modCount;

    private PermissionProvider permissionProvider;

    /**
     * New instance bases on a given {@link NodeStore} and a workspace
     *
     * @param store            node store
     * @param hook             the commit hook
     * @param workspaceName    name of the workspace
     * @param subject          the subject.
     * @param securityProvider the security configuration.
     * @param indexProvider    the query index provider.
     */
    public RootImpl(NodeStore store,
                    CommitHook hook,
                    String workspaceName,
                    Subject subject,
                    SecurityProvider securityProvider,
                    QueryIndexProvider indexProvider) {
        this.store = checkNotNull(store);
        this.hook = checkNotNull(hook);
        this.workspaceName = checkNotNull(workspaceName);
        this.subject = checkNotNull(subject);
        this.securityProvider = checkNotNull(securityProvider);
        this.indexProvider = indexProvider;

        branch = this.store.branch();
        rootTree = new TreeImpl(this, lastMove);
    }

    // TODO: review if these constructors really make sense and cannot be replaced.
    public RootImpl(NodeStore store) {
        this(store, EmptyHook.INSTANCE);
    }

    public RootImpl(NodeStore store, CommitHook hook) {
        // FIXME: define proper default or pass workspace name with the constructor
        this(store, hook, Oak.DEFAULT_WORKSPACE_NAME, SystemSubject.INSTANCE,
                new OpenSecurityProvider(), new CompositeQueryIndexProvider());
    }

    /**
     * Oak level variant of {@link org.apache.jackrabbit.oak.api.ContentSession#getLatestRoot()}
     * to be used when no {@code ContentSession} is available.
     *
     * @return A new Root instance.
     * @see org.apache.jackrabbit.oak.api.ContentSession#getLatestRoot()
     */
    public Root getLatest() {
        checkLive();
        RootImpl root = new RootImpl(store, hook, workspaceName, subject, securityProvider, getIndexProvider()) {
            @Override
            protected void checkLive() {
                RootImpl.this.checkLive();
            }
        };
        return root;
    }

    /**
     * Called whenever a method on this instance or on any {@code Tree} instance
     * obtained from this {@code Root} is called. This default implementation
     * does nothing. Sub classes may override this method and throw an exception
     * indicating that this {@code Root} instance is not live anymore (e.g. because
     * the session has been logged out already).
     */
    protected void checkLive() {

    }

    //---------------------------------------------------------------< Root >---
    @Override
    public boolean move(String sourcePath, String destPath) {
        checkLive();
        TreeImpl source = rootTree.getTree(sourcePath);
        if (source == null) {
            return false;
        }
        TreeImpl destParent = rootTree.getTree(getParentPath(destPath));
        if (destParent == null) {
            return false;
        }

        String destName = getName(destPath);
        if (destParent.hasChild(destName)) {
            return false;
        }

        purgePendingChanges();
        source.moveTo(destParent, destName);
        boolean success = branch.move(sourcePath, destPath);
        reset();
        if (success) {
            getTree(getParentPath(sourcePath)).updateChildOrder();
            getTree(getParentPath(destPath)).updateChildOrder();
        }

        lastMove = lastMove.setMove(sourcePath, destParent, destName);
        return success;
    }

    @Override
    public boolean copy(String sourcePath, String destPath) {
        checkLive();
        purgePendingChanges();
        boolean success = branch.copy(sourcePath, destPath);
        reset();
        if (success) {
            getTree(getParentPath(destPath)).updateChildOrder();
        }
        return success;
    }

    @Override
    public TreeImpl getTree(String path) {
        checkLive();
        return rootTree.getTree(path);
    }

    @Override
    public TreeLocation getLocation(String path) {
        checkLive();
        checkArgument(PathUtils.isAbsolute(path), "Not an absolute path: " + path);
        return rootTree.getLocation().getChild(path.substring(1));
    }

    @Override
    public void rebase() {
        checkLive();
        if (!store.getRoot().equals(rootTree.getBaseState())) {
            purgePendingChanges();
            branch.rebase();
            rootTree = new TreeImpl(this, lastMove);
            permissionProvider = null;
        }
    }

    @Override
    public final void refresh() {
        checkLive();
        branch = store.branch();

        // Disconnect all children -> access to now invalid trees fails fast
        if (OAK_690) {
            rootTree.getNodeBuilder().reset(EMPTY_NODE);
        }
        rootTree = new TreeImpl(this, lastMove);
        modCount = 0;
        if (permissionProvider != null) {
            permissionProvider.refresh();
        }
    }

    @Override
    public void commit() throws CommitFailedException {
        checkLive();
        rebase();
        purgePendingChanges();
        CommitFailedException exception = Subject.doAs(
                getCommitSubject(), new PrivilegedAction<CommitFailedException>() {
            @Override
            public CommitFailedException run() {
                try {
                    branch.merge(getCommitHook());
                    return null;
                } catch (CommitFailedException e) {
                    return e;
                }
            }
        });
        if (exception != null) {
            throw exception;
        }
        refresh();
    }

    /**
     * Combine the globally defined commit hook(s) with the hooks and
     * validators defined by the various security related configurations.
     *
     * @return A commit hook combining repository global commit hook(s) with
     *         the pluggable hooks defined with the security modules.
     */
    private CommitHook getCommitHook() {
        List<CommitHook> commitHooks = new ArrayList<CommitHook>();
        commitHooks.add(hook);
        List<CommitHook> postValidationHooks = new ArrayList<CommitHook>();
        for (SecurityConfiguration sc : securityProvider.getSecurityConfigurations()) {
            for (CommitHook ch : sc.getCommitHooks(workspaceName)) {
                if (ch instanceof PostValidationHook) {
                    postValidationHooks.add(ch);
                } else if (ch != EmptyHook.INSTANCE) {
                    commitHooks.add(ch);
                }
            }
            List<? extends ValidatorProvider> validators = sc.getValidators(workspaceName);
            if (!validators.isEmpty()) {
                commitHooks.add(new EditorHook(CompositeEditorProvider.compose(validators)));
            }
        }
        commitHooks.addAll(postValidationHooks);
        return CompositeHook.compose(commitHooks);
    }

    /**
     * TODO: review again once the permission validation is completed.
     * Build a read only subject for the {@link #commit()} call that makes the
     * principals and the permission provider available to the commit hooks.
     *
     * @return a new read only subject.
     */
    private Subject getCommitSubject() {
        return new Subject(true, subject.getPrincipals(),
                Collections.singleton(getPermissionProvider()), Collections.<Object>emptySet());
    }

    @Override
    public boolean hasPendingChanges() {
        checkLive();
        return !getBaseState().equals(rootTree.getNodeState());
    }

    @Nonnull
    public ChangeExtractor getChangeExtractor() {
        checkLive();
        return new ChangeExtractor() {
            private NodeState baseLine = store.getRoot();

            @Override
            public void getChanges(NodeStateDiff diff) {
                NodeState head = store.getRoot();
                head.compareAgainstBaseState(baseLine, diff);
                baseLine = head;
            }
        };
    }

    @Override
    public QueryEngine getQueryEngine() {
        checkLive();
        return new QueryEngineImpl(getIndexProvider()) {

            @Override
            protected NodeState getRootState() {
                return rootTree.getNodeState();
            }

            @Override
            protected Tree getRootTree() {
                return rootTree;
            }

        };
    }

    @Nonnull
    @Override
    public BlobFactory getBlobFactory() {
        checkLive();

        return new BlobFactory() {
            @Override
            public Blob createBlob(InputStream inputStream) throws IOException {
                checkLive();
                return store.createBlob(inputStream);
            }
        };
    }

    @Nonnull
    private QueryIndexProvider getIndexProvider() {
        if (hasPendingChanges()) {
            return new UUIDDiffIndexProviderWrapper(indexProvider,
                    getBaseState(), rootTree.getNodeState());
        }
        return indexProvider;
    }

    //-----------------------------------------------------------< internal >---

    /**
     * Returns the node state from which the current branch was created.
     *
     * @return base node state
     */
    @Nonnull
    NodeState getBaseState() {
        return branch.getBase();
    }

    @Nonnull
    NodeBuilder createRootBuilder() {
        return branch.getHead().builder();
    }

    // TODO better way to determine purge limit. See OAK-175
    void updated() {
        if (++modCount > PURGE_LIMIT) {
            modCount = 0;
            purgePendingChanges();
        }
    }

    @Nonnull
    PermissionProvider getPermissionProvider() {
        if (permissionProvider == null) {
            permissionProvider = createPermissionProvider();
        }
        return permissionProvider;
    }

    @Nonnull
    String getWorkspaceName() {
        return workspaceName;
    }

    //------------------------------------------------------------< private >---

    /**
     * Purge all pending changes to the underlying {@link NodeStoreBranch}.
     */
    private void purgePendingChanges() {
        branch.setRoot(rootTree.getNodeState());
        reset();
    }

    /**
     * Reset the root builder to the branch's current root state
     */
    private void reset() {
        rootTree.getNodeBuilder().reset(branch.getHead());
    }

    private PermissionProvider createPermissionProvider() {
        return  securityProvider.getAccessControlConfiguration().getPermissionProvider(this, subject.getPrincipals());
    }

    //---------------------------------------------------------< MoveRecord >---

    /**
     * Instances of this class record move operations which took place on this root.
     * They form a singly linked list where each move instance points to the next one.
     * The last entry in the list is always an empty slot to be filled in by calling
     * {@code setMove()}. This fills the slot with the source and destination of the move
     * and links this move to the next one which will be the new empty slot.
     *
     * Moves can be applied to {@code TreeImpl} instances by calling {@code apply()},
     * which will execute all moves in the list on the passed tree instance
     */
    class Move {

        /** source path */
        private String source;

        /** Parent tree of the destination */
        private TreeImpl destParent;

        /** Name at the destination */
        private String destName;

        /** Pointer to the next move. {@code null} if this is the last, empty slot */
        private Move next;

        /**
         * Set this move to the given source and destination. Creates a new empty slot,
         * sets this as the next move and returns it.
         */
        Move setMove(String source, TreeImpl destParent, String destName) {
            this.source = source;
            this.destParent = destParent;
            this.destName = destName;
            return next = new Move();
        }

        /**
         * Apply this and all subsequent moves to the passed tree instance.
         */
        Move apply(TreeImpl tree) {
            Move move = this;
            while (move.next != null) {
                if (move.source.equals(tree.getPathInternal())) {
                    tree.moveTo(move.destParent, move.destName);
                }
                move = move.next;
            }
            return move;
        }

    }
}
