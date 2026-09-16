package vn.youtubemusic.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.geyser.BedrockUiBridge;
import vn.youtubemusic.gui.MusicGui;
import vn.youtubemusic.music.MusicManager;
import vn.youtubemusic.music.Track;
import java.util.*;

public final class MusicCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin; private final MusicManager music; private final MusicGui gui; private final BedrockUiBridge bedrock;
    public MusicCommand(JavaPlugin p, MusicManager m, MusicGui g, BedrockUiBridge b){plugin=p;music=m;gui=g;bedrock=b;}
    public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(!s.hasPermission("youtubemusic.use")){s.sendMessage("§cBạn không có quyền dùng YouTube Music.");return true;}
        if(a.length==0||a[0].equalsIgnoreCase("ui")||a[0].equalsIgnoreCase("player")){if(s instanceof Player p){if(bedrock.isBedrock(p))bedrock.open(p);else gui.open(p);}else s.sendMessage("§b/music play <URL> §7| §b/music status");return true;}
        switch(a[0].toLowerCase(Locale.ROOT)){
            case "play","add"->{if(!s.hasPermission("youtubemusic.play")){s.sendMessage("§cBạn không có quyền phát nhạc YouTube.");return true;}if(!(s instanceof Player p))return true;if(a.length<2){s.sendMessage("§eDùng: /music play <URL YouTube>");return true;}music.request(p,String.join(" ",Arrays.copyOfRange(a,1,a.length)));}
            case "stop"->{music.stop();s.sendMessage("§c■ Đã dừng nhạc và xóa hàng đợi.");}
            case "pause"->{music.pause();s.sendMessage("§eⅡ Đã tạm dừng.");}
            case "resume"->{music.resume();s.sendMessage("§a▶ Đã tiếp tục.");}
            case "skip"->{music.skip();s.sendMessage("§e⏭ Đã bỏ qua.");}
            case "clear"->{music.clearQueue();s.sendMessage("§aĐã xóa hàng đợi.");}
            case "remove"->{if(a.length<2){s.sendMessage("§eDùng: /music remove <số>");return true;}try{int before=music.queue().size();music.remove(Integer.parseInt(a[1]));s.sendMessage(music.queue().size()<before?"§aĐã xóa mục khỏi hàng đợi.":"§cKhông tìm thấy mục đó trong hàng đợi.");}catch(NumberFormatException ex){s.sendMessage("§cSố không hợp lệ.");}}
            case "queue"->{Deque<Track> q=music.queue();if(q.isEmpty())s.sendMessage("§eHàng đợi đang trống.");else{int n=1;for(Track t:q)s.sendMessage("§7"+n++ +". §f"+t.title());}}
            case "volume"->{if(!(s instanceof Player p))return true;if(a.length<2){s.sendMessage("§bÂm lượng: §f"+music.volume(p)+"%");return true;}try{music.setVolume(p,Integer.parseInt(a[1]));s.sendMessage("§aÂm lượng: "+music.volume(p)+"%");}catch(NumberFormatException ex){s.sendMessage("§cSố không hợp lệ.");}}
            case "loop"->{music.toggleLoop();s.sendMessage("§d🔁 Lặp: "+(music.loop()?"BẬT":"TẮT"));}
            case "shuffle"->{music.toggleShuffle();s.sendMessage("§d🔀 Trộn: "+(music.shuffle()?"BẬT":"TẮT"));}
            case "radio"->{music.toggleRadio();s.sendMessage("§5📻 Radio: "+(music.radio()?"BẬT":"TẮT"));}
            case "status","now"->{Track t=music.current();s.sendMessage("§b🎵 Đang phát: §f"+(t==null?"Không có":t.title()));s.sendMessage("§7Queue: "+music.queue().size()+" | Lặp: "+music.loop()+" | Trộn: "+music.shuffle()+" | Radio: "+music.radio());}
            case "reload"->{if(!s.hasPermission("youtubemusic.admin")){s.sendMessage("§cBạn không có quyền.");return true;}plugin.reloadConfig();music.reloadSettings();s.sendMessage("§a✔ Đã tải lại cấu hình và đồng bộ hàng đợi.");}
            case "debug"->{if(!s.hasPermission("youtubemusic.admin")){s.sendMessage("§cBạn không có quyền.");return true;}s.sendMessage("§bYouTubeMusic §7| Paper 26.2 | Java 25 | UI: Java + Bedrock | Backend: yt-dlp + resource pack");}
            case "doctor","diagnostics"->{if(!s.hasPermission("youtubemusic.admin")){s.sendMessage("§cBạn không có quyền.");return true;}doctor(s);}
            case "help"->help(s); default->help(s);
        }return true;
    }
    private void doctor(CommandSender s){s.sendMessage("§b§lYouTubeMusic Doctor");check(s,"yt-dlp",plugin.getConfig().getString("audio.yt-dlp-command","yt-dlp"));check(s,"ffmpeg",plugin.getConfig().getString("audio.ffmpeg-command","ffmpeg"));String url=plugin.getConfig().getString("audio.resource-pack-url-template","");s.sendMessage(url.contains("YOUR_PUBLIC_IP")||url.isBlank()?"§c✖ Resource-pack URL chưa cấu hình.":"§a✔ Resource-pack URL đã cấu hình.");s.sendMessage("§7Port resource pack: §f"+plugin.getConfig().getInt("audio.resource-pack-server-port",8126));pluginStatus(s,"Floodgate","floodgate",true);pluginStatus(s,"Geyser","Geyser-Spigot",false);pluginStatus(s,"ViaVersion","ViaVersion",false);pluginStatus(s,"ViaBackwards","ViaBackwards",false);s.sendMessage("§7Queue: §f"+music.queue().size()+" §7| Current: §f"+(music.current()==null?"không có":music.current().title()));}
    private void pluginStatus(CommandSender s,String label,String pluginName,boolean required){boolean ok=plugin.getServer().getPluginManager().getPlugin(pluginName)!=null;s.sendMessage((ok?"§a✔ ":(required?"§c✖ ":"§e⚠ "))+label+"§7: "+(ok?"đã cài":(required?"thiếu — plugin cần thành phần này":"không có (không bắt buộc)")));}
    private void check(CommandSender s,String name,String command){try{Process p=new ProcessBuilder(command,"--version").redirectErrorStream(true).start();boolean ok=p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)&&p.exitValue()==0;s.sendMessage((ok?"§a✔ ":"§c✖ ")+name+"§7: "+(ok?"đã sẵn sàng":"không tìm thấy"));}catch(Exception e){s.sendMessage("§c✖ "+name+"§7: không tìm thấy");}}
    private void help(CommandSender s){s.sendMessage("§b§lYouTube Music §7— lệnh chính");s.sendMessage("§f/music §7UI | §f/music play <URL> §7phát | §f/music queue §7hàng đợi | §f/music skip §7bỏ qua | §f/music stop §7dừng | §f/music volume <0-100> §7âm lượng | §f/music loop §7lặp | §f/music shuffle §7trộn | §f/music radio §7radio | §f/music status §7trạng thái");}
    public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] args){if(args.length==1)return List.of("play","ui","stop","pause","resume","skip","queue","clear","remove","volume","loop","shuffle","radio","status","reload","debug","doctor","diagnostics","help").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();return List.of();}
}
