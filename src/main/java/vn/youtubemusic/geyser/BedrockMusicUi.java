package vn.youtubemusic.geyser;

import org.bukkit.entity.Player;
import vn.youtubemusic.music.MusicManager;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/** Bedrock-only UI using the Cumulus 1.1.2-compatible Forms API. */
public final class BedrockMusicUi implements BedrockUiBridge {
    private final MusicManager music;
    public BedrockMusicUi(MusicManager music) { this.music = music; }
    public boolean isBedrock(Player player) { try { return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId()); } catch (Throwable ignored) { return false; } }
    public void open(Player player) {
        if (!player.hasPermission("youtubemusic.use") || !isBedrock(player)) return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(player.getUniqueId()); if (fp == null) return;
        SimpleForm.Builder builder = SimpleForm.builder().title("🎵 YouTube Music")
            .content("§bTrung tâm âm nhạc\n§7Bài hiện tại: " + (music.current() == null ? "Chưa có" : music.current().title()))
            .button("🔗 Dán link YouTube")
            .button("⏯ Tạm dừng / Tiếp tục")
            .button("⏭ Bỏ qua")
            .button("⏹ Dừng")
            .button("📋 Hàng đợi")
            .button("🔁 Lặp: " + (music.loop() ? "BẬT" : "TẮT"))
            .button("🔀 Trộn: " + (music.shuffle() ? "BẬT" : "TẮT"))
            .button("📻 Radio: " + (music.radio() ? "BẬT" : "TẮT"))
            .button("🔊 Âm lượng");
        builder.validResultHandler((SimpleFormResponse response) -> {
            switch (response.clickedButtonId()) {
                case 0 -> openInput(player);
                case 1 -> { if (music.paused()) music.resume(); else music.pause(); open(player); }
                case 2 -> { music.skip(); open(player); }
                case 3 -> { music.stop(); open(player); }
                case 4 -> openQueue(player);
                case 5 -> { music.toggleLoop(); open(player); }
                case 6 -> { music.toggleShuffle(); open(player); }
                case 7 -> { music.toggleRadio(); open(player); }
                case 8 -> openVolume(player);
                default -> { }
            }
        });
        fp.sendForm(builder);
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
        SimpleForm.Builder form = SimpleForm.builder().title("📋 Hàng đợi").content(body.isEmpty() ? "Hàng đợi đang trống." : body.toString()).button("⬅ Quay lại");
        form.validResultHandler((SimpleFormResponse response) -> open(player));
        fp.sendForm(form);
    }
}
