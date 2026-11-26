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

import java.util.List;

public final class RepositoryBuckets {
    public static final String PACKS = "packs";
    public static final String CHUNKS = "chunks";
    public static final String REFS = "refs";
    public static final List<String> ALL = List.of(PACKS, CHUNKS, REFS);
    public static final String CHUNKS_INDEXES =
            "{\"$compound\": [{\"name\": \"file_seq\", \"unique\": true, \"fields\": [" +
                    "{\"selector\": \"file\", \"bson_type\": \"string\"}," +
                    "{\"selector\": \"seq\", \"bson_type\": \"int32\"}]}]}";
    public static final String REFS_INDEXES = "{\"name\": {\"bson_type\": \"string\", \"unique\": true}}";

    private RepositoryBuckets() {
    }
}
