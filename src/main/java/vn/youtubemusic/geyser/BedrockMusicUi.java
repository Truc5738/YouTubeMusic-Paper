package vn.youtubemusic.geyser;

import org.bukkit.entity.Player;
import vn.youtubemusic.music.MusicManager;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import java.util.function.Consumer;

/** Bedrock-only UI using the official Floodgate/Cumulus Forms API. */
public final class BedrockMusicUi implements BedrockUiBridge {
    private final MusicManager music;
    public BedrockMusicUi(MusicManager music) { this.music = music; }
    public boolean isBedrock(Player player) { try { return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId()); } catch (Throwable ignored) { return false; } }
    public void open(Player player) {
        if (!player.hasPermission("youtubemusic.use") || !isBedrock(player)) return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(player.getUniqueId()); if (fp == null) return;
        Consumer<SimpleFormResponse> input = r -> openInput(player);
        Consumer<SimpleFormResponse> pause = r -> { if (music.paused()) music.resume(); else music.pause(); open(player); };
        Consumer<SimpleFormResponse> skip = r -> { music.skip(); open(player); };
        Consumer<SimpleFormResponse> stop = r -> { music.stop(); open(player); };
        Consumer<SimpleFormResponse> queue = r -> openQueue(player);
        Consumer<SimpleFormResponse> loop = r -> { music.toggleLoop(); open(player); };
        Consumer<SimpleFormResponse> shuffle = r -> { music.toggleShuffle(); open(player); };
        Consumer<SimpleFormResponse> radio = r -> { music.toggleRadio(); open(player); };
        Consumer<SimpleFormResponse> volume = r -> openVolume(player);
        SimpleForm form = SimpleForm.builder().title("🎵 YouTube Music")
            .content("§bTrung tâm âm nhạc\n§7Bài hiện tại: " + (music.current() == null ? "Chưa có" : music.current().title()))
            .button("🔗 Dán link YouTube", input)
            .button("⏯ Tạm dừng / Tiếp tục", pause)
            .button("⏭ Bỏ qua", skip)
            .button("⏹ Dừng", stop)
            .button("📋 Hàng đợi", queue)
            .button("🔁 Lặp: " + (music.loop() ? "BẬT" : "TẮT"), loop)
            .button("🔀 Trộn: " + (music.shuffle() ? "BẬT" : "TẮT"), shuffle)
            .button("📻 Radio: " + (music.radio() ? "BẬT" : "TẮT"), radio)
            .button("🔊 Âm lượng", volume).build();
        fp.sendForm(form);
    }
    private void openInput(Player player) {
        if (!player.hasPermission("youtubemusic.play")) { player.sendMessage("§cBạn không có quyền phát nhạc YouTube."); return; }
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(player.getUniqueId()); if (fp == null) return;
        CustomForm.Builder form = CustomForm.builder().title("🔗 Phát YouTube").label("Dán URL YouTube vào ô bên dưới:").input("URL YouTube", "https://youtu.be/...", "")
            .validResultHandler((CustomFormResponse response) -> { String url = response.asInput(0); if (url != null && !url.isBlank()) music.request(player, url.trim()); open(player); });
        fp.sendForm(form);
    }
    private void openVolume(Player player) {
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(player.getUniqueId()); if (fp == null) return;
        CustomForm.Builder form = CustomForm.builder().title("🔊 Âm lượng").slider("Âm lượng", 0, 100, 1, music.volume(player))
            .validResultHandler((CustomFormResponse response) -> { music.setVolume(player, Math.round(response.asSlider(0))); open(player); });
        fp.sendForm(form);
    }
    private void openQueue(Player player) {
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(player.getUniqueId()); if (fp == null) return;
        StringBuilder body = new StringBuilder(); int n = 1; for (var t : music.queue()) body.append(n++).append(". ").append(t.title()).append('\n');
        SimpleForm.Builder form = SimpleForm.builder().title("📋 Hàng đợi").content(body.isEmpty() ? "Hàng đợi đang trống." : body.toString()).button("⬅ Quay lại", (Consumer<SimpleFormResponse>) r -> open(player));
        fp.sendForm(form);
    }
}
