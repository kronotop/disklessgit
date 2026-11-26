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

package com.kronotop.disklessgit.jgit;

import com.kronotop.disklessgit.client.BSONUtil;
import com.kronotop.disklessgit.client.BucketDeleteArgs;
import com.kronotop.disklessgit.client.BucketQueryArgs;
import com.kronotop.disklessgit.client.BucketQueryResult;
import com.kronotop.disklessgit.client.BucketUpdateArgs;
import com.kronotop.disklessgit.client.KronotopClient;
import com.kronotop.disklessgit.client.KronotopClientException;
import com.kronotop.disklessgit.client.KronotopErrors;
import com.kronotop.disklessgit.client.Session;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.kronotop.disklessgit.repository.RepositoryBuckets.CHUNKS;
import static com.kronotop.disklessgit.repository.RepositoryBuckets.PACKS;
import static com.kronotop.disklessgit.repository.RepositoryBuckets.REFS;

public final class KronotopStore {
    static final int CHUNK_SIZE = 1 << 20; // 1MiB
    private static final int CHUNK_DOCUMENT_OVERHEAD = 256;
    private static final int CURSOR_EXHAUSTED = -1;
    private static final String HEAD = "HEAD";

    private final KronotopClient client;
    private final String namespace;

    public KronotopStore(KronotopClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
    }

    private static BsonDocument chunkFilter(String file, int seq) {
        return new BsonDocument("file", new BsonString(file)).append("seq", new BsonInt32(seq));
    }

    private static BsonDocument nameFilter(String name) {
        return new BsonDocument("name", new BsonString(name));
    }

    private static boolean isConflict(IOException e) {
        return e.getCause() instanceof JedisDataException cause
                && KronotopErrors.hasCode(cause, KronotopErrors.NOT_COMMITTED);
    }

    private static void rollbackQuietly(Session session) {
        try {
            session.transaction().rollback();
        } catch (JedisException | KronotopClientException ignored) {
        }
    }

    static BsonDocument refDocument(String name, String target, boolean symbolic) {
        return new BsonDocument("name", new BsonString(name))
                .append("target", new BsonString(target))
                .append("symbolic", BsonBoolean.valueOf(symbolic));
    }

    public String namespace() {
        return namespace;
    }

    private <T> T execute(Function<Session, T> work) throws IOException {
        try (Session session = client.session()) {
            return work.apply(session);
        } catch (JedisException | KronotopClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private <T> T inTransaction(Function<Session, T> work) throws IOException {
        return execute(session -> {
            session.transaction().begin();
            T result;
            try {
                result = work.apply(session);
            } catch (RuntimeException e) {
                rollbackQuietly(session);
                throw e;
            }
            session.transaction().commit();
            return result;
        });
    }

    private List<BsonDocument> query(Session session, String bucket, BsonDocument filter, int limit) {
        BucketQueryArgs args = new BucketQueryArgs().namespace(namespace);
        if (limit > 0) {
            args.limit(limit);
        }
        BucketQueryResult result = session.bucket().query(bucket, filter, args);
        List<BsonDocument> documents = new ArrayList<>(result.entries());
        while (result.cursorId() != CURSOR_EXHAUSTED) {
            result = session.bucket().advanceQuery(result.cursorId());
            documents.addAll(result.entries());
        }
        return documents;
    }

    private void delete(Session session, String bucket, BsonDocument filter) {
        session.bucket().delete(bucket, filter, new BucketDeleteArgs().namespace(namespace).close());
    }

    private void writeRef(Session session, BsonDocument current, BsonDocument replacement) {
        if (current == null) {
            session.bucket().insert(REFS, namespace, List.of(replacement));
            return;
        }
        BsonDocument fields = new BsonDocument("target", replacement.get("target"))
                .append("symbolic", replacement.get("symbolic"));
        session.bucket().update(REFS, nameFilter(replacement.getString("name").getValue()),
                new BsonDocument("$set", fields), new BucketUpdateArgs().namespace(namespace).close());
    }

    public void linkHeadIfDangling(String branch) throws IOException {
        try {
            inTransaction(session -> {
                List<BsonDocument> heads = query(session, REFS, nameFilter(HEAD), 0);
                BsonDocument head = heads.isEmpty() ? null : heads.getFirst();
                if (head != null && head.getBoolean("symbolic").getValue()) {
                    String target = head.getString("target").getValue();
                    if (!query(session, REFS, nameFilter(target), 1).isEmpty()) {
                        return null;
                    }
                }
                writeRef(session, head, refDocument(HEAD, branch, true));
                return null;
            });
        } catch (IOException e) {
            if (!isConflict(e)) {
                throw e;
            }
        }
    }

    public void insertChunk(String file, int seq, byte[] data, int length) throws IOException {
        byte[] payload = length == data.length ? data : Arrays.copyOf(data, length);
        BsonDocument document = chunkFilter(file, seq).append("data", new BsonBinary(payload));
        byte[] encoded = BSONUtil.toBytes(document, payload.length + CHUNK_DOCUMENT_OVERHEAD);
        execute(session -> session.bucket().insertEncoded(CHUNKS, namespace, List.of(encoded)));
    }

    public byte[] readChunk(String file, int seq) throws IOException {
        List<BsonDocument> documents = execute(session -> query(session, CHUNKS, chunkFilter(file, seq), 1));
        if (documents.isEmpty()) {
            throw new IOException("No such chunk: '" + file + "' seq " + seq);
        }
        return documents.getFirst().getBinary("data").getData();
    }

    public List<BsonDocument> listPacks() throws IOException {
        return execute(session -> query(session, PACKS, new BsonDocument(), 0));
    }

    public void commitPacks(List<BsonDocument> added, List<String> removed) throws IOException {
        inTransaction(session -> {
            if (!added.isEmpty()) {
                session.bucket().insert(PACKS, namespace, added);
            }
            for (String name : removed) {
                delete(session, PACKS, nameFilter(name));
            }
            return null;
        });
    }

    public List<BsonDocument> readAllRefs() throws IOException {
        return execute(session -> query(session, REFS, new BsonDocument(), 0));
    }

    public boolean compareAndSetRef(String name, Predicate<BsonDocument> expected, BsonDocument replacement) throws IOException {
        try {
            return inTransaction(session -> {
                List<BsonDocument> documents = query(session, REFS, nameFilter(name), 0);
                BsonDocument current = documents.isEmpty() ? null : documents.getFirst();
                if (!expected.test(current)) {
                    return false;
                }
                if (replacement == null) {
                    if (current != null) {
                        delete(session, REFS, nameFilter(name));
                    }
                } else {
                    writeRef(session, current, replacement);
                }
                return true;
            });
        } catch (IOException e) {
            if (isConflict(e)) {
                return false;
            }
            throw e;
        }
    }
}
