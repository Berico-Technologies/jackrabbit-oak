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

import java.util.ArrayList;
import java.util.List;

import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.ContentSession;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Tree.Status;
import org.apache.jackrabbit.oak.api.Type;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RootImplTest {

    private ContentSession session;

    @Before
    public void setUp() throws CommitFailedException {
        session = new Oak().createContentSession();

        // Add test content
        Root root = session.getLatestRoot();
        Tree tree = root.getTree("/");
        tree.setProperty("a", 1);
        tree.setProperty("b", 2);
        tree.setProperty("c", 3);
        Tree x = tree.addChild("x");
        x.addChild("xx");
        x.setProperty("xa", "value");
        tree.addChild("y");
        tree.addChild("z");
        root.commit();
    }

    @After
    public void tearDown() {
        session = null;
    }

    @Test
    public void getTree() {
        Root root = session.getLatestRoot();

        List<String> validPaths = new ArrayList<String>();
        validPaths.add("/");
        validPaths.add("/x");
        validPaths.add("/x/xx");
        validPaths.add("/y");
        validPaths.add("/z");

        for (String treePath : validPaths) {
            Tree tree = root.getTree(treePath);
            assertNotNull(tree);
            assertEquals(treePath, tree.getPath());
        }

        List<String> invalidPaths = new ArrayList<String>();
        invalidPaths.add("/any");
        invalidPaths.add("/x/any");

        for (String treePath : invalidPaths) {
            assertNull(root.getTree(treePath));
        }
    }

    @Test
    public void move() throws CommitFailedException {
        Root root = session.getLatestRoot();
        Tree tree = root.getTree("/");

        Tree y = tree.getChild("y");
        Tree x = tree.getChild("x");
        assertNotNull(x);

        root.move("/x", "/y/xx");
        assertFalse(tree.hasChild("x"));
        assertTrue(y.hasChild("xx"));
        assertEquals("/y/xx", x.getPath());

        root.commit();
        tree = root.getTree("/");

        assertFalse(tree.hasChild("x"));
        assertTrue(tree.hasChild("y"));
        assertTrue(tree.getChild("y").hasChild("xx"));
    }

    @Test
    public void moveRemoveAdd() {
        Root root = session.getLatestRoot();

        Tree x = root.getTree("/x");
        Tree z = root.getTree("/z");
        z.setProperty("p", "1");

        root.move("/z", "/x/z");
        root.getTree("/x/z").remove();

        assertEquals(Status.DISCONNECTED, z.getStatus());

        x.addChild("z");
        assertEquals(Status.EXISTING, z.getStatus());

        x.getChild("z").setProperty("p", "2");
        PropertyState p = z.getProperty("p");
        assertNotNull(p);
        assertEquals("2", p.getValue(Type.STRING));
    }

    @Test
    public void moveNew() throws CommitFailedException {
        Root root = session.getLatestRoot();
        Tree tree = root.getTree("/");

        Tree t = tree.addChild("new");

        root.move("/new", "/y/new");
        assertEquals("/y/new", t.getPath());

        assertNull(tree.getChild("new"));
    }

    /**
     * Regression test for OAK-208
     */
    @Test
    public void removeMoved() throws CommitFailedException {
        Root root = session.getLatestRoot();
        Tree r = root.getTree("/");
        r.addChild("a");
        r.addChild("b");

        root.move("/a", "/b/c");
        assertFalse(r.hasChild("a"));
        assertTrue(r.hasChild("b"));

        r.getChild("b").remove();
        assertFalse(r.hasChild("a"));
        assertFalse(r.hasChild("b"));

        root.commit();
        r = root.getTree("/");
        assertFalse(r.hasChild("a"));
        assertFalse(r.hasChild("b"));
    }

    @Test
    public void rename() throws CommitFailedException {
        Root root = session.getLatestRoot();
        Tree tree = root.getTree("/");
        Tree x = tree.getChild("x");
        assertNotNull(x);

        root.move("/x", "/xx");
        assertFalse(tree.hasChild("x"));
        assertTrue(tree.hasChild("xx"));
        assertEquals("/xx", x.getPath());
        
        root.commit();
        tree = root.getTree("/");

        assertFalse(tree.hasChild("x"));
        assertTrue(tree.hasChild("xx"));
    }

    @Test
    public void copy() throws CommitFailedException {
        Root root = session.getLatestRoot();
        Tree tree = root.getTree("/");

        Tree y = tree.getChild("y");
        Tree x = tree.getChild("x");
        assertNotNull(x);

        assertTrue(tree.hasChild("x"));
        root.copy("/x", "/y/xx");
        assertTrue(tree.hasChild("x"));
        assertTrue(y.hasChild("xx"));
        
        root.commit();
        tree = root.getTree("/");

        assertTrue(tree.hasChild("x"));
        assertTrue(tree.hasChild("y"));
        assertTrue(tree.getChild("y").hasChild("xx"));
    }

    @Test
    public void deepCopy() throws CommitFailedException {
        Root root = session.getLatestRoot();
        Tree tree = root.getTree("/");

        Tree y = tree.getChild("y");

        root.getTree("/x").addChild("x1");
        root.copy("/x", "/y/xx");
        assertTrue(y.hasChild("xx"));
        assertTrue(y.getChild("xx").hasChild("x1"));

        root.commit();
        tree = root.getTree("/");

        assertTrue(tree.hasChild("x"));
        assertTrue(tree.hasChild("y"));
        assertTrue(tree.getChild("y").hasChild("xx"));
        assertTrue(tree.getChild("y").getChild("xx").hasChild("x1"));

        Tree x = tree.getChild("x");
        Tree xx = tree.getChild("y").getChild("xx");
        checkEqual(x, xx);
    }

    @Test
    public void rebase() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.rebase();
        checkEqual(root1.getTree("/"), (root2.getTree("/")));

        Tree one = root2.getTree("/one");
        one.getChild("two").remove();
        one.addChild("four");
        root2.commit();

        root1.rebase();
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithAddNode() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.getTree("/").addChild("child");
        root1.rebase();

        root2.getTree("/").addChild("child");
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithRemoveNode() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.getTree("/").getChild("x").remove();
        root1.rebase();

        root2.getTree("/").getChild("x").remove();
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithAddProperty() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.getTree("/").setProperty("new", 42);
        root1.rebase();

        root2.getTree("/").setProperty("new", 42);
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithRemoveProperty() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.getTree("/").removeProperty("a");
        root1.rebase();

        root2.getTree("/").removeProperty("a");
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithSetProperty() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.getTree("/").setProperty("a", 42);
        root1.rebase();

        root2.getTree("/").setProperty("a", 42);
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithMove() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.move("/x", "/y/x-moved");
        root1.rebase();

        root2.move("/x", "/y/x-moved");
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void rebaseWithCopy() throws CommitFailedException {
        Root root1 = session.getLatestRoot();
        Root root2 = session.getLatestRoot();

        checkEqual(root1.getTree("/"), root2.getTree("/"));

        root2.getTree("/").addChild("one").addChild("two").addChild("three")
                .setProperty("p1", "V1");
        root2.commit();

        root1.copy("/x", "/y/x-copied");
        root1.rebase();

        root2.copy("/x", "/y/x-copied");
        checkEqual(root1.getTree("/"), (root2.getTree("/")));
    }

    @Test
    public void testGetLatest() throws Exception {
        RootImpl root = (RootImpl) session.getLatestRoot();
        Root root2 = root.getLatest();
        assertNotSame(root, root2);

        session.close();
        try {
            root.getLatest();
            fail();
        } catch (IllegalStateException e) {
            // success
        }

        try {
            ((RootImpl) root2).checkLive();
            fail();
        } catch (IllegalStateException e) {
            // success
        }
    }

    private static void checkEqual(Tree tree1, Tree tree2) {
        assertEquals(tree1.getChildrenCount(), tree2.getChildrenCount());
        assertEquals(tree1.getPropertyCount(), tree2.getPropertyCount());

        for (PropertyState property1 : tree1.getProperties()) {
            assertEquals(property1, tree2.getProperty(property1.getName()));
        }

        for (Tree child1 : tree1.getChildren()) {
            checkEqual(child1, tree2.getChild(child1.getName()));
        }
    }
}
