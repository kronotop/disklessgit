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

package com.kronotop.disklessgit.ui;

import com.google.gitiles.GitilesAccess;
import com.google.gitiles.RepositoryDescription;
import com.kronotop.disklessgit.jgit.KronotopRepository;
import com.kronotop.disklessgit.repository.InvalidRepositoryNameException;
import com.kronotop.disklessgit.repository.RepositoryManager;
import com.kronotop.disklessgit.repository.RepositoryName;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class KronotopGitilesAccess implements GitilesAccess {
    private static final Object ANONYMOUS_USER_KEY = new Object();

    private final RepositoryManager manager;
    private final RepositoryResolver<HttpServletRequest> resolver;
    private final String baseGitUrl;
    private final Config config;
    private final HttpServletRequest req;

    public KronotopGitilesAccess(RepositoryManager manager, RepositoryResolver<HttpServletRequest> resolver,
                                 String baseGitUrl, Config config, HttpServletRequest req) {
        this.manager = manager;
        this.resolver = resolver;
        this.baseGitUrl = baseGitUrl;
        this.config = config;
        this.req = req;
    }

    private static String normalizeRefName(String name) {
        return name.startsWith(Constants.R_REFS) ? name : Constants.R_HEADS + name;
    }

    private static String toOwner(String prefix) {
        if (prefix == null) {
            return "";
        }
        int start = 0;
        int end = prefix.length();
        while (start < end && prefix.charAt(start) == '/') {
            start++;
        }
        while (end > start && prefix.charAt(end - 1) == '/') {
            end--;
        }
        return prefix.substring(start, end);
    }

    private List<RepositoryName> list(String prefix) {
        String owner = toOwner(prefix);
        if (owner.isEmpty()) {
            return manager.listAll();
        }
        try {
            return manager.list(owner);
        } catch (InvalidRepositoryNameException e) {
            return List.of();
        }
    }

    private RepositoryDescription describe(RepositoryName name, Set<String> branches)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException, IOException {
        RepositoryDescription description = new RepositoryDescription();
        description.name = name.toString();
        description.cloneUrl = baseGitUrl + name;
        if (branches.isEmpty()) {
            return description;
        }
        description.branches = new LinkedHashMap<>();
        try (Repository repository = resolver.open(req, name.toString())) {
            for (String branch : branches) {
                Ref ref = repository.exactRef(normalizeRefName(branch));
                if (ref != null && ref.getObjectId() != null) {
                    description.branches.put(branch, ref.getObjectId().name());
                }
            }
        }
        return description;
    }

    @Override
    public Map<String, RepositoryDescription> listRepositories(String prefix, Set<String> branches)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException, IOException {
        Map<String, RepositoryDescription> repositories = new TreeMap<>();
        for (RepositoryName name : list(prefix)) {
            repositories.put(name.toString(), describe(name, branches));
        }
        return repositories;
    }

    @Override
    public Object getUserKey() {
        return ANONYMOUS_USER_KEY;
    }

    @Override
    public String getRepositoryName() {
        return ((KronotopRepository) ServletUtils.getRepository(req)).name().toString();
    }

    @Override
    public RepositoryDescription getRepositoryDescription() throws IOException {
        try {
            return describe(((KronotopRepository) ServletUtils.getRepository(req)).name(), Set.of());
        } catch (ServiceNotEnabledException | ServiceNotAuthorizedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Config getConfig() {
        return config;
    }
}
