package vn.youtubemusic.storage;

import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.music.Track;
import java.sql.*;
import java.nio.file.Path;
import java.util.*;

/** Persistent favorites/history storage. All public methods are synchronized for SQLite safety. */
public final class Storage {
    private final JavaPlugin plugin;
    private Connection connection;

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        open();
    }

    private void open() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path file = plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("storage.file", "music.db"));
            if (file.getParent() != null) java.nio.file.Files.createDirectories(file.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("PRAGMA busy_timeout=5000");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS favorites(uuid TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,PRIMARY KEY(uuid,url))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS history(uuid TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,played_at INTEGER NOT NULL)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_uuid_time ON history(uuid, played_at DESC)");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("SQLite chưa sẵn sàng: " + e.getMessage());
        }
    }

    public synchronized void favorite(UUID uuid, Track t) {
        if (connection == null || t == null) return;
        try (PreparedStatement p = connection.prepareStatement("INSERT OR REPLACE INTO favorites VALUES(?,?,?)")) {
            p.setString(1, uuid.toString());
            p.setString(2, t.url());
            p.setString(3, t.title());
            p.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Không lưu được mục yêu thích: " + e.getMessage());
        }
    }

    public synchronized boolean unfavorite(UUID uuid, String url) {
        if (connection == null) return false;
        try (PreparedStatement p = connection.prepareStatement("DELETE FROM favorites WHERE uuid=? AND url=?")) {
            p.setString(1, uuid.toString());
            p.setString(2, url);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Không xóa được mục yêu thích: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean isFavorite(UUID uuid, String url) {
        if (connection == null) return false;
        try (PreparedStatement p = connection.prepareStatement("SELECT 1 FROM favorites WHERE uuid=? AND url=? LIMIT 1")) {
            p.setString(1, uuid.toString());
            p.setString(2, url);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<Track> favorites(UUID uuid, int limit) {
        List<Track> out = new ArrayList<>();
        if (connection == null) return out;
        try (PreparedStatement p = connection.prepareStatement("SELECT url,title FROM favorites WHERE uuid=? ORDER BY title COLLATE NOCASE LIMIT ?")) {
            p.setString(1, uuid.toString());
            p.setInt(2, Math.max(1, Math.min(100, limit)));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(new Track(r.getString("title"), r.getString("url"), 0, Integer.toHexString(r.getString("url").hashCode()), uuid));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Không đọc được mục yêu thích: " + e.getMessage());
        }
        return out;
    }

    public synchronized void history(UUID uuid, Track t) {
        if (connection == null || t == null) return;
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO history VALUES(?,?,?,?)")) {
            p.setString(1, uuid.toString());
            p.setString(2, t.url());
            p.setString(3, t.title());
            p.setLong(4, System.currentTimeMillis());
            p.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Không lưu được lịch sử: " + e.getMessage());
        }
    }

    public synchronized List<Track> history(UUID uuid, int limit) {
        List<Track> out = new ArrayList<>();
        if (connection == null) return out;
        try (PreparedStatement p = connection.prepareStatement("SELECT url,title,played_at FROM history WHERE uuid=? ORDER BY played_at DESC LIMIT ?")) {
            p.setString(1, uuid.toString());
            p.setInt(2, Math.max(1, Math.min(100, limit)));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(new Track(r.getString("title"), r.getString("url"), 0, Integer.toHexString((r.getString("url") + r.getLong("played_at")).hashCode()), uuid));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Không đọc được lịch sử: " + e.getMessage());
        }
        return out;
    }

    public synchronized boolean removeFavoriteByIndex(UUID uuid, int index) {
        List<Track> list = favorites(uuid, 100);
        if (index < 1 || index > list.size()) return false;
        return unfavorite(uuid, list.get(index - 1).url());
    }

    public synchronized boolean clearHistory(UUID uuid) {
        if (connection == null) return false;
        try (PreparedStatement p = connection.prepareStatement("DELETE FROM history WHERE uuid=?")) {
            p.setString(1, uuid.toString());
            p.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Không xóa được lịch sử: " + e.getMessage());
            return false;
        }
    }

    public synchronized void close() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
        connection = null;
    }
}
