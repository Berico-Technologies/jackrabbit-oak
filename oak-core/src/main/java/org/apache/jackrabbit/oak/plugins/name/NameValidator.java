/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.plugins.name;

import java.util.Set;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.spi.commit.DefaultValidator;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.state.NodeState;

import static org.apache.jackrabbit.oak.api.Type.NAME;
import static org.apache.jackrabbit.oak.api.Type.NAMES;

/**
 * TODO document
 */
class NameValidator extends DefaultValidator {

    private final Set<String> prefixes;

    public NameValidator(Set<String> prefixes) {
        this.prefixes = prefixes;
    }

    protected void checkValidName(String name) throws CommitFailedException {
        int colon = name.indexOf(':');
        if (colon > 0) {
            String prefix = name.substring(0, colon);
            if (prefix.isEmpty() || !prefixes.contains(prefix)) {
                throw new CommitFailedException(
                        "Invalid namespace prefix: " + name);
            }
        }

        String local = name.substring(colon + 1);

        int n = local.length();
        if (n > 3 && local.charAt(n - 1) == ']') {
            int i = n - 2;
            while (i > 1 && Character.isDigit(local.charAt(i))) {
                i--;
            }
            if (local.charAt(i) != '[') {
                throw new CommitFailedException("Invalid name index " + name);
            } else {
                local = local.substring(0, i);
            }
        }

        if (!Namespaces.isValidLocalName(local)) {
            throw new CommitFailedException("Invalid name: " + name);
        }
    }

    protected void checkValidValue(PropertyState property)
            throws CommitFailedException {
        if (NAME.equals(property.getType())) {
            for (String value : property.getValue(NAMES)) {
                checkValidValue(value);
            }
        }
    }

    protected void checkValidValue(String value)
            throws CommitFailedException {
        checkValidName(value);
    }

    //-------------------------------------------------------< NodeValidator >

    @Override
    public void propertyAdded(PropertyState after)
            throws CommitFailedException {
        checkValidName(after.getName());
        checkValidValue(after);
    }

    @Override
    public void propertyChanged(PropertyState before, PropertyState after)
            throws CommitFailedException {
        checkValidValue(after);
    }

    @Override
    public void propertyDeleted(PropertyState before) {
        // do nothing
    }

    @Override
    public Validator childNodeAdded(String name, NodeState after)
            throws CommitFailedException {
        checkValidName(name);
        return this;
    }

    @Override
    public Validator childNodeChanged(
            String name, NodeState before, NodeState after) {
        return this;
    }

    @Override
    public Validator childNodeDeleted(String name, NodeState before) {
        return null;
    }

}
