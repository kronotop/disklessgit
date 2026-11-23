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

import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

public enum KronotopCommand implements ProtocolCommand {
    NAMESPACE,
    SESSION_CLOSE("SESSION.CLOSE"),
    BUCKET_CREATE("BUCKET.CREATE"),
    BUCKET_LOCATE("BUCKET.LOCATE");

    private final byte[] raw;

    KronotopCommand() {
        this.raw = SafeEncoder.encode(name());
    }

    KronotopCommand(String command) {
        this.raw = SafeEncoder.encode(command);
    }

    @Override
    public byte[] getRaw() {
        return raw;
    }
}
