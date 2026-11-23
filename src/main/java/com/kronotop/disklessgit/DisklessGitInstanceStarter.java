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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Callable;

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

    @Override
    public Integer call() throws Exception {
        return 0;
    }


    public static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"disklessgit-server " + loadApplicationProperties().getProperty("disklessgit.version")};
        }
    }
}
