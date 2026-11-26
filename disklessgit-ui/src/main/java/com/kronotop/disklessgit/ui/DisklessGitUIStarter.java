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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.kronotop.disklessgit.ConfigException;
import com.kronotop.disklessgit.ContextImpl;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.base.Throwables.getRootCause;

@Command(
        name = "disklessgit-ui",
        description = "Starts the DisklessGit repository browser.",
        sortOptions = false
)
public class DisklessGitUIStarter implements Callable<Integer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisklessGitUIStarter.class);
    private static final String DEFAULTS_RESOURCE = "disklessgit-ui.conf";

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show help.")
    private boolean helpRequested;

    @Option(names = {"-c", "--config"}, paramLabel = "<file>", description = "Path to the config file.")
    private File configFile;

    @Option(names = "--log-level", paramLabel = "<level>", description = "Log level.")
    private String logLevel;

    @Option(names = "--host", paramLabel = "<host>", description = "Address the browser listens on.")
    private String host;

    @Option(names = "--port", paramLabel = "<port>", description = "Port the browser listens on.")
    private Integer port;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Option(names = "--kronotop-bootstrap-servers", paramLabel = "<host:port>", split = ",",
            description = "Comma-separated list of Kronotop servers used to discover the cluster topology.")
    private List<String> kronotopBootstrapServers;

    static void main(String[] args) {
        int exitCode = new CommandLine(new DisklessGitUIStarter()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static boolean isExpectedFailure(Throwable failure) {
        for (Throwable cause : getCausalChain(failure)) {
            if (cause instanceof ConfigException
                    || cause instanceof com.typesafe.config.ConfigException
                    || cause instanceof BindException) {
                return true;
            }
        }
        return false;
    }

    private static void logFailure(String message, Throwable e) {
        if (LOGGER.isDebugEnabled() || !isExpectedFailure(e)) {
            LOGGER.error(message, e);
        } else {
            LOGGER.error("{}: {}", message, getRootCause(e).getMessage());
        }
    }

    private static void applyLogLevel(String name) {
        Level level = Level.toLevel(name, null);
        if (level == null) {
            throw new ConfigException("Unknown log level: '" + name + "'");
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(level);
    }

    private static void putIfSet(Map<String, Object> overrides, String path, Object value) {
        if (value != null) {
            overrides.put(path, value);
        }
    }

    private static void greeting(Config config) {
        LOGGER.info("{}, a repository browser for DisklessGit", DisklessGitUIInstance.PRODUCT_NAME);
        LOGGER.info("Server:   {}:{}", config.getString("http.ui.host"), config.getInt("http.ui.port"));
        LOGGER.info("Git URL:  {}", DisklessGitUIInstance.baseGitUrl(config));
        LOGGER.info("Kronotop: {}", String.join(", ", config.getStringList("kronotop.bootstrap_servers")));
        LOGGER.info("Ready to accept connections in {} ms", ManagementFactory.getRuntimeMXBean().getUptime());
    }

    private static Thread createShutdownHook(DisklessGitUIInstance instance) {
        return new Thread(() -> {
            try {
                instance.close();
            } catch (Exception e) {
                LOGGER.error("Failed to stop {}", DisklessGitUIInstance.PRODUCT_NAME, e);
            } finally {
                LOGGER.info("Quit!");
            }
        });
    }

    private static int start(Config config) {
        try {
            DisklessGitUIInstance instance = new DisklessGitUIInstance(new ContextImpl(config));
            Runtime.getRuntime().addShutdownHook(createShutdownHook(instance));
            instance.start();
            greeting(config);
            return 0;
        } catch (Throwable e) {
            logFailure("Failed to start " + DisklessGitUIInstance.PRODUCT_NAME, e);
            return 1;
        }
    }

    private Config loadApplicationConfig() {
        if (configFile == null) {
            return ConfigFactory.defaultApplication();
        }
        return ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(false));
    }

    private Config buildConfig() {
        Map<String, Object> overrides = new HashMap<>();
        putIfSet(overrides, "http.ui.host", host);
        putIfSet(overrides, "http.ui.port", port);
        if (kronotopBootstrapServers != null) {
            overrides.put("kronotop.bootstrap_servers",
                    kronotopBootstrapServers.stream().filter(value -> !value.isBlank()).toList());
        }

        return ConfigFactory.parseMap(overrides, "command line options")
                .withFallback(ConfigFactory.defaultOverrides())
                .withFallback(loadApplicationConfig())
                .withFallback(ConfigFactory.parseResources(DisklessGitUIStarter.class.getClassLoader(), DEFAULTS_RESOURCE,
                        ConfigParseOptions.defaults().setAllowMissing(false)))
                .withFallback(ConfigFactory.defaultReference())
                .resolve();
    }

    @Override
    public Integer call() {
        Config config;
        try {
            if (logLevel != null) {
                applyLogLevel(logLevel);
            }
            config = buildConfig();
        } catch (Exception e) {
            logFailure("Invalid configuration", e);
            return 1;
        }
        return start(config);
    }
}
