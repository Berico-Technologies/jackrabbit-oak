/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;
import javax.jcr.NamespaceRegistry;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.lock.LockManager;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.observation.ObservationManager;
import javax.jcr.query.QueryManager;
import javax.jcr.version.Version;
import javax.jcr.version.VersionManager;

import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.jackrabbit.api.security.authorization.PrivilegeManager;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.jcr.delegate.SessionDelegate;
import org.apache.jackrabbit.oak.jcr.lock.LockManagerImpl;
import org.apache.jackrabbit.oak.jcr.query.QueryManagerImpl;
import org.apache.jackrabbit.oak.jcr.version.VersionManagerImpl;
import org.apache.jackrabbit.oak.namepath.NamePathMapper;
import org.apache.jackrabbit.oak.plugins.name.ReadWriteNamespaceRegistry;
import org.apache.jackrabbit.oak.plugins.nodetype.write.ReadWriteNodeTypeManager;
import org.apache.jackrabbit.util.Text;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;

import static org.apache.jackrabbit.oak.plugins.nodetype.NodeTypeConstants.NODE_TYPES_PATH;

/**
 * TODO document
 */
public class WorkspaceImpl implements JackrabbitWorkspace {

    private final SessionContext sessionContext;
    private final SessionDelegate sessionDelegate;
    private final QueryManagerImpl queryManager;
    private final LockManager lockManager;
    private final VersionManagerImpl versionManager;
    private final ReadWriteNodeTypeManager nodeTypeManager;

    public WorkspaceImpl(final SessionContext sessionContext) {
        this.sessionContext = sessionContext;
        this.sessionDelegate = sessionContext.getSessionDelegate();
        this.queryManager = new QueryManagerImpl(sessionContext);
        this.lockManager = new LockManagerImpl(sessionContext);
        this.versionManager = new VersionManagerImpl(sessionContext);
        this.nodeTypeManager = new ReadWriteNodeTypeManager() {
            @Override
            protected void refresh() throws RepositoryException {
                getSession().refresh(true);
            }

            @Override
            protected Tree getTypes() {
                return sessionDelegate.getRoot().getTree(NODE_TYPES_PATH);
            }

            @Nonnull
            @Override
            protected Root getWriteRoot() {
                return sessionDelegate.getContentSession().getLatestRoot();
            }

            @Override
            @Nonnull
            protected ValueFactory getValueFactory() {
                return sessionContext.getValueFactory();
            }

            @Nonnull
            @Override
            protected NamePathMapper getNamePathMapper() {
                return sessionContext;
            }
        };
    }

    //----------------------------------------------------------< Workspace >---
    @Override
    @Nonnull
    public Session getSession() {
        return sessionContext.getSession();
    }

    @Override
    public String getName() {
        return sessionDelegate.getWorkspaceName();
    }

    @Override
    public void copy(String srcAbsPath, String destAbsPath) throws RepositoryException {
        copy(getName(), srcAbsPath, destAbsPath);
    }

    private String getOakPathKeepIndexOrThrowNotFound(String absPath) throws PathNotFoundException {
        return sessionContext.getOakPathKeepIndexOrThrowNotFound(absPath);
    }

    @Override
    public void copy(String srcWorkspace, String srcAbsPath, String destAbsPath) throws RepositoryException {
        ensureIsAlive();

        if (!getName().equals(srcWorkspace)) {
            throw new UnsupportedRepositoryOperationException("Not implemented.");
        }

        SessionImpl.checkProtectedNodes(
                getSession(), Text.getRelativeParent(srcAbsPath, 1), Text.getRelativeParent(destAbsPath, 1));

        String oakPath = getOakPathKeepIndexOrThrowNotFound(destAbsPath);
        String oakName = PathUtils.getName(oakPath);
        // handle index
        if (oakName.contains("[")) {
            throw new RepositoryException("Cannot create a new node using a name including an index");
        }

        sessionDelegate.copy(
                getOakPathOrThrowNotFound(srcAbsPath),
                getOakPathOrThrowNotFound(oakPath));
    }

    private String getOakPathOrThrowNotFound(String srcAbsPath) throws PathNotFoundException {
        return sessionContext.getOakPathOrThrowNotFound(srcAbsPath);
    }

    @Override
    public void clone(String srcWorkspace, String srcAbsPath, String destAbsPath, boolean removeExisting) throws RepositoryException {
        ensureIsAlive();

        SessionImpl.checkProtectedNodes(
                getSession(), Text.getRelativeParent(srcAbsPath, 1), Text.getRelativeParent(destAbsPath, 1));

        // TODO
        throw new UnsupportedRepositoryOperationException("Not implemented.");
    }

