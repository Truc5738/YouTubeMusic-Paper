package vn.youtubemusic.audio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Embedded JDK-only HTTP server.
 * Serves the local resolver bridge and generated resource packs on the same TCP port.
 * TCP and UDP may use the same numeric port because they are separate transports.
 */
public final class LocalAudioApiServer {
    private final JavaPlugin plugin;
    private final HttpClient client;
    private final Path packRoot;
    private HttpServer server;
    private String resolverUrl;
    private String apiKey;

    public LocalAudioApiServer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.packRoot = plugin.getDataFolder().toPath().resolve("resource-packs");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("audio.local-api.enabled", true)) return;

        int port = plugin.getConfig().getInt("audio.local-api.port", 26467);
        String bind = plugin.getConfig().getString("audio.local-api.bind", "0.0.0.0");
        resolverUrl = normalize(plugin.getConfig().getString("audio.resolver.url", ""));
        apiKey = plugin.getConfig().getString("audio.resolver.key", "").trim();

        try {
            Files.createDirectories(packRoot);
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext("/health", this::health);
            server.createContext("/api/audio", this::audio);
            server.createContext("/packs", this::packs);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            plugin.getLogger().info("Local Audio API + Resource Pack HTTP listening on TCP " + bind + ":" + port);
            if (resolverUrl.isBlank()) {
                plugin.getLogger().warning("audio.resolver.url chưa cấu hình; /api/audio sẽ trả resolver_not_configured.");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Không thể mở HTTP server TCP " + bind + ":" + port + ": " + e.getMessage());
        }
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }
        String body = "{"ok":true,"service":"YouTubeMusic HTTP","resolverConfigured":"
                + (!resolverUrl.isBlank()) + ","resourcePacks":true}";
        send(exchange, 200, body, "application/json; charset=utf-8");
    }

    private void audio(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }
        if (resolverUrl.isBlank()) {
            send(exchange, 503, "{"status":"error","code":"resolver_not_configured"}",
                    "application/json; charset=utf-8");
            return;
        }

        byte[] requestBody;
        try (InputStream in = exchange.getRequestBody()) {
            requestBody = in.readAllBytes();
        }
        if (requestBody.length == 0 || requestBody.length > 32_768) {
            send(exchange, 400, "{"status":"error","code":"invalid_request"}",
                    "application/json; charset=utf-8");
            return;
        }

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(resolverUrl))
                    .timeout(Duration.ofSeconds(plugin.getConfig().getInt("audio.resolver.timeout-seconds", 120)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            if (!apiKey.isBlank()) request.header("Authorization", "Api-Key " + apiKey);

            HttpResponse<String> response = client.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String contentType = response.headers().firstValue("content-type")
                    .orElse("application/json; charset=utf-8");
            send(exchange, response.statusCode(), response.body(), contentType);
        } catch (Exception e) {
            plugin.getLogger().warning("Audio resolver request failed: " + e.getMessage());
            send(exchange, 502, "{"status":"error","code":"resolver_unreachable"}",
                    "application/json; charset=utf-8");
        }
    }

    private void packs(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String prefix = "/packs/";
        if (!path.startsWith(prefix)) {
            send(exchange, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }

        String name = path.substring(prefix.length());
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\")
                || !name.endsWith(".zip")) {
            send(exchange, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }

        Path file = packRoot.resolve(name).normalize();
        if (!file.startsWith(packRoot.normalize()) || !Files.isRegularFile(file)) {
            send(exchange, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        exchange.sendResponseHeaders(200, Files.size(file));
        try (OutputStream out = exchange.getResponseBody()) {
            Files.copy(file, out);
        }
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("/+$", "");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
