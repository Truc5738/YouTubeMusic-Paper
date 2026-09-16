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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicGui implements Listener {
    private final JavaPlugin plugin; private final MusicManager music; private final Set<UUID> waitingForUrl=ConcurrentHashMap.newKeySet(); private static final Component TITLE=Component.text("§b🎵 YouTube Music");
    public MusicGui(JavaPlugin plugin,MusicManager music){this.plugin=plugin;this.music=music;}
    private ItemStack item(Material material,String name,String... lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.displayName(Component.text(name));meta.lore(Arrays.stream(lore).map(Component::text).toList());stack.setItemMeta(meta);return stack;}
    public void open(Player p){if(!p.hasPermission("youtubemusic.use")){p.sendMessage("§cBạn không có quyền dùng YouTube Music.");return;}Inventory inv=Bukkit.createInventory(null,27,TITLE);inv.setItem(10,item(Material.NOTE_BLOCK,"§a▶ Phát YouTube","§7Mở ô nhập link bằng chat."));inv.setItem(11,item(Material.HOPPER,"§e📋 Hàng đợi","§7Có "+music.queue().size()+" bài."));inv.setItem(12,item(Material.JUKEBOX,"§b🎵 Đang phát","§7"+(music.current()==null?"Chưa có bài":music.current().title())));inv.setItem(13,item(Material.SLIME_BALL,"§a⏯ Tạm dừng / Tiếp tục"));inv.setItem(14,item(Material.ARROW,"§e⏭ Bỏ qua"));inv.setItem(15,item(Material.BARRIER,"§c■ Dừng"));inv.setItem(16,item(Material.REPEATER,"§d🔁 Lặp: "+(music.loop()?"BẬT":"TẮT")));inv.setItem(21,item(Material.COMPASS,"§d🔀 Trộn: "+(music.shuffle()?"BẬT":"TẮT")));inv.setItem(22,item(Material.REDSTONE_TORCH,"§5📻 Radio: "+(music.radio()?"BẬT":"TẮT")));inv.setItem(23,item(Material.NOTE_BLOCK,"§f🔊 Âm lượng: "+music.volume(p)+"%"));inv.setItem(26,item(Material.BOOK,"§f⚙ Trợ giúp","§7/music help"));p.openInventory(inv);}
    private void askForUrl(Player p){if(!p.hasPermission("youtubemusic.play")){p.sendMessage("§cBạn không có quyền phát nhạc YouTube.");return;}waitingForUrl.add(p.getUniqueId());p.closeInventory();p.sendMessage("§b🎵 YouTube Music §7→ §fHãy dán URL YouTube vào chat.");p.sendMessage("§7Gõ §ccancel §7để hủy.");}
    @EventHandler public void chat(AsyncChatEvent event){Player p=event.getPlayer();if(!waitingForUrl.remove(p.getUniqueId()))return;event.setCancelled(true);String text=PlainTextComponentSerializer.plainText().serialize(event.message()).trim();Bukkit.getScheduler().runTask(plugin,()->{if(text.equalsIgnoreCase("cancel")){p.sendMessage("§eĐã hủy.");return;}music.request(p,text);open(p);});}
    @EventHandler public void click(InventoryClickEvent e){if(!e.getView().title().equals(TITLE))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;if(!p.hasPermission("youtubemusic.use")){p.closeInventory();p.sendMessage("§cBạn không có quyền dùng YouTube Music.");return;}switch(e.getRawSlot()){case 10->askForUrl(p);case 11,12,13,14,15,16,21,22,23,26->{switch(e.getRawSlot()){case 11,12->{ }case 13->{if(music.paused())music.resume();else music.pause();}case 14->music.skip();case 15->music.stop();case 16->music.toggleLoop();case 21->music.toggleShuffle();case 22->music.toggleRadio();case 23->p.sendMessage("§bDùng /music volume <0-100> để chỉnh âm lượng.");case 26->p.sendMessage("§b/music §7mở giao diện | §b/music play <URL> §7phát nhạc");}if(p.isOnline())open(p);}}}
}
