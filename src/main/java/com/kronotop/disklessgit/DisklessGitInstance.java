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

package com.kronotop.disklessgit;

import com.kronotop.disklessgit.management.RepositoryServlet;
import com.kronotop.disklessgit.repository.RepositoryManager;
import com.typesafe.config.Config;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig;
import org.eclipse.jgit.transport.ReceivePack;

import java.util.List;

public class DisklessGitInstance implements AutoCloseable {
    public static final String PRODUCT_NAME = "DisklessGit";
    private static final String GIT_CONNECTOR = "git";
    private static final String MANAGEMENT_CONNECTOR = "management";
    private static final String VIRTUAL_THREAD_PREFIX = "disklessgit-vt";
    private static final String INSUFFICIENT_THREADS_MESSAGE = "Insufficient configured threads";
    private final Context context;
    private final Server server;

    public DisklessGitInstance(Context context) {
        this.context = context;

        Config config = context.getConfig();
        this.server = new Server(createThreadPool(config));
        addConnector(GIT_CONNECTOR, config.getString("http.git.host"), config.getInt("http.git.port"));
        addConnector(MANAGEMENT_CONNECTOR, config.getString("http.management.host"), config.getInt("http.management.port"));
    }

    private static QueuedThreadPool createThreadPool(Config config) {
        int minThreads = config.getInt("http.thread_pool.min_threads");
        int maxThreads = config.getInt("http.thread_pool.max_threads");
        if (minThreads < 1) {
            throw new ConfigException("http.thread_pool.min_threads must be at least 1: " + minThreads);
        }
        if (maxThreads < minThreads) {
            throw new ConfigException("http.thread_pool.max_threads must be greater than or equal to min_threads: " + maxThreads);
        }
        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads, minThreads);
        threadPool.setVirtualThreadsExecutor(VirtualThreads.getNamedVirtualThreadsExecutor(VIRTUAL_THREAD_PREFIX));
        return threadPool;
    }

    private static void configureBlockCache(Config config) {
        long blockSize = config.getBytes("jgit.dfs.block_cache.block_size");
        long blockLimit = config.getBytes("jgit.dfs.block_cache.block_limit");
        if (blockSize <= 0 || blockSize > Integer.MAX_VALUE || (blockSize & (blockSize - 1)) != 0) {
            throw new ConfigException("jgit.dfs.block_cache.block_size must be a power of 2: " + blockSize);
        }
        if (blockLimit < blockSize) {
            throw new ConfigException("jgit.dfs.block_cache.block_limit must be greater than or equal to block_size: " + blockLimit);
        }
        try {
            DfsBlockCache.reconfigure(new DfsBlockCacheConfig()
                    .setBlockSize((int) blockSize)
                    .setBlockLimit(blockLimit));
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Invalid jgit.dfs.block_cache configuration: " + e.getMessage());
        }
    }

    private static ServletContextHandler createGitHandler() {
        GitServlet servlet = new GitServlet();
        servlet.setRepositoryResolver((req, name) -> {
            throw new RepositoryNotFoundException(name);
        });
        servlet.setReceivePackFactory((req, db) -> new ReceivePack(db));
        servlet.setAsIsFileService(AsIsFileService.DISABLED);

        ServletContextHandler handler = new ServletContextHandler();
        handler.setVirtualHosts(List.of("@" + GIT_CONNECTOR));
        handler.addServlet(new ServletHolder(servlet), "/*");
        return handler;
    }

    private void addConnector(String name, String host, int port) {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setName(name);
        connector.setHost(host);
        connector.setPort(port);
        server.addConnector(connector);
    }

    private ServletContextHandler createManagementHandler() {
        RepositoryManager manager = new RepositoryManager(context.getKronotopClient());

        ServletContextHandler handler = new ServletContextHandler();
        handler.setVirtualHosts(List.of("@" + MANAGEMENT_CONNECTOR));
        handler.addServlet(new ServletHolder(new RepositoryServlet(manager)), RepositoryServlet.PATH_SPEC);
        return handler;
    }

    public void start() throws Exception {
        configureBlockCache(context.getConfig());

        server.setHandler(new ContextHandlerCollection(createGitHandler(), createManagementHandler()));
        try {
            server.start();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(INSUFFICIENT_THREADS_MESSAGE)) {
                throw new ConfigException("http.thread_pool.max_threads is too low to start the HTTP server: "
                        + context.getConfig().getInt("http.thread_pool.max_threads"));
            }
            throw e;
        }
    }

    public void close() throws Exception {
        try {
            server.stop();
        } finally {
            context.getKronotopClient().close();
        }
    }
}
