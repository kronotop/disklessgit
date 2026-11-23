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

import org.bson.BsonDocument;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.params.IParams;

public final class BucketQueryArgs implements IParams {
    private String sortBy;
    private SortDirection sortDirection;
    private String resultSortBy;
    private SortDirection resultSortDirection;
    private BsonDocument projection;
    private Integer batch;
    private Integer limit;
    private String collation;
    private String namespace;
    private boolean close;

    public BucketQueryArgs sortBy(String field, SortDirection direction) {
        this.sortBy = field;
        this.sortDirection = direction;
        return this;
    }

    public BucketQueryArgs resultSort(String field, SortDirection direction) {
        this.resultSortBy = field;
        this.resultSortDirection = direction;
        return this;
    }

    public BucketQueryArgs projection(BsonDocument projection) {
        this.projection = projection;
        return this;
    }

    public BucketQueryArgs batch(int batch) {
        this.batch = batch;
        return this;
    }

    public BucketQueryArgs limit(int limit) {
        this.limit = limit;
        return this;
    }

    public BucketQueryArgs collation(String collation) {
        this.collation = collation;
        return this;
    }

    public BucketQueryArgs namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public BucketQueryArgs close() {
        this.close = true;
        return this;
    }

    @Override
    public void addParams(CommandArguments args) {
        if (sortBy != null) {
            args.add(BucketKeyword.SORTBY).add(sortBy).add(sortDirection);
        }
        if (resultSortBy != null) {
            args.add(BucketKeyword.RESULTSORT).add(resultSortBy).add(resultSortDirection);
        }
        if (projection != null) {
            args.add(BucketKeyword.PROJECTION).add(projection.toJson());
        }
        CursorArgs.addParams(args, batch, limit, collation, namespace, close);
    }
}
