package vn.youtubemusic.audio;

import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.music.Track;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** Real audio resolver using administrator-installed yt-dlp + ffmpeg. */
public final class YtDlpAudioBackend implements AudioBackend {
    private final JavaPlugin plugin; private final ExecutorService executor; private final Path cache; private final String ytDlp; private final String ffmpeg;
    public YtDlpAudioBackend(JavaPlugin plugin) {
        this.plugin = plugin; this.executor = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "YouTubeMusic-audio"); t.setDaemon(true); return t; });
        this.cache = plugin.getDataFolder().toPath().resolve("audio-cache"); this.ytDlp = plugin.getConfig().getString("audio.yt-dlp-command", "yt-dlp"); this.ffmpeg = plugin.getConfig().getString("audio.ffmpeg-command", "ffmpeg");
        try { Files.createDirectories(cache); } catch (IOException e) { plugin.getLogger().warning("Không thể tạo audio-cache: " + e.getMessage()); }
    }
    @Override public CompletableFuture<ResolvedAudio> resolveAndDownload(String url) { return CompletableFuture.supplyAsync(() -> { try { Metadata m = metadata(url); int max = plugin.getConfig().getInt("audio.max-duration-seconds", 3600); if (m.durationSeconds <= 0) throw new IOException("Không xác định được thời lượng bài hát."); if (m.durationSeconds > max) throw new IOException("Bài hát vượt quá thời lượng cho phép."); String safe = m.id.replaceAll("[^A-Za-z0-9_-]", "_"); Path output = cache.resolve(safe + ".ogg"); if (!Files.exists(output) || Files.size(output) < 1024) download(url, output); return new ResolvedAudio(new Track(m.title, url, m.durationSeconds, m.id, null), output); } catch (Exception e) { throw new CompletionException(e); } }, executor); }
    private Metadata metadata(String url) throws Exception { Process p = new ProcessBuilder(ytDlp, "--no-playlist", "--skip-download", "--print", "%(id)s\\t%(duration)s\\t%(title)s", url).redirectErrorStream(true).start(); String out = readAll(p.getInputStream()); boolean finished = p.waitFor(90, TimeUnit.SECONDS); if (!finished) { p.destroyForcibly(); throw new IOException("yt-dlp phản hồi quá lâu."); } if (p.exitValue() != 0 || out.isBlank()) throw new IOException("yt-dlp không lấy được thông tin YouTube: " + tail(out.trim(), 500)); String line = out.strip().substring(out.strip().lastIndexOf('\n') + 1); String[] a = line.split("\\t", 3); if (a.length < 3) throw new IOException("Phản hồi yt-dlp không hợp lệ."); return new Metadata(a[0], a[2], parseDuration(a[1])); }
    private void download(String url, Path output) throws Exception { Files.createDirectories(output.getParent()); Path base = output.resolveSibling(output.getFileName().toString().replaceFirst("\\.ogg$", ".part")); Path expected = base.resolveSibling(base.getFileName() + ".ogg"); Files.deleteIfExists(expected); Process p = new ProcessBuilder(ytDlp, "--no-playlist", "-x", "--audio-format", "vorbis", "--audio-quality", "5", "--ffmpeg-location", ffmpeg, "-o", base.toString() + ".%(ext)s", url).redirectErrorStream(true).start(); String log = readAll(p.getInputStream()); boolean finished = p.waitFor(20, TimeUnit.MINUTES); if (!finished) { p.destroyForcibly(); Files.deleteIfExists(expected); throw new IOException("yt-dlp tải âm thanh quá lâu."); } if (p.exitValue() != 0 || !Files.exists(expected) || Files.size(expected) < 1024) { Files.deleteIfExists(expected); throw new IOException("Không tải được âm thanh từ YouTube: " + tail(log, 500)); } try { Files.move(expected, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException e) { Files.move(expected, output, StandardCopyOption.REPLACE_EXISTING); } }
    private static long parseDuration(String s) { try { return (long) Double.parseDouble(s); } catch (NumberFormatException e) { return 0; } }
    private static String readAll(InputStream in) throws IOException { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    private static String tail(String s, int n) { return s.length() <= n ? s : s.substring(s.length() - n); }
    public void shutdown() { executor.shutdownNow(); }
    private record Metadata(String id, String title, long durationSeconds) {}
}
