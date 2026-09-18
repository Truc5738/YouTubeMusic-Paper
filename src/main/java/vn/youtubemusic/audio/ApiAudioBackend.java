package vn.youtubemusic.audio;

import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.music.Track;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API-only audio backend. No yt-dlp and no local ffmpeg.
 * Uses a configurable Cobalt-compatible API to resolve/download OGG audio.
 * Optional YouTube Data API supplies accurate title + duration metadata.
 */
public final class ApiAudioBackend implements AudioBackend {
    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final HttpClient http;
    private final Path cache;
    private final String apiUrl;
    private final String apiKey;
    private final String youtubeKey;
    private final int defaultDuration;

    public ApiAudioBackend(JavaPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "YouTubeMusic-api");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.cache = plugin.getDataFolder().toPath().resolve(
                plugin.getConfig().getString("audio.cache-directory", "audio-cache"));
        this.apiUrl = normalizeApiUrl(plugin.getConfig().getString(
                "audio.api.url", "http://127.0.0.1:9000/"));
        this.apiKey = plugin.getConfig().getString("audio.api.key", "").trim();
        this.youtubeKey = plugin.getConfig().getString("audio.youtube-data-api-key", "").trim();
        this.defaultDuration = Math.max(1, plugin.getConfig().getInt(
                "audio.default-duration-seconds", 600));
        try {
            Files.createDirectories(cache);
        } catch (IOException e) {
            plugin.getLogger().warning("Không thể tạo audio-cache: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<ResolvedAudio> resolveAndDownload(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Metadata meta = youtubeMetadata(url);
                String id = meta.id;
                Path output = cache.resolve(id + ".ogg");

                if (!Files.exists(output) || Files.size(output) < 1024) {
                    String response = callResolverApi(url);
                    String audioUrl = jsonValue(response, "url");
                    if (audioUrl == null || audioUrl.isBlank()) {
                        throw new IOException("API không trả về URL âm thanh.");
                    }
                    download(audioUrl, output);
                    String apiTitle = firstNonBlank(jsonValue(response, "filename"), "");
                    if (!apiTitle.isBlank() && meta.title.isBlank()) {
                        meta = new Metadata(id, stripExtension(apiTitle), meta.durationSeconds);
                    }
                }

                long duration = meta.durationSeconds > 0 ? meta.durationSeconds : defaultDuration;
                if (duration > plugin.getConfig().getInt("audio.max-duration-seconds", 3600)) {
                    throw new IOException("Bài hát vượt quá thời lượng cho phép.");
                }

                return new ResolvedAudio(
                        new Track(meta.title.isBlank() ? id : meta.title, url, duration, id, null),
                        output
                );
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Spotify-style keyword search using YouTube Data API v3. */
    public CompletableFuture<java.util.List<Track>> search(String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (youtubeKey.isBlank()) throw new IOException("Chưa cấu hình YouTube Data API key.");
                String q = query == null ? "" : query.trim();
                if (q.isBlank()) return java.util.List.of();
                int max = Math.max(1, Math.min(10, limit));
                String endpoint = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=" + max
                        + "&q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8)
                        + "&key=" + java.net.URLEncoder.encode(youtubeKey, java.nio.charset.StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(20))
                        .header("Accept","application/json").GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IOException("YouTube Search API HTTP " + res.statusCode());
                java.util.List<Track> out = new java.util.ArrayList<>();
                Matcher m = Pattern.compile("\\\"videoId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[\\s\\S]*?\\\"title\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(res.body());
                while (m.find() && out.size() < max) {
                    String id=m.group(1); String title=unescape(m.group(2));
                    if (id != null && !id.isBlank()) out.add(new Track(title, "https://www.youtube.com/watch?v="+id, defaultDuration, id));
                }
                return out;
            } catch(Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    private String callResolverApi(String sourceUrl) throws Exception {
        String body = "{"
                + "\"url\":" + quote(sourceUrl) + ","
                + "\"downloadMode\":\"audio\","
                + "\"audioFormat\":\"ogg\","
                + "\"audioBitrate\":\"" + plugin.getConfig().getString("audio.api.bitrate", "128") + "\","
                + "\"filenameStyle\":\"pretty\""
                + "}";

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("audio.api.timeout-seconds", 120)))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (!apiKey.isBlank()) {
            b.header("Authorization", "Api-Key " + apiKey);
        }

        HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API âm thanh HTTP " + response.statusCode() + ": " + tail(response.body(), 400));
        }

        String status = jsonValue(response.body(), "status");
        if ("error".equalsIgnoreCase(status)) {
            String code = jsonValue(response.body(), "code");
            if (code == null) code = jsonValue(response.body(), "error");
            throw new IOException("API âm thanh từ chối yêu cầu: " + (code == null ? "unknown_error" : code));
        }
        return response.body();
    }

    private void download(String audioUrl, Path output) throws Exception {
        URI uri = URI.create(audioUrl);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("API trả về URL âm thanh không an toàn.");
        }

        Path part = output.resolveSibling(output.getFileName() + ".part");
        Files.deleteIfExists(part);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("audio.download-timeout-seconds", 300)))
                .header("Accept", "audio/ogg,audio/*,*/*;q=0.8")
                .GET().build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream ignored = response.body()) {}
            throw new IOException("Không tải được audio từ API: HTTP " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }

        if (!Files.exists(part) || Files.size(part) < 1024) {
            Files.deleteIfExists(part);
            throw new IOException("API trả về file âm thanh rỗng hoặc quá nhỏ.");
        }

        try {
            Files.move(part, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Metadata youtubeMetadata(String url) throws Exception {
        String id = extractVideoId(url);
        if (id == null) throw new IOException("Không lấy được YouTube video ID.");

        if (youtubeKey.isBlank()) {
            return new Metadata(id, "", 0);
        }

        String endpoint = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id="
                + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8)
                + "&key=" + java.net.URLEncoder.encode(youtubeKey, java.nio.charset.StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("YouTube Data API HTTP " + response.statusCode());
        }

        String title = jsonValue(response.body(), "title");
        String durationIso = jsonValue(response.body(), "duration");
        long duration = parseIsoDuration(durationIso);
        if (title == null) title = "";
        return new Metadata(id, title, duration);
    }

    private static String extractVideoId(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            if (host == null) return null;
            if (host.equalsIgnoreCase("youtu.be")) {
                String p = u.getPath();
                return p == null ? null : p.replaceFirst("^/", "").split("/")[0];
            }
            String query = u.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2 && kv[0].equals("v")) return kv[1];
                }
            }
            String path = u.getPath();
            if (path != null && path.startsWith("/shorts/")) return path.substring(8).split("/")[0];
            if (path != null && path.startsWith("/embed/")) return path.substring(7).split("/")[0];
        } catch (Exception ignored) {}
        return null;
    }

    private static long parseIsoDuration(String value) {
        if (value == null || value.isBlank()) return 0;
        Matcher m = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matcher(value);
        if (!m.matches()) return 0;
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        long sec = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        return h * 3600 + min * 60 + sec;
    }

    private static String jsonValue(String json, String key) {
        if (json == null) return null;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(json);
        if (!m.find()) return null;
        return unescape(m.group(1));
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String normalizeApiUrl(String s) {
        if (s == null || s.isBlank()) return "http://127.0.0.1:9000/";
        return s.endsWith("/") ? s : s + "/";
    }

    private static String firstNonBlank(String a, String fallback) {
        return a == null || a.isBlank() ? fallback : a;
    }

    private static String stripExtension(String s) {
        return s.replaceFirst("(?i)\\.(ogg|mp3|opus|wav)$", "");
    }

    private static String tail(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    public String apiUrl() { return apiUrl; }
    public boolean hasYoutubeMetadataKey() { return !youtubeKey.isBlank(); }
    public void shutdown() { executor.shutdownNow(); }

    private record Metadata(String id, String title, long durationSeconds) {}
}
