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

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public record RepositoryName(String owner, String name) {
    static final String ROOT_NAMESPACE = "disklessgit";
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String RESERVED = "__internal__";

    public RepositoryName {
        requireValid("owner", owner);
        requireValid("name", name);
    }

    static void requireValid(String field, String value) {
        if (value == null || !SEGMENT.matcher(value).matches() || RESERVED.equals(value)) {
            throw new InvalidRepositoryNameException("Invalid repository " + field + ": '" + value + "'");
        }
    }

    static String ownerNamespace(String owner) {
        return ROOT_NAMESPACE + "." + owner;
    }

    public String namespace() {
        return ownerNamespace(owner) + "." + name;
    }

    @Override
    @NotNull
    public String toString() {
        return owner + "/" + name;
    }
}
