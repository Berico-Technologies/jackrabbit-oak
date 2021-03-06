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
package org.apache.jackrabbit.oak.query.ast;

import org.apache.jackrabbit.oak.query.index.FilterImpl;
import org.apache.jackrabbit.oak.spi.query.Filter;

/**
 * The function "issamenode(..)".
 */
public class SameNodeImpl extends ConstraintImpl {

    private final String path;
    private final String selectorName;
    private SelectorImpl selector;

    public SameNodeImpl(String selectorName, String path) {
        this.selectorName = selectorName;
        this.path = path;
    }

    @Override
    public boolean evaluate() {
        String p = validateAndNormalizePath(path);
        // TODO normalize paths
        return selector.currentPath().equals(p);
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        return "issamenode(" +  quote(selectorName) + 
                ", " + quote(path) + ')';
    }

    public void bindSelector(SourceImpl source) {
        selector = source.getExistingSelector(selectorName);
    }

    @Override
    public void restrict(FilterImpl f) {
        if (f.getSelector() == selector) {
            String p = validateAndNormalizePath(path);
            f.restrictPath(p, Filter.PathRestriction.EXACT);
        }
        // TODO validate absolute path
    }

    @Override
    public void restrictPushDown(SelectorImpl s) {
        if (s == selector) {
            s.restrictSelector(this);
        }
    }

}
