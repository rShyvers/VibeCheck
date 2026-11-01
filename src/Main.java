import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * VibeCheck verdict view.
 *
 * Serves the single-page verdict dashboard (web/index.html) and the JSON
 * verdict it renders (/verdict.json). The verdict is produced by the Lean
 * harness in rShyvers/semantic-mips (pinned submodule); until a real run's
 * output is dropped at out/verdict.json, the bundled sample verdict is
 * served so the demo always has something true to show.
 *
 * Modes:
 *   normal  — serve forever, open the browser. `make run`.
 *   CI      — env CI set: self-test both endpoints on an ephemeral port,
 *             print PASS/FAIL, exit. Keeps `make install && make run`
 *             non-blocking in GitHub Actions.
 */
public class Main {

    static final String INDEX_PATH = "web/index.html";
    static final String REAL_VERDICT_PATH = "out/verdict.json";
    static final String SAMPLE_VERDICT_PATH = "web/sample-verdict.json";

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

    /** CI mode: boot the server, hit both endpoints, verify they answer. */
    static int selfTest(Path verdictOverride) throws Exception {
        HttpServer server = start(0, verdictOverride); // ephemeral port
        int port = server.getAddress().getPort();
        try {
            String index = httpGet("http://localhost:" + port + "/");
            if (!index.contains("verdict")) throw new AssertionError("index page missing marker");
            String json = httpGet("http://localhost:" + port + "/verdict.json");
            if (!json.contains("\"status\"")) throw new AssertionError("verdict JSON missing status field");
            System.out.println("VibeCheck verdict view: self-test PASS (port " + port + ", verdict "
                    + verdictSource(verdictOverride) + ")");
            return 0;
        } catch (AssertionError e) {
            System.out.println("VibeCheck verdict view: self-test FAIL — " + e.getMessage());
            return 1;
        } finally {
            server.stop(0);
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
