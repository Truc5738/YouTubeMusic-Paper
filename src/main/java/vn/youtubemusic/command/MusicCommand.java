package vn.youtubemusic.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import vn.youtubemusic.geyser.BedrockUiBridge;
import vn.youtubemusic.gui.MusicGui;
import vn.youtubemusic.music.MusicManager;
import vn.youtubemusic.music.Track;
import vn.youtubemusic.storage.Storage;
import java.util.*;

public final class MusicCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final MusicManager music;
    private final MusicGui gui;
    private final BedrockUiBridge bedrock;
    private final Storage storage;

    public MusicCommand(JavaPlugin p, MusicManager m, MusicGui g, BedrockUiBridge b, Storage storage) {
        plugin = p; music = m; gui = g; bedrock = b; this.storage = storage;
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("youtubemusic.use")) {
            s.sendMessage("§cBạn không có quyền dùng YouTube Music.");
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("ui") || a[0].equalsIgnoreCase("player")) {
            if (s instanceof Player p) {
                if (bedrock.isBedrock(p)) bedrock.open(p); else gui.open(p);
            } else s.sendMessage("§b/music play <URL> §7| §b/music status");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "play", "add" -> {
                if (!s.hasPermission("youtubemusic.play")) { s.sendMessage("§cBạn không có quyền phát nhạc YouTube."); return true; }
                if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return true; }
                if (a.length < 2) { s.sendMessage("§eDùng: /music play <URL YouTube>"); return true; }
                music.request(p, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "stop" -> { if (!control(s)) return true; music.stop(); s.sendMessage("§c■ Đã dừng nhạc và xóa hàng đợi."); }
            case "pause" -> { if (!control(s)) return true; music.pause(); s.sendMessage("§eⅡ Đã tạm dừng."); }
            case "resume" -> { if (!control(s)) return true; music.resume(); s.sendMessage("§a▶ Đã tiếp tục."); }
            case "skip" -> { if (!control(s)) return true; music.skip(); s.sendMessage("§e⏭ Đã bỏ qua."); }
            case "clear" -> { if (!control(s)) return true; music.clearQueue(); s.sendMessage("§aĐã xóa hàng đợi."); }
            case "remove" -> {
                if (!control(s)) return true;
                if (a.length < 2) { s.sendMessage("§eDùng: /music remove <số>"); return true; }
                try {
                    int before = music.queue().size(); music.remove(Integer.parseInt(a[1]));
                    s.sendMessage(music.queue().size() < before ? "§aĐã xóa mục khỏi hàng đợi." : "§cKhông tìm thấy mục đó trong hàng đợi.");
                } catch (NumberFormatException ex) { s.sendMessage("§cSố không hợp lệ."); }
            }
            case "queue" -> {
                Deque<Track> q = music.queue();
                if (q.isEmpty()) s.sendMessage("§eHàng đợi đang trống.");
                else { int n = 1; for (Track t : q) s.sendMessage("§7" + n++ + ". §f" + t.title()); }
            }
            case "volume" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length < 2) { s.sendMessage("§bÂm lượng: §f" + music.volume(p) + "%"); return true; }
                try { music.setVolume(p, Integer.parseInt(a[1])); s.sendMessage("§aÂm lượng: " + music.volume(p) + "%"); }
                catch (NumberFormatException ex) { s.sendMessage("§cSố không hợp lệ."); }
            }
            case "loop" -> { if (!control(s)) return true; music.toggleLoop(); s.sendMessage("§d🔁 Lặp: " + (music.loop() ? "BẬT" : "TẮT")); }
            case "shuffle" -> { if (!control(s)) return true; music.toggleShuffle(); s.sendMessage("§d🔀 Trộn: " + (music.shuffle() ? "BẬT" : "TẮT")); }
            case "radio" -> { if (!control(s)) return true; music.toggleRadio(); s.sendMessage("§5📻 Radio: " + (music.radio() ? "BẬT" : "TẮT")); }
            case "favorite", "fav" -> favorite(s, a);
            case "search", "find" -> search(s, a);
            case "playfavorite", "pfav" -> playSavedByIndex(s, a, false);
            case "playhistory", "phistory" -> playSavedByIndex(s, a, true);
            case "clearhistory", "delhistory" -> clearHistory(s);

            case "lyrics", "lyric" -> lyrics(s, a);
            case "say" -> sayLyrics(s, a);
            case "favorites", "favs" -> listSaved(s, false);
            case "history" -> listSaved(s, true);
            case "status", "now", "nowplaying", "np" -> {
                Track t = music.current();
                s.sendMessage("§b🎵 Đang phát: §f" + (t == null ? "Không có" : t.title()));
                s.sendMessage("§7Queue: " + music.queue().size() + " | Lặp: " + music.loop() + " | Trộn: " + music.shuffle() + " | Radio: " + music.radio());
            }
            case "reload" -> { if (!admin(s)) return true; plugin.reloadConfig(); music.reloadSettings(); s.sendMessage("§a✔ Đã tải lại cấu hình và đồng bộ hàng đợi."); }
            case "debug" -> { if (!admin(s)) return true; s.sendMessage("§bYouTubeMusic §7| Paper 26.2 | Java 25 | UI: Java + Bedrock | Backend: API + resource pack"); }
            case "doctor", "diagnostics" -> { if (!admin(s)) return true; doctor(s); }
            case "help" -> help(s);
            default -> help(s);
        }
        return true;
    }

    private boolean control(CommandSender s) {
        if (s.hasPermission("youtubemusic.control")) return true;
        s.sendMessage("§cBạn không có quyền điều khiển trình phát.");
        return false;
    }
    private boolean admin(CommandSender s) {
        if (s.hasPermission("youtubemusic.admin")) return true;
        s.sendMessage("§cBạn không có quyền.");
        return false;
    }

    private void lyrics(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return; }
        if (a.length >= 2) {
            boolean on = !a[1].equalsIgnoreCase("off") && !a[1].equalsIgnoreCase("tat") && !a[1].equalsIgnoreCase("tắt");
            music.lyrics().setEnabled(p, on);
        } else music.lyrics().toggle(p);
    }

    private void sayLyrics(CommandSender s, String[] a) {
        if (!s.hasPermission("youtubemusic.control")) { s.sendMessage("§cBạn không có quyền bật lời cho người khác."); return; }
        if (a.length < 2) { s.sendMessage("§eDùng: /music say <tên người chơi>"); return; }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) { s.sendMessage("§cKhông tìm thấy người chơi đang online: §f" + a[1]); return; }
        boolean on = music.lyrics().toggle(target);
        s.sendMessage("§a🎤 Lời riêng của §f" + target.getName() + "§a: " + (on ? "BẬT" : "TẮT"));
    }

    private void favorite(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return; }
        Track t = music.current();
        if (a.length >= 2 && a[1].equalsIgnoreCase("remove")) {
            if (t == null || !storage.unfavorite(p.getUniqueId(), t.url())) { s.sendMessage("§eBài hiện tại không nằm trong yêu thích."); }
            else s.sendMessage("§a♥ Đã bỏ khỏi yêu thích.");
            return;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("remove-index")) {
            if (a.length < 3) { s.sendMessage("§eDùng: /music favorite remove-index <số>"); return; }
            try {
                s.sendMessage(storage.removeFavoriteByIndex(p.getUniqueId(), Integer.parseInt(a[2]))
                    ? "§a♥ Đã xóa bài khỏi yêu thích." : "§cKhông tìm thấy mục yêu thích đó.");
            } catch (NumberFormatException ex) { s.sendMessage("§cSố không hợp lệ."); }
            return;
        }
        if (t == null) { s.sendMessage("§eChưa có bài đang phát để lưu."); return; }
        if (storage.isFavorite(p.getUniqueId(), t.url())) { s.sendMessage("§eBài này đã có trong yêu thích."); return; }
        storage.favorite(p.getUniqueId(), t);
        s.sendMessage("§a♥ Đã lưu bài hiện tại vào yêu thích.");
    }

    private void search(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return; }
        if (a.length < 2) { s.sendMessage("§eDùng: /music search <từ khóa>"); return; }
        String q = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        s.sendMessage("§e🔎 Đang tìm YouTube: §f" + q);
        music.search(p, q, results -> {
            if (results.isEmpty()) { s.sendMessage("§eKhông có kết quả. Hãy kiểm tra YouTube Data API key trong config.yml."); return; }
            s.sendMessage("§b§lKết quả tìm kiếm:");
            for (int i=0; i<results.size(); i++) s.sendMessage("§7"+(i+1)+". §f"+results.get(i).title());
            s.sendMessage("§7Kết quả tìm kiếm có thể phát trực tiếp từ UI; thư viện dùng §f/music playfavorite <số>§7.");
        });
    }

    private void playSavedByIndex(CommandSender s, String[] a, boolean history) {
        if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return; }
        if (a.length < 2) { s.sendMessage("§eDùng: /music "+(history?"playhistory":"playfavorite")+" <số>"); return; }
        try {
            int index=Integer.parseInt(a[1]);
            List<Track> list=history?storage.history(p.getUniqueId(),100):storage.favorites(p.getUniqueId(),100);
            if(index<1||index>list.size()){s.sendMessage("§cKhông có mục số "+index+".");return;}
            music.playSaved(p,list.get(index-1));
        } catch(NumberFormatException ex){s.sendMessage("§cSố không hợp lệ.");}
    }

    private void clearHistory(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return; }
        s.sendMessage(storage.clearHistory(p.getUniqueId()) ? "§a🧹 Đã xóa lịch sử nghe của bạn." : "§cKhông thể xóa lịch sử.");
    }

    private void listSaved(CommandSender s, boolean history) {
        if (!(s instanceof Player p)) { s.sendMessage("§cLệnh này cần một người chơi."); return; }
        List<Track> list = history ? storage.history(p.getUniqueId(), 15) : storage.favorites(p.getUniqueId(), 15);
        if (list.isEmpty()) { s.sendMessage(history ? "§eLịch sử đang trống." : "§eDanh sách yêu thích đang trống."); return; }
        s.sendMessage(history ? "§b§lLịch sử nghe gần đây:" : "§b§lBài hát yêu thích:");
        int n = 1;
        for (Track t : list) s.sendMessage("§7" + n++ + ". §f" + t.title() + " §8→ §7" + t.url());
    }

    private void doctor(CommandSender s) {
        s.sendMessage("§b§lYouTubeMusic Doctor");
        String api = plugin.getConfig().getString("audio.api.url", "");
        s.sendMessage(api.isBlank() ? "§c✖ Audio API URL chưa cấu hình." : "§a✔ Audio API: §f" + api);
        String apiKey = plugin.getConfig().getString("audio.api.key", "");
        s.sendMessage(apiKey.isBlank() ? "§e⚠ Audio API key: §7không dùng" : "§a✔ Audio API key: §7đã cấu hình");
        String ytKey = plugin.getConfig().getString("audio.youtube-data-api-key", "");
        s.sendMessage(ytKey.isBlank() ? "§e⚠ YouTube Data API: §7không dùng (thời lượng fallback)" : "§a✔ YouTube Data API: §7đã cấu hình");
        String url = plugin.getConfig().getString("audio.resource-pack-url-template", "");
        s.sendMessage(url.contains("YOUR_PUBLIC_IP") || url.isBlank() ? "§c✖ Resource-pack URL chưa cấu hình." : "§a✔ Resource-pack URL đã cấu hình.");
        boolean embedded = plugin.getConfig().getBoolean("audio.resource-pack-server.enabled", true);
        s.sendMessage("§7Embedded resource-pack server: " + (embedded ? "§aBẬT" : "§eTẮT") + " §7| Port: §f" + plugin.getConfig().getInt("audio.resource-pack-server-port", 8126));
        pluginStatus(s, "Floodgate", "floodgate", true);
        pluginStatus(s, "Geyser", "Geyser-Spigot", false);
        pluginStatus(s, "ViaVersion", "ViaVersion", false);
        pluginStatus(s, "ViaBackwards", "ViaBackwards", false);
        s.sendMessage("§7Queue: §f" + music.queue().size() + " §7| Current: §f" + (music.current() == null ? "không có" : music.current().title()));
    }

    private void pluginStatus(CommandSender s, String label, String pluginName, boolean required) {
        boolean ok = plugin.getServer().getPluginManager().getPlugin(pluginName) != null;
        s.sendMessage((ok ? "§a✔ " : (required ? "§c✖ " : "§e⚠ ")) + label + "§7: " + (ok ? "đã cài" : (required ? "thiếu — plugin cần thành phần này" : "không có (không bắt buộc)")));
    }

    private void check(CommandSender s, String name, String command) {
        try {
            Process p = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            boolean ok = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
            s.sendMessage((ok ? "§a✔ " : "§c✖ ") + name + "§7: " + (ok ? "đã sẵn sàng" : "không tìm thấy"));
            if (!ok) p.destroyForcibly();
        } catch (Exception e) { s.sendMessage("§c✖ " + name + "§7: không tìm thấy"); }
    }

    private void help(CommandSender s) {
        s.sendMessage("§b§lYouTube Music §7— lệnh chính");
        s.sendMessage("§f/music §7UI | §f/music play <URL> §7phát | §f/music queue §7hàng đợi | §f/music skip §7bỏ qua | §f/music stop §7dừng");
        s.sendMessage("§f/music volume <0-100> §7âm lượng | §f/music loop §7lặp | §f/music shuffle §7trộn | §f/music radio §7radio");
        s.sendMessage("§f/music favorite §7lưu | §f/music favorite remove §7bỏ lưu | §f/music favorite remove-index <số> §7xóa theo số");
        s.sendMessage("§f/music favorites §7yêu thích | §f/music history §7lịch sử | §f/music clearhistory §7xóa lịch sử");
        s.sendMessage("§f/music playfavorite <số> §7phát thư viện | §f/music playhistory <số> §7phát lịch sử | §f/music search <từ khóa> §7tìm YouTube");
        s.sendMessage("§f/music lyrics [on|off] §7lời riêng | §f/music say <người chơi> §7bật/tắt lời cho người chơi");
    }

    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        if (args.length == 1) return List.of("play", "ui", "stop", "pause", "resume", "skip", "queue", "clear", "remove", "volume", "loop", "shuffle", "radio", "favorite", "favorites", "history", "clearhistory", "playfavorite", "playhistory", "search", "lyrics", "say", "status", "reload", "debug", "doctor", "diagnostics", "help").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("favorite") || args[0].equalsIgnoreCase("fav"))) return List.of("remove","remove-index").stream().filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
