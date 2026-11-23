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

final class CursorArgs {
    private CursorArgs() {
    }

    static void addParams(CommandArguments args, Integer batch, Integer limit, String collation, String namespace, boolean close) {
        if (batch != null) {
            args.add(BucketKeyword.BATCH).add(batch.intValue());
        }
        if (limit != null) {
            args.add(BucketKeyword.LIMIT).add(limit.intValue());
        }
        if (collation != null) {
            args.add(BucketKeyword.COLLATION).add(collation);
        }
        if (namespace != null) {
            args.add(BucketKeyword.NAMESPACE).add(namespace);
        }
        if (close) {
            args.add(BucketKeyword.CLOSE);
        }
    }
}
