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

import static com.kronotop.disklessgit.client.KronotopErrors.hasCode;

public class RepositoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryManager.class);

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

    private static void removeAndPurgeBucket(Session session, String bucket, String namespace) {
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

    private static void removeAndPurgeRepository(Session session, String namespace) {
        try {
            for (String bucket : RepositoryBuckets.ALL) {
                removeAndPurgeBucket(session, bucket, namespace);
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

    private static List<RepositoryName> listRepositories(Session session, String owner) {
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
                session.bucket().create(RepositoryBuckets.PACKS, new BucketCreateArgs().namespace(namespace));
                session.bucket().create(RepositoryBuckets.CHUNKS,
                        new BucketCreateArgs().indexes(RepositoryBuckets.CHUNKS_INDEXES).namespace(namespace));
                session.bucket().create(RepositoryBuckets.REFS,
                        new BucketCreateArgs().indexes(RepositoryBuckets.REFS_INDEXES).namespace(namespace));
            } catch (RuntimeException e) {
                try {
                    removeAndPurgeRepository(session, namespace);
                } catch (RuntimeException rollback) {
                    LOGGER.warn("Failed to roll back repository '{}': {}", repository, rollback.getMessage());
                }
                throw e;
            }
        }
    }

    public boolean exists(RepositoryName repository) {
        try (Session session = client.session()) {
            return session.namespace().exists(repository.namespace());
        } catch (JedisDataException e) {
            if (hasCode(e, NAMESPACE_BEING_REMOVED)) {
                return false;
            }
            throw e;
        }
    }

    public void delete(RepositoryName repository) {
        try (Session session = client.session()) {
            removeAndPurgeRepository(session, repository.namespace());
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

    public List<RepositoryName> listAll() {
        try (Session session = client.session()) {
            List<RepositoryName> repositories = new ArrayList<>();
            for (String owner : children(session, RepositoryName.ROOT_NAMESPACE)) {
                repositories.addAll(listRepositories(session, owner));
            }
            return repositories;
        }
    }

    public List<RepositoryName> list(String owner) {
        RepositoryName.requireValid("owner", owner);
        try (Session session = client.session()) {
            return listRepositories(session, owner);
        }
    }
}
