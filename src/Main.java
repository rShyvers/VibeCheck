import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * VibeCheck verdict view.
 *
 * Serves the single-page verdict dashboard (web/index.html) and the JSON
 * verdict it renders (/verdict.json). The verdict is produced by the Lean
 * harness in rShyvers/semantic-mips (pinned submodule); until a real run's
 * output is dropped at out/verdict.json, the bundled sample verdict is
 * served so the demo always has something true to show.
 *
 * POST /run takes raw MIPS assembly text, pipes it to the Lean `MipsRun`
 * interpreter (semantic-mips/.lake/build/bin/MipsRun, or env
 * VIBE_CHECK_MIPSRUN), and returns the interpreter's JSON verbatim.
 *
 * Modes:
 *   normal  — serve forever, open the browser. `make run`.
 *   CI      — env CI set: self-test the endpoints on an ephemeral port,
 *             print PASS/FAIL, exit. Keeps `make install && make run`
 *             non-blocking in GitHub Actions.
 */
public class Main {

    static final String INDEX_PATH = "web/index.html";
    static final String REAL_VERDICT_PATH = "out/verdict.json";
    static final String SAMPLE_VERDICT_PATH = "web/sample-verdict.json";
    static final String DEFAULT_MIPSRUN_PATH = "semantic-mips/.lake/build/bin/MipsRun";
    static final long MIPSRUN_TIMEOUT_SECONDS = 10;
    /** One thread per pipe end, so stdin and stdout never wait on each other. */
    static final java.util.concurrent.ExecutorService PIPE_THREADS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mipsrun-pipe");
        t.setDaemon(true);
        return t;
    });

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("VIBE_CHECK_PORT", "8321"));
        Path verdictOverride = args.length > 0 ? Path.of(args[0]) : null;

        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            System.exit(selfTest(verdictOverride));
        }

        HttpServer server = start(port, verdictOverride);
        String url = "http://localhost:" + port + "/";
        System.out.println("VibeCheck verdict view: " + url);
        System.out.println("Verdict source: " + verdictSource(verdictOverride));
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.out.println("(no browser available; open the URL above)");
            }
        } catch (Exception e) {
            System.out.println("(could not auto-open browser: " + e.getMessage() + ")");
        }
        // serve forever
        Thread.currentThread().join();
    }

    static HttpServer start(int port, Path verdictOverride) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/", ex -> serveFile(ex, INDEX_PATH, "text/html; charset=utf-8"));
        server.createContext("/verdict.json", ex -> {
            Path v = verdictSourcePath(verdictOverride);
            serveFile(ex, v.toString(), "application/json; charset=utf-8");
        });
        server.createContext("/run", Main::handleRun);
        server.start();
        return server;
    }

    static Path verdictSourcePath(Path override) {
        if (override != null && Files.exists(override)) return override;
        if (Files.exists(Path.of(REAL_VERDICT_PATH))) return Path.of(REAL_VERDICT_PATH);
        return Path.of(SAMPLE_VERDICT_PATH);
    }

    static String verdictSource(Path override) {
        return verdictSourcePath(override).toString();
    }

    static void serveFile(com.sun.net.httpserver.HttpExchange ex, String path, String type) {
        try {
            Path p = Path.of(path);
            if (!Files.exists(p)) {
                byte[] msg = ("missing " + path + " (run from the repo root)").getBytes();
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(500, msg.length);
                ex.getResponseBody().write(msg);
            } else {
                byte[] body = Files.readAllBytes(p);
                ex.getResponseHeaders().set("Content-Type", type);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            }
        } catch (IOException e) {
            try {
                byte[] msg = ("error: " + e.getMessage()).getBytes();
                ex.sendResponseHeaders(500, msg.length);
                ex.getResponseBody().write(msg);
            } catch (IOException ignored) { }
        } finally {
            ex.close();
        }
    }

    /** Path of the MipsRun interpreter: env VIBE_CHECK_MIPSRUN, else the lake build output. */
    static Path mipsRunPath() {
        String override = System.getenv("VIBE_CHECK_MIPSRUN");
        if (override != null && !override.isEmpty()) return Path.of(override);
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        return Path.of(windows ? DEFAULT_MIPSRUN_PATH + ".exe" : DEFAULT_MIPSRUN_PATH);
    }

    /**
     * POST /run: the request body is raw .asm text. It is written to MipsRun's
     * stdin, and MipsRun's stdout (one JSON object) is returned as-is with
     * HTTP 200. Assembly failures are reported inside that JSON, not as HTTP
     * errors. 503 if the interpreter is not built, 504 if it runs past the
     * timeout, 502 if it prints nothing.
     */
    static void handleRun(HttpExchange ex) {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Allow", "POST");
                sendJson(ex, 405, "{\"error\":\"use POST with the assembly text as the body\"}");
                return;
            }
            byte[] asm = ex.getRequestBody().readAllBytes();
            Path bin = mipsRunPath();
            if (!Files.isRegularFile(bin) || !Files.isExecutable(bin)) {
                sendJson(ex, 503, "{\"error\":\"interpreter not built — run 'make build-semantics' from the repo root\"}");
                return;
            }

            Process p = new ProcessBuilder(bin.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            // Feed stdin and drain stdout on their own threads, so a program that
            // ignores its input or floods its output cannot block the timeout.
            CompletableFuture.runAsync(() -> {
                try (OutputStream in = p.getOutputStream()) {
                    in.write(asm);
                } catch (IOException ignored) { } // interpreter exited without reading all input
            }, PIPE_THREADS);
            CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try (InputStream out = p.getInputStream()) {
                    return out.readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            }, PIPE_THREADS);

            if (!p.waitFor(MIPSRUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                sendJson(ex, 504, "{\"error\":\"interpreter timed out\"}");
                return;
            }
            byte[] stdout;
            try {
                // bounded: a leftover child process could keep stdout open after exit
                stdout = reader.get(MIPSRUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                sendJson(ex, 504, "{\"error\":\"interpreter timed out\"}");
                return;
            }
            if (new String(stdout, StandardCharsets.UTF_8).isBlank()) {
                sendJson(ex, 502, "{\"error\":\"interpreter printed no output (exit code " + p.exitValue() + ")\"}");
                return;
            }
            sendBytes(ex, 200, stdout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendJson(ex, 500, "{\"error\":\"interrupted while running the interpreter\"}");
        } catch (IOException e) {
            sendJson(ex, 500, "{\"error\":\"could not run the interpreter\"}");
        } finally {
            ex.close();
        }
    }

    static void sendJson(HttpExchange ex, int status, String json) {
        sendBytes(ex, status, json.getBytes(StandardCharsets.UTF_8));
    }

    static void sendBytes(HttpExchange ex, int status, byte[] body) {
        try {
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
        } catch (IOException ignored) { } // client went away
    }

    /** CI mode: boot the server, hit each endpoint, verify it answers. */
    static int selfTest(Path verdictOverride) throws Exception {
        HttpServer server = start(0, verdictOverride); // ephemeral port
        int port = server.getAddress().getPort();
        try {
            String index = httpGet("http://localhost:" + port + "/");
            if (!index.contains("verdict")) throw new AssertionError("index page missing marker");
            String json = httpGet("http://localhost:" + port + "/verdict.json");
            if (!json.contains("\"status\"")) throw new AssertionError("verdict JSON missing status field");
            // /run: 200 with interpreter JSON if MipsRun is built, 503 if not. Both are correct.
            String[] run = httpPost("http://localhost:" + port + "/run", "li $v0, 10\nsyscall\n");
            String runResult;
            if (run[0].equals("200") && run[1].contains("\"assembled\"")) {
                runResult = "interpreter ran";
            } else if (run[0].equals("503")) {
                runResult = "interpreter not built (503)";
            } else {
                throw new AssertionError("/run -> HTTP " + run[0] + ": " + run[1]);
            }
            System.out.println("VibeCheck verdict view: self-test PASS (port " + port + ", verdict "
                    + verdictSource(verdictOverride) + ", /run " + runResult + ")");
            return 0;
        } catch (AssertionError e) {
            System.out.println("VibeCheck verdict view: self-test FAIL — " + e.getMessage());
            return 1;
        } finally {
            server.stop(0);
        }
    }

    /** POST a text/plain body; returns {status code, response body}. */
    static String[] httpPost(String url, String body) throws IOException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        var req = java.net.http.HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            return new String[] { String.valueOf(resp.statusCode()), resp.body() };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while posting to " + url, e);
        }
    }

    static String httpGet(String url) throws IOException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        var req = java.net.http.HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        try {
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new IOException(url + " -> HTTP " + resp.statusCode());
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore the interrupt flag
            throw new IOException("interrupted while fetching " + url, e);
        }
    }
}
