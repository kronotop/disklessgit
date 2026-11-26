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

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream;
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFile;
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;
import org.eclipse.jgit.internal.storage.pack.PackExt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class KronotopObjDatabase extends DfsObjDatabase {
    private final KronotopStore store;

    KronotopObjDatabase(KronotopRepository repository, KronotopStore store, DfsReaderOptions options) {
        super(repository, options);
        this.store = store;
    }

    private static BsonDocument toDocument(DfsPackDescription desc) {
        BsonDocument files = new BsonDocument();
        for (PackExt ext : PackExt.values()) {
            if (desc.hasFileExt(ext)) {
                files.append(ext.getExtension(), new BsonDocument("size", new BsonInt64(desc.getFileSize(ext)))
                        .append("blockSize", new BsonInt32(desc.getBlockSize(ext))));
            }
        }
        return new BsonDocument("name", new BsonString(desc.getPackName()))
                .append("source", new BsonString(desc.getPackSource().name()))
                .append("lastModified", new BsonInt64(desc.getLastModified()))
                .append("objectCount", new BsonInt64(desc.getObjectCount()))
                .append("deltaCount", new BsonInt64(desc.getDeltaCount()))
                .append("indexVersion", new BsonInt32(desc.getIndexVersion()))
                .append("estimatedPackSize", new BsonInt64(desc.getEstimatedPackSize()))
                .append("files", files);
    }

    private static DfsPackDescription fromDocument(DfsRepositoryDescription repoDesc, BsonDocument document) {
        DfsPackDescription desc = new DfsPackDescription(repoDesc, document.getString("name").getValue(),
                PackSource.valueOf(document.getString("source").getValue()));
        desc.setLastModified(document.getInt64("lastModified").getValue());
        desc.setObjectCount(document.getInt64("objectCount").getValue());
        desc.setDeltaCount(document.getInt64("deltaCount").getValue());
        desc.setIndexVersion(document.getInt32("indexVersion").getValue());
        desc.setEstimatedPackSize(document.getInt64("estimatedPackSize").getValue());
        for (Map.Entry<String, BsonValue> entry : document.getDocument("files").entrySet()) {
            PackExt ext = extension(entry.getKey());
            BsonDocument file = entry.getValue().asDocument();
            desc.addFileExt(ext);
            desc.setFileSize(ext, file.getInt64("size").getValue());
            desc.setBlockSize(ext, file.getInt32("blockSize").getValue());
        }
        return desc;
    }

    private static PackExt extension(String name) {
        for (PackExt ext : PackExt.values()) {
            if (ext.getExtension().equals(name)) {
                return ext;
            }
        }
        throw new IllegalArgumentException("Unknown pack extension: '" + name + "'");
    }

    @Override
    protected DfsPackDescription newPack(PackSource source) {
        String name = "pack-" + UUID.randomUUID() + "-" + source.name();
        DfsPackDescription desc = new DfsPackDescription(getRepository().getDescription(), name, source);
        desc.setLastModified(System.currentTimeMillis());
        return desc;
    }

    @Override
    protected void commitPackImpl(Collection<DfsPackDescription> desc, Collection<DfsPackDescription> replaces) throws IOException {
        List<BsonDocument> added = new ArrayList<>(desc.size());
        for (DfsPackDescription pack : desc) {
            added.add(toDocument(pack));
        }
        List<String> removed = new ArrayList<>();
        if (replaces != null) {
            for (DfsPackDescription pack : replaces) {
                removed.add(pack.getPackName());
            }
        }
        store.commitPacks(added, removed);
        clearCache();
    }

    @Override
    protected void rollbackPack(Collection<DfsPackDescription> desc) {
    }

    @Override
    protected List<DfsPackDescription> listPacks() throws IOException {
        DfsRepositoryDescription repoDesc = getRepository().getDescription();
        List<DfsPackDescription> packs = new ArrayList<>();
        for (BsonDocument document : store.listPacks()) {
            packs.add(fromDocument(repoDesc, document));
        }
        return packs;
    }

    @Override
    protected ReadableChannel openFile(DfsPackDescription desc, PackExt ext) throws IOException {
        if (!desc.hasFileExt(ext)) {
            throw new FileNotFoundException(desc.getFileName(ext));
        }
        return new ChunkedReadableChannel(store, desc.getFileName(ext), desc.getFileSize(ext));
    }

    @Override
    protected DfsOutputStream writeFile(DfsPackDescription desc, PackExt ext) {
        return new ChunkedOutputStream(store, desc, ext);
    }

    @Override
    public long getApproximateObjectCount() {
        try {
            long count = 0;
            for (DfsPackFile pack : getPacks()) {
                count += pack.getPackDescription().getObjectCount();
            }
            return count;
        } catch (IOException e) {
            return -1;
        }
    }
}
