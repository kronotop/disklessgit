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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.util.*;
import java.util.concurrent.Callable;

import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.base.Throwables.getRootCause;

@Command(
        name = "disklessgit-server",
        description = "Starts a DisklessGit server.",
        versionProvider = DisklessGitInstanceStarter.VersionProvider.class,
        sortOptions = false
)
public class DisklessGitInstanceStarter implements Callable<Integer> {
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show help.")
    private boolean helpRequested;

    @Option(names = {"-v", "--version"}, versionHelp = true, description = "Print version information and quit.")
    private boolean versionRequested;

    @Option(names = {"-c", "--config"}, paramLabel = "<file>", description = "Path to the config file.")
    private File configFile;

    @Option(names = "--log-level", paramLabel = "<level>", description = "Log level.")
    private String logLevel;

    @Option(names = "--host", paramLabel = "<host>",
            description = "Address the server listens on for client connections.")
    private String host;

    @Option(names = "--port", paramLabel = "<port>",
            description = "Port the server listens on for client connections.")
    private Integer port;

    @Option(names = "--kronotop-bootstrap-servers", paramLabel = "<host:port>", split = ",",
            description = "Comma-separated list of Kronotop servers used to discover the cluster topology.")
    private List<String> kronotopBootstrapServers;

    private static final Logger LOGGER = LoggerFactory.getLogger(DisklessGitInstanceStarter.class);

    static void main(String[] args) {
        int exitCode = new CommandLine(new DisklessGitInstanceStarter()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static Properties loadApplicationProperties() {
        Properties props = new Properties();
        try (InputStream in = DisklessGitInstance.class.getClassLoader().getResourceAsStream("disklessgit-build.properties")) {
            props.load(in);
        } catch (IOException exp) {
            LOGGER.error("Failed to load application properties", exp);
            throw new RuntimeException(exp);
        }
        return props;
    }

    static boolean isExpectedFailure(Throwable failure) {
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

    private static List<String> withoutBlanks(List<String> values) {
        return values == null ? null : values.stream().filter(value -> !value.isBlank()).toList();
    }

    private static void putIfSet(Map<String, Object> overrides, String path, Object value) {
        if (value != null) {
            overrides.put(path, value);
        }
    }

    Config loadApplicationConfig() {
        if (configFile == null) {
            return ConfigFactory.defaultApplication();
        }
        return ConfigFactory.parseFile(configFile, ConfigParseOptions.defaults().setAllowMissing(false));
    }

    Config buildConfig() {
        Map<String, Object> overrides = new HashMap<>();
        putIfSet(overrides, "server.host", host);
        putIfSet(overrides, "server.port", port);
        putIfSet(overrides, "kronotop.bootstrap_servers", withoutBlanks(kronotopBootstrapServers));

        // Precedence: command line options, -D system properties, config file, reference.conf
        return ConfigFactory.parseMap(overrides, "command line options")
                .withFallback(ConfigFactory.defaultOverrides())
                .withFallback(loadApplicationConfig())
                .withFallback(ConfigFactory.defaultReference())
                .resolve();
    }

    static String resolveProperty(String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            return "unknown";
        }
        return value;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.0f MB", mb);
        }
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
    }

    private static void greeting(Config config) {
        Properties props = loadApplicationProperties();

        LOGGER.info("{}, a stateless Git server backed by Kronotop", DisklessGitInstance.PRODUCT_NAME);
        LOGGER.info("Starting {} {} ({}, built {}) pid {}",
                DisklessGitInstance.PRODUCT_NAME,
                props.getProperty("disklessgit.version"),
                resolveProperty(props.getProperty("disklessgit.git.commit")),
                resolveProperty(props.getProperty("disklessgit.build.time")),
                ProcessHandle.current().pid());
        LOGGER.info("Runtime:  Java {} ({}), {}/{}, {} cores, {} heap",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                formatBytes(Runtime.getRuntime().maxMemory()));
        LOGGER.info("Server:   {}:{}", config.getString("server.host"), config.getInt("server.port"));
        LOGGER.info("Kronotop: {}", String.join(", ", config.getStringList("kronotop.bootstrap_servers")));
        LOGGER.info("Ready to accept connections in {} ms",
                ManagementFactory.getRuntimeMXBean().getUptime());
    }

    private static Thread createShutdownHook(DisklessGitInstance instance) {
        return new Thread(() -> {
            try {
                instance.close();
            } catch (Exception e) {
                LOGGER.error("Failed to stop DisklessGit instance", e);
            } finally {
                LOGGER.info("Quit!");
            }
        });
    }

    private static int start(Config config) {
        try {
            DisklessGitInstance instance = new DisklessGitInstance(new ContextImpl(config));
            Runtime.getRuntime().addShutdownHook(createShutdownHook(instance));
            instance.start();
            greeting(config);
            return 0;
        } catch (Throwable e) {
            logFailure("Failed to start DisklessGit instance", e);
            return 1;
        }
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


    public static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"disklessgit-server " + loadApplicationProperties().getProperty("disklessgit.version")};
        }
    }
}
