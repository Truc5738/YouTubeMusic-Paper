package vn.youtubemusic;

import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.command.MusicCommand;
import vn.youtubemusic.geyser.BedrockUiBridge;
import vn.youtubemusic.gui.MusicGui;
import vn.youtubemusic.music.MusicManager;
import vn.youtubemusic.storage.Storage;

public final class YouTubeMusicPlugin extends JavaPlugin {
    private Storage storage;
    private MusicManager music;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("messages_vi.yml", false);
        int javaFeature = Runtime.version().feature();
        if (javaFeature < 25) {
            getLogger().severe("YouTubeMusic 2.8.7 yêu cầu Java 25 trở lên. Java hiện tại: " + Runtime.version());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        validateAudioConfiguration();
        storage = new Storage(this);
        music = new MusicManager(this, storage);
        MusicGui gui = new MusicGui(this, music);
        boolean floodgatePresent = getServer().getPluginManager().getPlugin("floodgate") != null;
        BedrockUiBridge bedrock = BedrockUiBridge.create(music, floodgatePresent);
        MusicCommand command = new MusicCommand(this, music, gui, bedrock, storage);
        if (getCommand("music") != null) {
            getCommand("music").setExecutor(command);
            getCommand("music").setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(gui, this);
        getLogger().info("YouTubeMusic 2.8.7 đã bật — Paper 26.2+ / Java 25+ / Java UI + Bedrock Forms.");
        if (floodgatePresent) getLogger().info("Floodgate đã sẵn sàng — Bedrock Forms được bật.");
    }

    private void validateAudioConfiguration() {
        String packUrl = getConfig().getString("audio.resource-pack-url-template", "");
        if (packUrl.isBlank() || packUrl.contains("YOUR_PUBLIC_IP")) {
            getLogger().warning("Resource-pack URL chưa cấu hình; YouTubeMusic sẽ không thể phát audio Java cho đến khi cấu hình audio.resource-pack-url-template.");
        }
        if (!getConfig().getBoolean("audio.resource-pack-server.enabled", true)) {
            getLogger().warning("Embedded resource-pack server đang tắt; URL phải trỏ tới máy chủ cung cấp ZIP bên ngoài.");
        }
        int port = getConfig().getInt("audio.resource-pack-server-port", 8126);
        if (port < 1 || port > 65535) getLogger().warning("audio.resource-pack-server-port không hợp lệ: " + port);
        int maxVolume = getConfig().getInt("player.max-volume", 100);
        if (maxVolume < 1 || maxVolume > 100) getLogger().warning("player.max-volume phải nằm trong 1-100.");
    }

    @Override public void onDisable() {
        if (music != null) music.shutdown();
        if (storage != null) storage.close();
    }
}
