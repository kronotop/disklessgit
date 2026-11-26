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

import com.kronotop.disklessgit.repository.RepositoryName;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryBuilder;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.RefDatabase;

import java.io.IOException;

public final class KronotopRepository extends DfsRepository {
    private final RepositoryName name;
    private final KronotopStore store;
    private final KronotopObjDatabase objdb;
    private final KronotopRefDatabase refdb;

    private KronotopRepository(Builder builder) {
        super(builder);
        name = builder.name;
        store = builder.store;
        DfsReaderOptions options = builder.getReaderOptions() != null ? builder.getReaderOptions() : new DfsReaderOptions();
        objdb = new KronotopObjDatabase(this, builder.store, options);
        refdb = new KronotopRefDatabase(this, builder.store);
    }

    public RepositoryName name() {
        return name;
    }

    public KronotopStore store() {
        return store;
    }

    @Override
    public DfsObjDatabase getObjectDatabase() {
        return objdb;
    }

    @Override
    public RefDatabase getRefDatabase() {
        return refdb;
    }

    public static final class Builder extends DfsRepositoryBuilder<Builder, KronotopRepository> {
        private RepositoryName name;
        private KronotopStore store;

        public Builder setName(RepositoryName name) {
            this.name = name;
            return this;
        }

        public Builder setStore(KronotopStore store) {
            this.store = store;
            return this;
        }

        @Override
        public KronotopRepository build() throws IOException {
            setRepositoryDescription(new DfsRepositoryDescription(store.namespace()));
            return new KronotopRepository(this);
        }
    }
}
