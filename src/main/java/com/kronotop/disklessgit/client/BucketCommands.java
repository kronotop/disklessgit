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

import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.CommandObject;

public final class BucketCommands {
    private final Session session;

    BucketCommands(Session session) {
        this.session = session;
    }

    public void create(String bucket) {
        create(bucket, new BucketCreateArgs());
    }

    public void create(String bucket, BucketCreateArgs options) {
        CommandArguments args = new CommandArguments(KronotopCommand.BUCKET_CREATE)
                .add(bucket)
                .addParams(options);
        session.execute(new CommandObject<>(args, BuilderFactory.STRING));
    }

    public BucketLocation locate(String bucket) {
        return locate(bucket, null);
    }

    public BucketLocation locate(String bucket, String namespace) {
        CommandArguments args = new CommandArguments(KronotopCommand.BUCKET_LOCATE).add(bucket);
        if (namespace != null) {
            args.add(BucketKeyword.NAMESPACE).add(namespace);
        }
        return session.execute(new CommandObject<>(args, BucketLocationBuilder.INSTANCE));
    }
}
