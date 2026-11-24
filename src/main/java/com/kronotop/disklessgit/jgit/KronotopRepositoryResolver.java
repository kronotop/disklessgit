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

import com.kronotop.disklessgit.client.KronotopClient;
import com.kronotop.disklessgit.client.KronotopClientException;
import com.kronotop.disklessgit.repository.InvalidRepositoryNameException;
import com.kronotop.disklessgit.repository.RepositoryManager;
import com.kronotop.disklessgit.repository.RepositoryName;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;

public final class KronotopRepositoryResolver implements RepositoryResolver<HttpServletRequest> {
    private static final String GIT_SUFFIX = ".git";

    private final RepositoryManager manager;
    private final KronotopClient client;

    public KronotopRepositoryResolver(RepositoryManager manager, KronotopClient client) {
        this.manager = manager;
        this.client = client;
    }

    private static RepositoryName parse(String name) throws RepositoryNotFoundException {
        String path = name.endsWith(GIT_SUFFIX) ? name.substring(0, name.length() - GIT_SUFFIX.length()) : name;
        String[] segments = path.split("/", -1);
        if (segments.length != 2) {
            throw new RepositoryNotFoundException(name);
        }
        try {
            return new RepositoryName(segments[0], segments[1]);
        } catch (InvalidRepositoryNameException e) {
            throw new RepositoryNotFoundException(name);
        }
    }

    @Override
    public Repository open(HttpServletRequest req, String name) throws RepositoryNotFoundException, ServiceMayNotContinueException {
        RepositoryName repository = parse(name);
        boolean exists;
        try {
            exists = manager.exists(repository);
        } catch (JedisException | KronotopClientException e) {
            throw new ServiceMayNotContinueException(e.getMessage(), e);
        }
        if (!exists) {
            throw new RepositoryNotFoundException(name);
        }
        try {
            return new KronotopRepository.Builder()
                    .setStore(new KronotopStore(client, repository.namespace()))
                    .build();
        } catch (IOException e) {
            throw new ServiceMayNotContinueException(e.getMessage(), e);
        }
    }
}
