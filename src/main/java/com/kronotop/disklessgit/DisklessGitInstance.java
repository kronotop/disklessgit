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

import com.typesafe.config.Config;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig;
import org.eclipse.jgit.transport.ReceivePack;

import java.net.InetSocketAddress;

public class DisklessGitInstance implements AutoCloseable {
    public static final String PRODUCT_NAME = "DisklessGit";
    private final Context context;
    private final Server server;

    public DisklessGitInstance(Context context) {
        this.context = context;

        Config config = context.getConfig();
        String host = config.getString("server.host");
        int port = config.getInt("server.port");
        this.server = new Server(new InetSocketAddress(host, port));
    }

    private static void configureBlockCache(Config config) {
        long blockSize = config.getBytes("dfs.block_cache.block_size");
        long blockLimit = config.getBytes("dfs.block_cache.block_limit");
        if (blockSize <= 0 || blockSize > Integer.MAX_VALUE || (blockSize & (blockSize - 1)) != 0) {
            throw new ConfigException("dfs.block_cache.block_size must be a power of 2: " + blockSize);
        }
        if (blockLimit < blockSize) {
            throw new ConfigException("dfs.block_cache.block_limit must be greater than or equal to block_size: " + blockLimit);
        }
        try {
            DfsBlockCache.reconfigure(new DfsBlockCacheConfig()
                    .setBlockSize((int) blockSize)
                    .setBlockLimit(blockLimit));
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Invalid dfs.block_cache configuration: " + e.getMessage());
        }
    }

    public void start() throws Exception {
        configureBlockCache(context.getConfig());

        GitServlet servlet = new GitServlet();
        servlet.setReceivePackFactory((req, db) -> new ReceivePack(db));
        servlet.setAsIsFileService(AsIsFileService.DISABLED);

        ServletContextHandler servletCtx = new ServletContextHandler();
        servletCtx.addServlet(new ServletHolder(servlet), "/*");

        server.setHandler(servletCtx);
        server.start();
    }

    public void close() throws Exception {
        server.stop();
    }
}
