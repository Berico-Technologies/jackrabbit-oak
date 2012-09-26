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
package org.apache.jackrabbit.mongomk.impl.model;

import org.apache.jackrabbit.mongomk.api.model.InstructionVisitor;
import org.apache.jackrabbit.mongomk.api.model.Instruction.AddNodeInstruction;
import org.apache.jackrabbit.oak.commons.PathUtils;


/**
 * Implementation of {@link AddNodeInstruction}.
 *
 * @author <a href="mailto:pmarx@adobe.com>Philipp Marx</a>
 */
public class AddNodeInstructionImpl implements AddNodeInstruction {

    private final String path;

    /**
     * Constructs a new {@code AddNodeInstructionImpl}.
     *
     * @param parentPath The parent path.
     * @param name The name.
     */
    public AddNodeInstructionImpl(String parentPath, String name) {
        this.path = PathUtils.concat(parentPath, name);
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AddNodeInstructionImpl [path=");
        builder.append(path);
        builder.append("]");
        return builder.toString();
    }
}