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
package org.apache.jackrabbit.oak.plugins.index.lucene;

import static org.apache.jackrabbit.oak.plugins.index.IndexUtils.buildIndexDefinitions;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.oak.plugins.index.IndexDefinition;
import org.apache.jackrabbit.oak.spi.query.QueryIndex;
import org.apache.jackrabbit.oak.spi.query.QueryIndexProvider;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A provider for Lucene indexes.
 * 
 * @see LuceneIndex
 * 
 */
@Component
@Service(QueryIndexProvider.class)
public class LuceneIndexProvider implements QueryIndexProvider,
        LuceneIndexConstants {

    private static final Logger LOG = LoggerFactory
            .getLogger(LuceneIndexProvider.class);

    @Override @Nonnull
    public List<QueryIndex> getQueryIndexes(NodeState nodeState) {
        List<QueryIndex> tempIndexes = new ArrayList<QueryIndex>();
        for (IndexDefinition child : buildIndexDefinitions(nodeState, "/",
                TYPE_LUCENE)) {
            LOG.debug("found a lucene index definition {}", child);
            tempIndexes.add(newLuceneIndex(child));
        }
        return tempIndexes;
    }

    protected LuceneIndex newLuceneIndex(IndexDefinition child) {
        return new LuceneIndex(child);
    }
}
