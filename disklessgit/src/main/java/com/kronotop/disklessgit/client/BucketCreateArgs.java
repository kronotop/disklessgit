/*
 * Copyright (c) 2026 Burak Sezer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kronotop.disklessgit.client;

import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.params.IParams;

import java.util.List;

public final class BucketCreateArgs implements IParams {
    private List<Integer> shards;
    private String indexes;
    private String collation;
    private boolean ifNotExists;
    private String namespace;

    public BucketCreateArgs shards(List<Integer> shards) {
        this.shards = shards;
        return this;
    }

    public BucketCreateArgs indexes(String indexes) {
        this.indexes = indexes;
        return this;
    }

    public BucketCreateArgs collation(String collation) {
        this.collation = collation;
        return this;
    }

    public BucketCreateArgs ifNotExists() {
        this.ifNotExists = true;
        return this;
    }

    public BucketCreateArgs namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    @Override
    public void addParams(CommandArguments args) {
        if (shards != null && !shards.isEmpty()) {
            args.add(BucketKeyword.SHARDS);
            for (int shard : shards) {
                args.add(shard);
            }
        }
        if (indexes != null) {
            args.add(BucketKeyword.INDEXES).add(indexes);
        }
        if (collation != null) {
            args.add(BucketKeyword.COLLATION).add(collation);
        }
        if (ifNotExists) {
            args.add(BucketKeyword.IF_NOT_EXISTS);
        }
        if (namespace != null) {
            args.add(BucketKeyword.NAMESPACE).add(namespace);
        }
    }
}
