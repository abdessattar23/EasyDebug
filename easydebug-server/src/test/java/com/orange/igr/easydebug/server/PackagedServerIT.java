package com.orange.igr.easydebug.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.jar.JarFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PackagedServerIT {

    @TempDir
    Path home;

    @Test
    void packagedJarStartsFromDefaultsAndKeepsRowsAcrossRestart() throws Exception {
        int port = freePort();
        Path database = home.resolve("data").resolve("easydebug.mv.db");
        assertThat(database).doesNotExist();
        Path jar = packagedJar();

        assertThat(packagedConfig(jar)).contains("jdbc:h2:file:./data/easydebug;DB_CLOSE_ON_EXIT=FALSE");
        assertThat(packagedConfig(jar)).doesNotContain("AUTO_SERVER");
        String log = startAndStop(jar, port);
        assertThat(log).contains("Started EasyDebugServerApplication");
        assertThat(log).doesNotContain("Feature not supported");
        assertThat(database).exists();

        HttpClient http = HttpClient.newHttpClient();
        String body = """
                {"id":"keep-1","kind":"http","url":"http://example/kept","occurredAt":"2026-10-19T12:00:00Z"}
                """;
        try (ServerHandle first = start(jar, port)) {
            awaitStarted(first);
            HttpResponse<String> created = http.send(request(port, "/api/events")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(created.statusCode()).isEqualTo(201);
            HttpResponse<String> stored = http.send(request(port, "/api/events/keep-1").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(stored.statusCode()).isEqualTo(200);
            assertThat(stored.body()).contains("http://example/kept");
            Thread.sleep(1_500L);
        }
        try (ServerHandle second = start(jar, port)) {
            awaitStarted(second);
            HttpResponse<String> stored = http.send(request(port, "/api/events/keep-1").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(stored.statusCode()).isEqualTo(200);
            assertThat(stored.body()).contains("http://example/kept");
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpRequest.Builder request(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private static String packagedConfig(Path jar) throws Exception {
        try (JarFile archive = new JarFile(jar.toFile())) {
            var entry = archive.getJarEntry("BOOT-INF/classes/application.yml");
            if (entry == null) {
                throw new AssertionError("packaged application.yml is missing");
            }
            return new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path packagedJar() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("target"))) {
            return files.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("easydebug-server-")
                                && name.endsWith(".jar")
                                && !name.contains("sources")
                                && !name.contains("javadoc")
                                && !name.endsWith(".jar.original");
                    })
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("packaged server jar is missing"));
        }
    }

    private String startAndStop(Path jar, int port) throws Exception {
        try (ServerHandle handle = start(jar, port)) {
            return awaitStarted(handle);
        }
    }

    private ServerHandle start(Path jar, int port) throws Exception {
        awaitPortFree(port);
        ProcessBuilder command = new ProcessBuilder("java", "-jar", jar.toAbsolutePath().toString());
        command.directory(home.toFile());
        command.redirectErrorStream(true);
        command.environment().put("EASYDEBUG_SERVER_PORT", Integer.toString(port));
        return new ServerHandle(command.start());
    }

    private static void awaitPortFree(int port) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            try (ServerSocket socket = new ServerSocket(port)) {
                socket.setReuseAddress(true);
                return;
            } catch (Exception occupied) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("port " + port + " stayed busy");
    }

    private static String awaitStarted(ServerHandle handle) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < end) {
            if (handle.log().contains("APPLICATION FAILED TO START")) {
                throw new AssertionError(handle.log());
            }
            if (handle.log().contains("Started EasyDebugServerApplication")) {
                return handle.log();
            }
            if (!handle.process.isAlive()) {
                throw new AssertionError(handle.log());
            }
            Thread.sleep(50);
        }
        throw new AssertionError(handle.log());
    }

    private static final class ServerHandle implements AutoCloseable {
        private final Process process;
        private final StringBuilder log = new StringBuilder();

        private ServerHandle(Process process) {
            this.process = process;
            Thread reader = new Thread(() -> {
                try (BufferedReader input = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        synchronized (log) {
                            log.append(line).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                    // the process is closing
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        private String log() {
            synchronized (log) {
                return log.toString();
            }
        }

        @Override
        public void close() throws Exception {
            process.destroy();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
