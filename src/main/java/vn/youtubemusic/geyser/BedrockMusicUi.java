package vn.youtubemusic.geyser;

import org.bukkit.entity.Player;
import vn.youtubemusic.music.MusicManager;
import org.geysermc.cumulus.component.ButtonComponent;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/** Bedrock-only UI using the official Floodgate/Cumulus Forms API. */
public final class BedrockMusicUi implements BedrockUiBridge {
    private final MusicManager music;
    public BedrockMusicUi(MusicManager music) { this.music = music; }
    public boolean isBedrock(Player player) { try { return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId()); } catch (Throwable ignored) { return false; } }
    public void open(Player player) {
        if (!player.hasPermission("youtubemusic.use") || !isBedrock(player)) return;
        FloodgatePlayer fp = FloodgateApi.getInstance().getPlayer(player.getUniqueId()); if (fp == null) return;
        SimpleForm form = SimpleForm.builder().title("🎵 YouTube Music")
            .content("§bTrung tâm âm nhạc\n§7Bài hiện tại: " + (music.current() == null ? "Chưa có" : music.current().title()))
            .button(ButtonComponent.of("🔗 Dán link YouTube"), r -> openInput(player))
            .button(ButtonComponent.of("⏯ Tạm dừng / Tiếp tục"), r -> { if (music.paused()) music.resume(); else music.pause(); open(player); })
            .button(ButtonComponent.of("⏭ Bỏ qua"), r -> { music.skip(); open(player); })
            .button(ButtonComponent.of("⏹ Dừng"), r -> { music.stop(); open(player); })
            .button(ButtonComponent.of("📋 Hàng đợi"), r -> openQueue(player))
            .button(ButtonComponent.of("🔁 Lặp: " + (music.loop() ? "BẬT" : "TẮT")), r -> { music.toggleLoop(); open(player); })
            .button(ButtonComponent.of("🔀 Trộn: " + (music.shuffle() ? "BẬT" : "TẮT")), r -> { music.toggleShuffle(); open(player); })
            .button(ButtonComponent.of("📻 Radio: " + (music.radio() ? "BẬT" : "TẮT")), r -> { music.toggleRadio(); open(player); })
            .button(ButtonComponent.of("🔊 Âm lượng"), r -> openVolume(player)).build();
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
        SimpleForm.Builder form = SimpleForm.builder().title("📋 Hàng đợi").content(body.isEmpty() ? "Hàng đợi đang trống." : body.toString()).button(ButtonComponent.of("⬅ Quay lại"), r -> open(player));
        fp.sendForm(form);
    }
}
