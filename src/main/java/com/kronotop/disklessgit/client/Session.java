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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.CommandObject;
import redis.clients.jedis.Connection;
import redis.clients.jedis.ConnectionPool;

public final class Session implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Session.class);

    private final ConnectionPool pool;
    private final NamespaceCommands namespace;
    private Connection connection;
    private boolean closed;

    Session(ConnectionPool pool) {
        this.pool = pool;
        this.namespace = new NamespaceCommands(this);
    }

    public NamespaceCommands namespace() {
        return namespace;
    }

    <T> T execute(CommandObject<T> command) {
        if (closed) {
            throw new KronotopClientException("session is closed");
        }
        if (connection == null) {
            connection = pool.getResource();
        } else if (connection.isBroken()) {
            throw new KronotopClientException("session connection was lost; open a new session");
        }
        return connection.executeCommand(command);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isBroken()) {
                CommandArguments args = new CommandArguments(KronotopCommand.SESSION_CLOSE);
                connection.executeCommand(new CommandObject<>(args, BuilderFactory.STRING));
            }
        } catch (Exception e) {
            LOGGER.warn("Error ignored while closing session: {}", e.getMessage());
        } finally {
            connection.close();
            connection = null;
        }
    }
}
