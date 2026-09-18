package vn.youtubemusic.geyser;

import org.bukkit.entity.Player;
import vn.youtubemusic.music.MusicManager;
import vn.youtubemusic.music.Track;
import vn.youtubemusic.storage.Storage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import java.util.*;

public final class BedrockMusicUi implements BedrockUiBridge {
    private final MusicManager music; private final Storage storage;
    public BedrockMusicUi(MusicManager m, Storage s){music=m;storage=s;}
    public boolean isBedrock(Player p){try{return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());}catch(Throwable e){return false;}}
    private FloodgatePlayer fp(Player p){return FloodgateApi.getInstance().getPlayer(p.getUniqueId());}

    public void open(Player p){
        if(!p.hasPermission("youtubemusic.use")||!isBedrock(p))return; FloodgatePlayer f=fp(p); if(f==null)return;
        Track t=music.current();
        SimpleForm.Builder b=SimpleForm.builder().title("🎵 YouTube Music")
          .content("§bTrang chủ • Spotify-style\n§f"+(t==null?"Chưa có bài":t.title())+"\n§7"+(music.paused()?"⏸ Đang tạm dừng":"▶ Đang phát")+" • Hàng đợi: "+music.queue().size())
          .button("🔎 Tìm nhạc").button("🔗 Dán link YouTube").button("⏯ "+(music.paused()?"Tiếp tục":"Tạm dừng"))
          .button("⏭ Bỏ qua").button("⏹ Dừng").button("📋 Hàng đợi").button("♥ Yêu thích").button("🕘 Lịch sử")
          .button("🔁 Lặp: "+(music.loop()?"BẬT":"TẮT")).button("🔀 Trộn: "+(music.shuffle()?"BẬT":"TẮT"))
          .button("📻 Radio: "+(music.radio()?"BẬT":"TẮT")).button("🔊 Âm lượng: "+music.volume(p)+"%")
          .button("♥ "+(t!=null&&storage.isFavorite(p.getUniqueId(),t.url())?"Bỏ lưu":"Lưu bài"));
        b.validResultHandler((SimpleFormResponse r)->{switch(r.clickedButtonId()){
            case 0->search(p); case 1->input(p); case 2->{if(music.paused())music.resume();else music.pause();open(p);}
            case 3->{music.skip();open(p);} case 4->{music.stop();open(p);} case 5->queue(p); case 6->library(p,false); case 7->library(p,true);
            case 8->{music.toggleLoop();open(p);} case 9->{music.toggleShuffle();open(p);} case 10->{music.toggleRadio();open(p);}
            case 11->volume(p); case 12->{if(music.current()!=null)p.sendMessage(music.toggleFavorite(p)?"§a♥ Đã lưu bài.":"§e♥ Đã bỏ lưu bài.");open(p);}
        }}); f.sendForm(b);
    }
    private void input(Player p){if(!p.hasPermission("youtubemusic.play"))return;FloodgatePlayer f=fp(p);if(f==null)return;
        CustomForm.Builder x=CustomForm.builder().title("🔗 Phát YouTube").label("Dán URL YouTube:").input("URL","https://youtu.be/...","");
        x.validResultHandler((CustomFormResponse r)->{String u=r.asInput(0);if(u!=null&&!u.isBlank())music.request(p,u.trim());open(p);});f.sendForm(x);
    }
    private void search(Player p){if(!p.hasPermission("youtubemusic.play"))return;FloodgatePlayer f=fp(p);if(f==null)return;
        CustomForm.Builder x=CustomForm.builder().title("🔎 Tìm nhạc").label("Tên bài hát / nghệ sĩ:").input("Từ khóa","lofi chill","");
        x.validResultHandler((CustomFormResponse r)->{String q=r.asInput(0);if(q==null||q.isBlank()){open(p);return;}p.sendMessage("§e🔎 Đang tìm: §f"+q);music.search(p,q,res->searchResults(p,res,q));});f.sendForm(x);
    }
    private void searchResults(Player p,List<Track> r,String q){FloodgatePlayer f=fp(p);if(f==null)return;SimpleForm.Builder x=SimpleForm.builder().title("🔎 Kết quả YouTube").content(r.isEmpty()?"Không có kết quả.":"§bKết quả cho: §f"+q);
        for(Track t:r)x.button("▶ "+t.title());x.button("⬅ Quay lại");
        x.validResultHandler((SimpleFormResponse a)->{int n=a.clickedButtonId();if(n>=0&&n<r.size()){music.playSearchResult(p,r.get(n));open(p);}else open(p);});f.sendForm(x);
    }
    private void library(Player p,boolean history){FloodgatePlayer f=fp(p);if(f==null)return;List<Track> list=history?storage.history(p.getUniqueId(),20):storage.favorites(p.getUniqueId(),20);
        SimpleForm.Builder x=SimpleForm.builder().title(history?"🕘 Lịch sử":"♥ Yêu thích").content(list.isEmpty()?"Chưa có bài.":"Chọn bài để phát.");
        for(Track t:list)x.button("▶ "+t.title());x.button("⬅ Quay lại");
        x.validResultHandler((SimpleFormResponse a)->{int n=a.clickedButtonId();if(n>=0&&n<list.size()){music.playSaved(p,list.get(n));open(p);}else open(p);});f.sendForm(x);
    }
    private void queue(Player p){FloodgatePlayer f=fp(p);if(f==null)return;List<Track> list=new ArrayList<>(music.queue());
        SimpleForm.Builder x=SimpleForm.builder().title("📋 Hàng đợi").content(list.isEmpty()?"Hàng đợi trống.":"Chạm bài để xóa.");
        for(int n=0;n<list.size()&&n<25;n++)x.button("🗑 "+(n+1)+". "+list.get(n).title());x.button("🧹 Xóa hàng đợi").button("⬅ Quay lại");
        x.validResultHandler((SimpleFormResponse a)->{int n=a.clickedButtonId();if(n>=0&&n<Math.min(list.size(),25)){music.remove(n+1);queue(p);}else if(n==Math.min(list.size(),25)){music.clearQueue();queue(p);}else open(p);});f.sendForm(x);
    }
    private void volume(Player p){FloodgatePlayer f=fp(p);if(f==null)return;CustomForm.Builder x=CustomForm.builder().title("🔊 Âm lượng").slider("Âm lượng",0,100,1,music.volume(p));
        x.validResultHandler((CustomFormResponse r)->{music.setVolume(p,Math.round(r.asSlider(0)));open(p);});f.sendForm(x);
    }
}
