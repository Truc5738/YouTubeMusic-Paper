package vn.youtubemusic.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.music.MusicManager;
import vn.youtubemusic.music.Track;
import vn.youtubemusic.storage.Storage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicGui implements Listener {
    private final JavaPlugin plugin; private final MusicManager music; private final Storage storage;
    private final Set<UUID> urlInput=ConcurrentHashMap.newKeySet(), searchInput=ConcurrentHashMap.newKeySet();
    private static final Component HOME=Component.text("§b🎵 YouTube Music");

    public MusicGui(JavaPlugin p,MusicManager m,Storage s){plugin=p;music=m;storage=s;}
    private ItemStack item(Material mat,String name,String... lore){ItemStack x=new ItemStack(mat);ItemMeta m=x.getItemMeta();m.displayName(Component.text(name));m.lore(Arrays.stream(lore).map(Component::text).toList());x.setItemMeta(m);return x;}

    public void open(Player p){
        if(!p.hasPermission("youtubemusic.use"))return;
        Inventory i=Bukkit.createInventory(null,54,HOME); Track t=music.current();
        i.setItem(4,item(Material.JUKEBOX,"§b🎵 ĐANG PHÁT",t==null?"§7Chưa có bài":"§f"+t.title(),t==null?"§7Mở Tìm nhạc để bắt đầu":(music.paused()?"§e⏸ Tạm dừng":"§a▶ Đang phát")));
        i.setItem(10,item(Material.COMPASS,"§a🔎 Tìm nhạc","§7Tìm theo tên bài / nghệ sĩ"));
        i.setItem(11,item(Material.NOTE_BLOCK,"§a▶ Phát URL","§7Dán link YouTube"));
        i.setItem(12,item(Material.HOPPER,"§e📋 Hàng đợi","§7"+music.queue().size()+" bài đang chờ"));
        i.setItem(13,item(Material.NETHER_STAR,"§c⏯ "+(music.paused()?"Tiếp tục":"Tạm dừng")));
        i.setItem(14,item(Material.ARROW,"§e⏭ Bỏ qua"));
        i.setItem(15,item(Material.BARRIER,"§c⏹ Dừng"));
        i.setItem(16,item(Material.REPEATER,"§d🔁 Lặp: "+(music.loop()?"BẬT":"TẮT")));
        i.setItem(19,item(Material.CHEST,"§6♥ Yêu thích","§7Thư viện cá nhân"));
        i.setItem(20,item(Material.CLOCK,"§b🕘 Lịch sử","§7Bài đã nghe gần đây"));
        i.setItem(21,item(Material.REDSTONE_TORCH,"§5📻 Radio: "+(music.radio()?"BẬT":"TẮT")));
        i.setItem(22,item(Material.SLIME_BALL,"§d🔀 Trộn: "+(music.shuffle()?"BẬT":"TẮT")));
        i.setItem(23,item(Material.NOTE_BLOCK,"§f🔊 Âm lượng: "+music.volume(p)+"%"));
        i.setItem(24,item(Material.GOLD_INGOT,"§6♥ "+(t!=null&&storage.isFavorite(p.getUniqueId(),t.url())?"Bỏ lưu":"Lưu bài hiện tại")));
        i.setItem(25,item(Material.BOOK,"§fℹ Trạng thái","§7UI Java + Bedrock","§7API audio + resource pack"));
        i.setItem(32,item(Material.WRITABLE_BOOK,"§d🎤 Lời bài hát riêng","§7"+(music.lyrics().isEnabled(p)?"Đang BẬT":"Đang TẮT"),"§7Chỉ bạn thấy lyrics","§7Dùng /music lyrics để đổi"));
        i.setItem(31,item(Material.WRITABLE_BOOK,"§f⚙ Hướng dẫn","§7/musics để mở UI (lệnh /music vẫn dùng được)"));
        i.setItem(40,item(Material.BARRIER,"§cĐóng"));
        p.openInventory(i);
    }

    private void ask(Player p,boolean search){if(!p.hasPermission("youtubemusic.play"))return;(search?searchInput:urlInput).add(p.getUniqueId());p.closeInventory();p.sendMessage(search?"§b🔎 Tìm YouTube §7→ §fNhập từ khóa vào chat.":"§b🎵 YouTube §7→ §fDán URL vào chat.");p.sendMessage("§7Gõ §ccancel §7để hủy.");}
    @EventHandler public void chat(AsyncChatEvent e){
        Player p=e.getPlayer();boolean s=searchInput.remove(p.getUniqueId()),u=urlInput.remove(p.getUniqueId());if(!s&&!u)return;e.setCancelled(true);
        String text=PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin,()->{if(text.equalsIgnoreCase("cancel")){p.sendMessage("§eĐã hủy.");open(p);return;}
            if(s){p.sendMessage("§e🔎 Đang tìm: §f"+text);music.search(p,text,r->searchResults(p,r,text));}else{music.request(p,text);open(p);}});
    }
    private void searchResults(Player p,List<Track> r,String q){
        Inventory i=Bukkit.createInventory(null,54,Component.text("§b🔎 Kết quả: "+q));i.setItem(49,item(Material.ARROW,"§7⬅ Quay lại"));
        for(int n=0;n<Math.min(45,r.size());n++)i.setItem(n,item(Material.MUSIC_DISC_CAT,"§f"+(n+1)+". §b"+r.get(n).title(),"§aNhấn để phát ngay","§8"+r.get(n).url()));
        p.openInventory(i);
    }
    private void library(Player p,boolean history){
        List<Track> list=history?storage.history(p.getUniqueId(),45):storage.favorites(p.getUniqueId(),45);
        Inventory i=Bukkit.createInventory(null,54,Component.text(history?"§b🕘 Lịch sử":"§6♥ Yêu thích"));i.setItem(49,item(Material.ARROW,"§7⬅ Quay lại"));
        for(int n=0;n<list.size();n++)i.setItem(n,item(Material.MUSIC_DISC_13,"§f"+(n+1)+". §e"+list.get(n).title(),"§aNhấn để phát","§8"+list.get(n).url()));
        p.openInventory(i);
    }
    private void queue(Player p){
        List<Track> list=new ArrayList<>(music.queue());Inventory i=Bukkit.createInventory(null,54,Component.text("§e📋 Hàng đợi"));i.setItem(49,item(Material.ARROW,"§7⬅ Quay lại"));i.setItem(53,item(Material.TNT,"§c🧹 Xóa hàng đợi"));
        for(int n=0;n<Math.min(45,list.size());n++)i.setItem(n,item(Material.HOPPER,"§f"+(n+1)+". §b"+list.get(n).title(),"§cNhấn để xóa khỏi hàng đợi"));
        p.openInventory(i);
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||e.getClickedInventory()==null)return;
        String title=PlainTextComponentSerializer.plainText().serialize(e.getView().title());e.setCancelled(true);int slot=e.getRawSlot();
        if(slot<0||slot>=e.getView().getTopInventory().getSize())return;
        if(title.equals("§b🎵 YouTube Music")){
            switch(slot){
                case 10->ask(p,true);case 11->ask(p,false);case 12->queue(p);
                case 13->{if(music.paused())music.resume();else music.pause();open(p);}case 14->{music.skip();open(p);}case 15->{music.stop();open(p);}
                case 16->{music.toggleLoop();open(p);}case 19->library(p,false);case 20->library(p,true);
                case 21->{music.toggleRadio();open(p);}case 22->{music.toggleShuffle();open(p);}
                case 23->p.sendMessage("§bDùng /music volume <0-100> để chỉnh âm lượng.");
                case 24->{if(music.current()!=null)p.sendMessage(music.toggleFavorite(p)?"§a♥ Đã lưu bài.":"§e♥ Đã bỏ lưu bài.");open(p);}
                case 31->p.sendMessage("§bTìm kiếm cần YouTube Data API key trong config.yml. §7URL vẫn hoạt động không cần key.");
                case 32->{music.lyrics().toggle(p);open(p);}
                case 40->p.closeInventory();
            }return;
        }
        if(title.startsWith("§b🔎 Kết quả: ")){
            if(slot==49){open(p);return;} if(slot<45&&e.getCurrentItem()!=null){
                String raw=PlainTextComponentSerializer.plainText().serialize(e.getCurrentItem().getItemMeta().displayName());int dot=raw.indexOf(".");
                if(dot>0)try{int idx=Integer.parseInt(raw.substring(0,dot))-1;String q=title.substring("§b🔎 Kết quả: ".length());music.search(p,q,r->{if(idx>=0&&idx<r.size())music.playSearchResult(p,r.get(idx));});}catch(Exception ignored){}
                open(p);
            }return;
        }
        if(title.equals("§6♥ Yêu thích")||title.equals("§b🕘 Lịch sử")){
            if(slot==49){open(p);return;}List<Track> list=title.equals("§b🕘 Lịch sử")?storage.history(p.getUniqueId(),45):storage.favorites(p.getUniqueId(),45);
            if(slot>=0&&slot<list.size()){music.playSaved(p,list.get(slot));open(p);}return;
        }
        if(title.equals("§e📋 Hàng đợi")){
            if(slot==49){open(p);return;}if(slot==53){music.clearQueue();queue(p);return;}if(slot<45){music.remove(slot+1);queue(p);}
        }
    }
}