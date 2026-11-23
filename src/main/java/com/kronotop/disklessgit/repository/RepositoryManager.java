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

package com.kronotop.disklessgit.repository;

import com.kronotop.disklessgit.client.BucketCreateArgs;
import com.kronotop.disklessgit.client.KronotopClient;
import com.kronotop.disklessgit.client.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.ArrayList;
import java.util.List;

public class RepositoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryManager.class);

    private static final String PACKS = "packs";
    private static final String CHUNKS = "chunks";
    private static final String REFS = "refs";
    private static final List<String> BUCKETS = List.of(PACKS, CHUNKS, REFS);
    private static final String CHUNKS_INDEXES =
            "{\"$compound\": [{\"name\": \"file_seq\", \"unique\": true, \"fields\": [" +
                    "{\"selector\": \"file\", \"bson_type\": \"string\"}," +
                    "{\"selector\": \"seq\", \"bson_type\": \"int32\"}]}]}";
    private static final String REFS_INDEXES = "{\"name\": {\"bson_type\": \"string\", \"unique\": true}}";

    private static final String NAMESPACE_ALREADY_EXISTS = "NAMESPACEALREADYEXISTS";
    private static final String NAMESPACE_BEING_REMOVED = "NAMESPACEBEINGREMOVED";
    private static final String NO_SUCH_NAMESPACE = "NOSUCHNAMESPACE";
    private static final String NO_SUCH_BUCKET = "NOSUCHBUCKET";
    private static final String BUCKET_BEING_REMOVED = "BUCKETBEINGREMOVED";
    private static final String BARRIER_NOT_SATISFIED = "BARRIERNOTSATISFIED";
    private static final int PURGE_ATTEMPTS = 3;

    private final KronotopClient client;

    public RepositoryManager(KronotopClient client) {
        this.client = client;
    }

    private static boolean hasCode(JedisDataException e, String code) {
        String message = e.getMessage();
        return message != null && (message.equals(code) || message.startsWith(code + " "));
    }

    private static void purge(Runnable command) {
        for (int attempt = 1; ; attempt++) {
            try {
                command.run();
                return;
            } catch (JedisDataException e) {
                if (!hasCode(e, BARRIER_NOT_SATISFIED) || attempt == PURGE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private static void dropBucket(Session session, String bucket, String namespace) {
        try {
            session.bucket().remove(bucket, namespace);
        } catch (JedisDataException e) {
            if (hasCode(e, NO_SUCH_BUCKET)) {
                return;
            }
            if (!hasCode(e, BUCKET_BEING_REMOVED)) {
                throw e;
            }
        }
        purge(() -> session.bucket().purge(bucket, namespace));
    }

    private static void drop(Session session, String namespace) {
        try {
            for (String bucket : BUCKETS) {
                dropBucket(session, bucket, namespace);
            }
            session.namespace().remove(namespace);
        } catch (JedisDataException e) {
            if (!hasCode(e, NAMESPACE_BEING_REMOVED)) {
                throw e;
            }
        }
        purge(() -> session.namespace().purge(namespace));
    }

    private static List<String> children(Session session, String namespace) {
        try {
            return session.namespace().list(namespace);
        } catch (JedisDataException e) {
            if (hasCode(e, NO_SUCH_NAMESPACE)) {
                return List.of();
            }
            throw e;
        }
    }

    private static List<RepositoryName> list(Session session, String owner) {
        List<RepositoryName> repositories = new ArrayList<>();
        for (String name : children(session, RepositoryName.ownerNamespace(owner))) {
            repositories.add(new RepositoryName(owner, name));
        }
        return repositories;
    }

    public void create(RepositoryName repository) {
        String namespace = repository.namespace();
        try (Session session = client.session()) {
            try {
                session.namespace().create(namespace);
            } catch (JedisDataException e) {
                if (hasCode(e, NAMESPACE_ALREADY_EXISTS) || hasCode(e, NAMESPACE_BEING_REMOVED)) {
                    throw new RepositoryAlreadyExistsException("Repository already exists: '" + repository + "'");
                }
                throw e;
            }
            try {
                session.bucket().create(PACKS, new BucketCreateArgs().namespace(namespace));
                session.bucket().create(CHUNKS, new BucketCreateArgs().indexes(CHUNKS_INDEXES).namespace(namespace));
                session.bucket().create(REFS, new BucketCreateArgs().indexes(REFS_INDEXES).namespace(namespace));
            } catch (RuntimeException e) {
                try {
                    drop(session, namespace);
                } catch (RuntimeException rollback) {
                    LOGGER.warn("Failed to roll back repository '{}': {}", repository, rollback.getMessage());
                }
                throw e;
            }
        }
    }

    public void delete(RepositoryName repository) {
        try (Session session = client.session()) {
            drop(session, repository.namespace());
        } catch (JedisDataException e) {
            if (hasCode(e, NO_SUCH_NAMESPACE)) {
                throw new NoSuchRepositoryException("No such repository: '" + repository + "'");
            }
            if (hasCode(e, BARRIER_NOT_SATISFIED)) {
                throw new BarrierNotSatisfiedException("Repository '" + repository + "' is still being removed, retry the request");
            }
            throw e;
        }
    }

    public List<RepositoryName> list() {
        try (Session session = client.session()) {
            List<RepositoryName> repositories = new ArrayList<>();
            for (String owner : children(session, RepositoryName.ROOT_NAMESPACE)) {
                repositories.addAll(list(session, owner));
            }
            return repositories;
        }
    }

    public List<RepositoryName> list(String owner) {
        RepositoryName.requireValid("owner", owner);
        try (Session session = client.session()) {
            return list(session, owner);
        }
    }
}
