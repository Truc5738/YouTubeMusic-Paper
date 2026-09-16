package vn.youtubemusic.audio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.Executors;

/** Tiny embedded HTTP server for generated resource packs. Configure a public URL in config.yml. */
public final class ResourcePackServer {
    private final Path root; private HttpServer server; private int port;
    public ResourcePackServer(Path root, int port) { this.root = root; this.port = port; }
    public void start() throws IOException {
        Files.createDirectories(root); server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0); server.createContext("/packs", this::handle); server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
    }
    private void handle(HttpExchange e) throws IOException {
        if (!"GET".equalsIgnoreCase(e.getRequestMethod())) { e.sendResponseHeaders(405, -1); return; }
        String raw = e.getRequestURI().getPath(); String name = raw.substring(raw.lastIndexOf('/') + 1);
        if (name.isBlank() || name.contains("..") || !name.endsWith(".zip")) { e.sendResponseHeaders(404, -1); return; }
        Path file = root.resolve(name).normalize(); if (!file.startsWith(root.normalize()) || !Files.isRegularFile(file)) { e.sendResponseHeaders(404, -1); return; }
        e.getResponseHeaders().set("Content-Type", "application/zip"); e.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable"); e.sendResponseHeaders(200, Files.size(file));
        try (OutputStream out = e.getResponseBody()) { Files.copy(file, out); }
    }
    public void stop() { if (server != null) server.stop(0); }
}
