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

import redis.clients.jedis.util.KeyValue;
import redis.clients.jedis.util.SafeEncoder;

import java.util.List;
import java.util.function.Supplier;

record CursorReply(int cursorId, List<?> items) {
    private static final String CURSOR_ID = "cursor_id";

    static CursorReply parse(Object data, String itemsKey, Supplier<KronotopClientException> unexpectedReply) {
        if (!(data instanceof List<?> reply) || reply.size() != 2) {
            throw unexpectedReply.get();
        }
        Object cursorId = null;
        Object items = null;
        if (reply.getFirst() instanceof KeyValue<?, ?>) {
            for (Object item : reply) {
                if (!(item instanceof KeyValue<?, ?> pair) || !(pair.getKey() instanceof byte[] key)) {
                    throw unexpectedReply.get();
                }
                String name = SafeEncoder.encode(key);
                if (name.equals(CURSOR_ID)) {
                    cursorId = pair.getValue();
                } else if (name.equals(itemsKey)) {
                    items = pair.getValue();
                } else {
                    throw unexpectedReply.get();
                }
            }
        } else {
            cursorId = reply.get(0);
            items = reply.get(1);
        }
        if (!(cursorId instanceof Long id) || !(items instanceof List<?> list)) {
            throw unexpectedReply.get();
        }
        return new CursorReply(id.intValue(), list);
    }
}
