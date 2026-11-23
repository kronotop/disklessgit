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

import redis.clients.jedis.Builder;

import java.util.ArrayList;
import java.util.List;

final class BucketObjectIdsResultBuilder extends Builder<BucketObjectIdsResult> {
    static final BucketObjectIdsResultBuilder INSTANCE = new BucketObjectIdsResultBuilder();

    private static final String OBJECT_IDS = "object_ids";

    private BucketObjectIdsResultBuilder() {
    }

    private static KronotopClientException unexpectedReply() {
        return new KronotopClientException("unexpected object ids reply");
    }

    private static List<byte[]> buildObjectIds(List<?> entries) {
        List<byte[]> objectIds = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof byte[] raw)) {
                throw unexpectedReply();
            }
            objectIds.add(raw);
        }
        return List.copyOf(objectIds);
    }

    @Override
    public BucketObjectIdsResult build(Object data) {
        CursorReply reply = CursorReply.parse(data, OBJECT_IDS, BucketObjectIdsResultBuilder::unexpectedReply);
        return new BucketObjectIdsResult(reply.cursorId(), buildObjectIds(reply.items()));
    }
}
