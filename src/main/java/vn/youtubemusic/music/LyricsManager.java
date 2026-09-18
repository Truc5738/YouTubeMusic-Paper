package vn.youtubemusic.music;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Private, timestamped LRC lyrics. Only players who enable lyrics receive the lines. */
public final class LyricsManager {
    private record Line(long millis, String text) {}
    private final JavaPlugin plugin;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Line>> cache = new ConcurrentHashMap<>();
    private Track current;
    private long startedAt;
    private long offsetMillis;
    private boolean paused;
    private int lastLine = -1;

    public LyricsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Path dir = plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("lyrics.directory", "lyrics"));
        try { Files.createDirectories(dir); } catch (IOException e) { plugin.getLogger().warning("Không tạo được thư mục lời bài hát: " + e.getMessage()); }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 5L);
    }

    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (enabled.remove(id)) { p.sendMessage("§e🎤 Lời bài hát: §cTẮT"); return false; }
        enabled.add(id); p.sendMessage("§a🎤 Lời bài hát: §aBẬT");
        sendCurrentLine(p, true);
        return true;
    }

    public boolean isEnabled(Player p) { return enabled.contains(p.getUniqueId()); }

    public boolean setEnabled(Player p, boolean value) {
        if (value) enabled.add(p.getUniqueId()); else enabled.remove(p.getUniqueId());
        p.sendMessage(value ? "§a🎤 Lời bài hát: §aBẬT" : "§e🎤 Lời bài hát: §cTẮT");
        if (value) sendCurrentLine(p, true);
        return value;
    }

    public void start(Track track) {
        current = track;
        startedAt = System.currentTimeMillis();
        offsetMillis = 0;
        paused = false;
        lastLine = -1;
        load(track);
        for (Player p : Bukkit.getOnlinePlayers()) if (enabled.contains(p.getUniqueId())) p.sendMessage("§b🎤 Lời bài hát riêng: §f" + track.title());
    }

    public void pause() {
        if (current == null || paused) return;
        offsetMillis = elapsed();
        paused = true;
    }

    public void resume() {
        if (current == null || !paused) return;
        startedAt = System.currentTimeMillis();
        paused = false;
        lastLine = lineAt(offsetMillis);
    }

    public void stop() {
        current = null;
        paused = false;
        offsetMillis = 0;
        lastLine = -1;
    }

    public void skip() { stop(); }

    private long elapsed() { return paused ? offsetMillis : Math.max(0, System.currentTimeMillis() - startedAt); }

    private void tick() {
        if (current == null || paused) return;
        long pos = elapsed();
        int index = lineAt(pos);
        if (index < 0 || index == lastLine) return;
        lastLine = index;
        Line line = cache.getOrDefault(current.id(), List.of()).get(index);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (enabled.contains(p.getUniqueId())) p.sendMessage("§d♪ §f" + line.text());
        }
    }

    private void sendCurrentLine(Player p, boolean force) {
        if (current == null || !enabled.contains(p.getUniqueId())) return;
        int index = lineAt(elapsed());
        List<Line> lines = cache.getOrDefault(current.id(), List.of());
        if (index >= 0 && index < lines.size() && (force || index != lastLine)) {
            p.sendMessage("§d♪ §f" + lines.get(index).text());
        }
    }

    private int lineAt(long millis) {
        List<Line> lines = current == null ? List.of() : cache.getOrDefault(current.id(), List.of());
        int lo = 0, hi = lines.size() - 1, answer = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).millis() <= millis) { answer = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return answer;
    }

    private List<Line> load(Track track) {
        return cache.computeIfAbsent(track.id(), id -> {
            Path file = plugin.getDataFolder().toPath()
                    .resolve(plugin.getConfig().getString("lyrics.directory", "lyrics"))
                    .resolve(safe(id) + ".lrc");
            if (!Files.isRegularFile(file)) return List.of();
            try {
                List<Line> out = new ArrayList<>();
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int pos = raw.indexOf(']');
                    if (!raw.startsWith("[") || pos < 0) continue;
                    String stamp = raw.substring(1, pos).trim();
                    String text = raw.substring(pos + 1).trim();
                    long ms = parseTime(stamp);
                    if (ms >= 0 && !text.isBlank()) out.add(new Line(ms, text));
                }
                out.sort(Comparator.comparingLong(Line::millis));
                return List.copyOf(out);
            } catch (IOException e) {
                plugin.getLogger().warning("Không đọc được LRC " + file + ": " + e.getMessage());
                return List.of();
            }
        });
    }

    private long parseTime(String s) {
        try {
            String[] p = s.split(":");
            if (p.length != 2) return -1;
            double seconds = Double.parseDouble(p[1]);
            return (long) ((Integer.parseInt(p[0]) * 60 + seconds) * 1000);
        } catch (NumberFormatException e) { return -1; }
    }

    private String safe(String s) { return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    public void reload() { cache.clear(); }
    public void shutdown() { enabled.clear(); cache.clear(); current = null; }
}
