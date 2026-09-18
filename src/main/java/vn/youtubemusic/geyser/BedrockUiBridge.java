package vn.youtubemusic.geyser;

import org.bukkit.entity.Player;
import vn.youtubemusic.music.MusicManager;
import vn.youtubemusic.storage.Storage;

/** Optional Bedrock UI bridge. Contains no Floodgate/Geyser classes so Java-only servers remain safe. */
public interface BedrockUiBridge {
    boolean isBedrock(Player player);
    void open(Player player);

    static BedrockUiBridge create(MusicManager music, Storage storage, boolean floodgatePresent) {
        if (!floodgatePresent) return new Noop();
        try {
            Class<?> type = Class.forName("vn.youtubemusic.geyser.BedrockMusicUi");
            return (BedrockUiBridge) type.getConstructor(MusicManager.class, Storage.class).newInstance(music, storage);
        } catch (Throwable ignored) {
            return new Noop();
        }
    }

    final class Noop implements BedrockUiBridge {
        @Override public boolean isBedrock(Player player) { return false; }
        @Override public void open(Player player) { }
    }
}
