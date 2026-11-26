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

package com.kronotop.disklessgit.management;

import com.kronotop.disklessgit.repository.BarrierNotSatisfiedException;
import com.kronotop.disklessgit.repository.InvalidRepositoryNameException;
import com.kronotop.disklessgit.repository.NoSuchRepositoryException;
import com.kronotop.disklessgit.repository.RepositoryAlreadyExistsException;
import com.kronotop.disklessgit.repository.RepositoryManager;
import com.kronotop.disklessgit.repository.RepositoryName;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RepositoryServlet extends HttpServlet {
    public static final String PATH_SPEC = "/api/v1/repositories/*";
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryServlet.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final transient RepositoryManager manager;

    public RepositoryServlet(RepositoryManager manager) {
        this.manager = manager;
    }

    private static List<String> segments(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) {
            return List.of();
        }
        return List.of(path.substring(1).split("/"));
    }

    private static RepositoryBody toBody(RepositoryName repository) {
        return new RepositoryBody(repository.owner(), repository.name());
    }

    private static RepositoryListBody toBody(List<RepositoryName> repositories) {
        return new RepositoryListBody(repositories.stream().map(RepositoryServlet::toBody).toList());
    }

    private static RepositoryName readRepositoryName(HttpServletRequest req) throws IOException {
        RepositoryBody body;
        try {
            body = MAPPER.readValue(req.getInputStream(), RepositoryBody.class);
        } catch (JacksonException e) {
            body = null;
        }
        if (body == null) {
            throw new InvalidRepositoryNameException("Invalid request body");
        }
        return new RepositoryName(body.owner(), body.name());
    }

    private static void write(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(resp.getOutputStream(), body);
    }

    private static void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        write(resp, status, new ErrorBody(message));
    }

    private static void handle(HttpServletRequest req, HttpServletResponse resp, Action action) throws IOException {
        try {
            action.run();
        } catch (InvalidRepositoryNameException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (NoSuchRepositoryException e) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (RepositoryAlreadyExistsException e) {
            writeError(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (BarrierNotSatisfiedException e) {
            writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("{} {} failed", req.getMethod(), req.getRequestURI(), e);
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private static void notFound(HttpServletResponse resp) throws IOException {
        writeError(resp, HttpServletResponse.SC_NOT_FOUND, "Not found");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<String> segments = segments(req);
        handle(req, resp, () -> {
            switch (segments.size()) {
                case 0 -> write(resp, HttpServletResponse.SC_OK, toBody(manager.listAll()));
                case 1 -> write(resp, HttpServletResponse.SC_OK, toBody(manager.list(segments.getFirst())));
                default -> notFound(resp);
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!segments(req).isEmpty()) {
            notFound(resp);
            return;
        }
        handle(req, resp, () -> {
            RepositoryName repository = readRepositoryName(req);
            manager.create(repository);
            write(resp, HttpServletResponse.SC_CREATED, toBody(repository));
        });
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<String> segments = segments(req);
        if (segments.size() != 2) {
            notFound(resp);
            return;
        }
        handle(req, resp, () -> {
            manager.delete(new RepositoryName(segments.get(0), segments.get(1)));
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws IOException;
    }

    record RepositoryBody(String owner, String name) {
    }

    record RepositoryListBody(List<RepositoryBody> repositories) {
    }

    record ErrorBody(String error) {
    }
}
