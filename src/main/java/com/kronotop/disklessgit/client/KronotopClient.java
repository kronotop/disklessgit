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

import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;

import java.util.ArrayList;
import java.util.List;

public final class KronotopClient implements AutoCloseable {
    private final List<Member> members;

    public KronotopClient(List<HostAndPort> bootstrapServers, JedisClientConfig config) {
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            throw new IllegalArgumentException("kronotop.bootstrap_servers must not be empty");
        }
        List<Member> list = new ArrayList<>(bootstrapServers.size());
        for (HostAndPort address : bootstrapServers) {
            list.add(new Member(address, new ConnectionPool(address, config)));
        }
        this.members = List.copyOf(list);
    }

    public Session session(HostAndPort host) {
        for (Member member : members) {
            if (member.address().equals(host)) {
                return new Session(member.pool());
            }
        }
        throw new IllegalArgumentException("Unknown host: " + host);
    }

    @Override
    public void close() {
        for (Member member : members) {
            member.pool().close();
        }
    }

    private record Member(HostAndPort address, ConnectionPool pool) {
    }
}
