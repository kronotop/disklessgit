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

import com.google.gitiles.BranchRedirect;
import com.google.gitiles.GitilesServlet;
import com.google.gitiles.VisibilityCache;
import com.kronotop.disklessgit.ConfigException;
import com.kronotop.disklessgit.Context;
import com.kronotop.disklessgit.DisklessGitInstance;
import com.kronotop.disklessgit.jgit.KronotopRepositoryResolver;
import com.kronotop.disklessgit.repository.RepositoryManager;
import org.eclipse.jetty.ee11.servlet.ResourceServlet;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jgit.lib.Config;

import java.net.URI;

public class DisklessGitUIInstance implements AutoCloseable {
    public static final String PRODUCT_NAME = DisklessGitInstance.PRODUCT_NAME + " UI";
    private static final String GITILES_SECTION = "gitiles";
    private static final String STATIC_FILE = "static/base.css";
    private final Context context;
    private final Server server;

    public DisklessGitUIInstance(Context context) {
        this.context = context;

        com.typesafe.config.Config config = context.getConfig();
        this.server = new Server(DisklessGitInstance.createThreadPool(config));

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);

        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setHost(config.getString("http.ui.host"));
        connector.setPort(config.getInt("http.ui.port"));
        server.addConnector(connector);
    }

    static String baseGitUrl(com.typesafe.config.Config config) {
        String url = config.getString("http.ui.base_git_url");
        return url.endsWith("/") ? url : url + "/";
    }

    private static String hostOf(String baseGitUrl) {
        String host;
        try {
            host = URI.create(baseGitUrl).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null) {
            throw new ConfigException("http.ui.base_git_url must be a valid URL: " + baseGitUrl);
        }
        return host;
    }

    private ServletContextHandler createHandler() {
        String baseGitUrl = baseGitUrl(context.getConfig());

        Config gitilesConfig = new Config();
        gitilesConfig.setString(GITILES_SECTION, null, "baseGitUrl", baseGitUrl);
        gitilesConfig.setString(GITILES_SECTION, null, "canonicalHostName", hostOf(baseGitUrl));
        gitilesConfig.setString(GITILES_SECTION, null, "siteTitle", DisklessGitInstance.PRODUCT_NAME);

        RepositoryManager manager = new RepositoryManager(context.getKronotopClient());
        KronotopRepositoryResolver resolver = new KronotopRepositoryResolver(manager, context.getKronotopClient());
        GitilesServlet servlet = new GitilesServlet(gitilesConfig, null, null,
                req -> new KronotopGitilesAccess(manager, resolver, baseGitUrl, gitilesConfig, req),
                resolver, new VisibilityCache(new CompatVisibilityChecker()), null, null, null, new BranchRedirect());

        String staticFile = GitilesServlet.class.getResource(STATIC_FILE).toExternalForm();
        ServletHolder staticHolder = new ServletHolder(new ResourceServlet());
        staticHolder.setInitParameter("baseResource", staticFile.substring(0, staticFile.lastIndexOf('/') + 1));
        staticHolder.setInitParameter("dirAllowed", "false");

        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(staticHolder, GitilesServlet.STATIC_PREFIX + "*");
        handler.addServlet(new ServletHolder(servlet), "/*");
        return handler;
    }

    public void start() throws Exception {
        DisklessGitInstance.configureBlockCache(context.getConfig());

        server.setHandler(createHandler());
        server.start();
    }

    public void close() throws Exception {
        try {
            server.stop();
        } finally {
            context.getKronotopClient().close();
        }
    }
}
