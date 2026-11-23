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

import com.kronotop.disklessgit.ConfigException;
import com.typesafe.config.Config;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;

import java.time.Duration;
import java.util.List;

public final class KronotopClientFactory {

    private KronotopClientFactory() {
    }

    private static int socketTimeoutMillis(Config config) {
        Duration socketTimeout = config.getDuration("kronotop.socket_timeout");
        if (!socketTimeout.isPositive() || socketTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new ConfigException("kronotop.socket_timeout must be positive and at most "
                    + Integer.MAX_VALUE + " ms: " + socketTimeout);
        }
        return (int) socketTimeout.toMillis();
    }

    private static ConnectionPoolConfig connectionPoolConfig(Config config) {
        int maxConnections = config.getInt("kronotop.connection_pool.max_connections");
        Duration maxWait = config.getDuration("kronotop.connection_pool.max_wait");
        if (maxConnections < 1) {
            throw new ConfigException("kronotop.connection_pool.max_connections must be at least 1: " + maxConnections);
        }
        if (!maxWait.isPositive()) {
            throw new ConfigException("kronotop.connection_pool.max_wait must be positive: " + maxWait);
        }
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setMaxIdle(maxConnections);
        poolConfig.setMaxWait(maxWait);
        return poolConfig;
    }

    public static KronotopClient create(Config config) {
        List<HostAndPort> bootstrapServers = config.getStringList("kronotop.bootstrap_servers").stream()
                .map(HostAndPort::from)
                .toList();
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(socketTimeoutMillis(config))
                .build();
        return new KronotopClient(bootstrapServers, clientConfig, connectionPoolConfig(config));
    }
}