    @Override
    public void move(String srcAbsPath, String destAbsPath) throws RepositoryException {
        ensureIsAlive();

        SessionImpl.checkProtectedNodes(
                getSession(), Text.getRelativeParent(srcAbsPath, 1), Text.getRelativeParent(destAbsPath, 1));

        String oakPath = getOakPathKeepIndexOrThrowNotFound(destAbsPath);
        String oakName = PathUtils.getName(oakPath);
        // handle index
        if (oakName.contains("[")) {
            throw new RepositoryException("Cannot create a new node using a name including an index");
        }

        sessionDelegate.move(
                getOakPathOrThrowNotFound(srcAbsPath),
                getOakPathOrThrowNotFound(oakPath),
                false);
    }

    @Override
    public void restore(Version[] versions, boolean removeExisting) throws RepositoryException {
        getVersionManager().restore(versions, removeExisting);
    }

    @Override
    public LockManager getLockManager() {
        return lockManager;
    }

    @Override
    public QueryManager getQueryManager() throws RepositoryException {
        ensureIsAlive();
        return queryManager;
    }

    @Override
    public NamespaceRegistry getNamespaceRegistry() {
        return new ReadWriteNamespaceRegistry() {
            @Override
            protected Tree getReadTree() {
                return sessionDelegate.getRoot().getTree("/");
            }

            @Override
            protected Root getWriteRoot() {
                return sessionDelegate.getContentSession().getLatestRoot();
            }

            @Override
            protected void refresh() throws RepositoryException {
                getSession().refresh(true);
            }
        };
    }

    @Override
    public NodeTypeManager getNodeTypeManager() {
        return getReadWriteNodeTypeManager();
    }

    @Override
    public ObservationManager getObservationManager() throws RepositoryException {
        ensureIsAlive();

        return sessionContext.getObservationManager();
    }

    @Override
    public VersionManager getVersionManager() throws RepositoryException {
        ensureIsAlive();
        return versionManager;
    }

    @Override
    public String[] getAccessibleWorkspaceNames() throws RepositoryException {
        ensureIsAlive();

        // TODO -> SPI
        return new String[]{getName()};
    }

    @Override
    public ContentHandler getImportContentHandler(String parentAbsPath, int uuidBehavior) throws RepositoryException {
        ensureIsAlive();

        // TODO
        throw new UnsupportedRepositoryOperationException("TODO: Workspace.getImportContentHandler");
    }

    @Override
    public void importXML(String parentAbsPath, InputStream in, int uuidBehavior) throws IOException, RepositoryException {
        ensureIsAlive();

        // TODO -> SPI
        throw new UnsupportedRepositoryOperationException("TODO: Workspace.importXML");
    }

    @Override
    public void createWorkspace(String name) throws RepositoryException {
        ensureIsAlive();

        // TODO -> SPI
        throw new UnsupportedRepositoryOperationException("TODO: Workspace.createWorkspace");
    }

    @Override
    public void createWorkspace(String name, String srcWorkspace) throws RepositoryException {
        ensureIsAlive();

        // TODO -> SPI
        throw new UnsupportedRepositoryOperationException("TODO: Workspace.createWorkspace");
    }

    @Override
    public void deleteWorkspace(String name) throws RepositoryException {
        ensureIsAlive();

        // TODO -> SPI
        throw new UnsupportedRepositoryOperationException("TODO: Workspace.deleteWorkspace");
    }

    //------------------------------------------------< JackrabbitWorkspace >---

    @Override
    public void createWorkspace(String workspaceName, InputSource workspaceTemplate) throws RepositoryException {
        ensureIsAlive();

        // TODO -> SPI
        throw new UnsupportedRepositoryOperationException("TODO: Workspace.createWorkspace");
    }

    /**
     * @see org.apache.jackrabbit.api.JackrabbitWorkspace#getPrivilegeManager()
     */
    @Override
    public PrivilegeManager getPrivilegeManager() throws RepositoryException {
        return sessionContext.getPrivilegeManager();
    }

    //------------------------------------------------------------< internal >---

    ReadWriteNodeTypeManager getReadWriteNodeTypeManager() {
        return nodeTypeManager;
    }

    //------------------------------------------------------------< private >---

    private void ensureIsAlive() throws RepositoryException {
        // check session status
        if (!sessionDelegate.isAlive()) {
            throw new RepositoryException("This session has been closed.");
        }
    }

}
