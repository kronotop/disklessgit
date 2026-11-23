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
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.util.SafeEncoder;

import java.util.*;

final class BucketLocationBuilder extends Builder<BucketLocation> {
    static final BucketLocationBuilder INSTANCE = new BucketLocationBuilder();

    private BucketLocationBuilder() {
    }

    private static KronotopClientException unexpectedReply() {
        return new KronotopClientException("unexpected BUCKET.LOCATE reply");
    }

    private static List<?> asList(Object data) {
        if (data instanceof List<?> list) {
            return list;
        }
        throw unexpectedReply();
    }

    private static String asString(Object data) {
        if (data instanceof byte[] raw) {
            return SafeEncoder.encode(raw);
        }
        throw unexpectedReply();
    }

    private static HostAndPort parseAddress(String address) {
        String host;
        String port;
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 0 || end + 1 >= address.length() || address.charAt(end + 1) != ':') {
                throw unexpectedReply();
            }
            host = address.substring(1, end);
            port = address.substring(end + 2);
        } else {
            int colon = address.lastIndexOf(':');
            if (colon < 0) {
                throw unexpectedReply();
            }
            host = address.substring(0, colon);
            port = address.substring(colon + 1);
        }
        try {
            return new HostAndPort(host, Integer.parseInt(port));
        } catch (NumberFormatException e) {
            throw unexpectedReply();
        }
    }

    private static List<ShardLocation> buildShards(List<?> routes) {
        if (routes.size() % 3 != 0) {
            throw unexpectedReply();
        }
        List<ShardLocation> shards = new ArrayList<>(routes.size() / 3);
        for (int i = 0; i < routes.size(); i += 3) {
            if (!(routes.get(i) instanceof Long shardId)) {
                throw unexpectedReply();
            }
            String primary = asString(routes.get(i + 1));
            List<String> standbys = new ArrayList<>();
            for (Object standby : asList(routes.get(i + 2))) {
                standbys.add(asString(standby));
            }
            shards.add(new ShardLocation(shardId.intValue(), primary, List.copyOf(standbys)));
        }
        return List.copyOf(shards);
    }

    private static Map<String, List<HostAndPort>> buildMembers(List<?> members) {
        if (members.size() % 2 != 0) {
            throw unexpectedReply();
        }
        Map<String, List<HostAndPort>> result = new LinkedHashMap<>();
        for (int i = 0; i < members.size(); i += 2) {
            String memberId = asString(members.get(i));
            List<HostAndPort> addresses = new ArrayList<>();
            for (Object address : asList(members.get(i + 1))) {
                addresses.add(parseAddress(asString(address)));
            }
            result.put(memberId, List.copyOf(addresses));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public BucketLocation build(Object data) {
        List<?> reply = asList(data);
        if (reply.size() != 2) {
            throw unexpectedReply();
        }
        return new BucketLocation(buildShards(asList(reply.get(0))), buildMembers(asList(reply.get(1))));
    }
}
