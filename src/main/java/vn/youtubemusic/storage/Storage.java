package vn.youtubemusic.storage;

import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.music.Track;
import java.sql.*;
import java.nio.file.Path;
import java.util.*;

public final class Storage {
    private final JavaPlugin plugin; private Connection connection;
    public Storage(JavaPlugin plugin) { this.plugin=plugin; open(); }
    private void open() {
        try { Class.forName("org.sqlite.JDBC");
            Path file=plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("storage.file","music.db"));
            java.nio.file.Files.createDirectories(file.getParent());
            connection=DriverManager.getConnection("jdbc:sqlite:"+file);
            try(Statement s=connection.createStatement()) { s.executeUpdate("CREATE TABLE IF NOT EXISTS favorites(uuid TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,PRIMARY KEY(uuid,url))"); s.executeUpdate("CREATE TABLE IF NOT EXISTS history(uuid TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,played_at INTEGER NOT NULL)"); }
        } catch(Exception e) { plugin.getLogger().warning("SQLite chưa sẵn sàng: "+e.getMessage()); }
    }
    public synchronized void favorite(UUID uuid, Track t) {
        if(connection==null)return; try(PreparedStatement p=connection.prepareStatement("INSERT OR REPLACE INTO favorites VALUES(?,?,?)")){p.setString(1,uuid.toString());p.setString(2,t.url());p.setString(3,t.title());p.executeUpdate();}catch(SQLException e){plugin.getLogger().warning(e.getMessage());}
    }
    public synchronized void history(UUID uuid, Track t) {
        if(connection==null)return; try(PreparedStatement p=connection.prepareStatement("INSERT INTO history VALUES(?,?,?,?)")){p.setString(1,uuid.toString());p.setString(2,t.url());p.setString(3,t.title());p.setLong(4,System.currentTimeMillis());p.executeUpdate();}catch(SQLException e){plugin.getLogger().warning(e.getMessage());}
    }
    public void close(){try{if(connection!=null)connection.close();}catch(SQLException ignored){}}
}
