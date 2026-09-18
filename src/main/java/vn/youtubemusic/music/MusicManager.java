package vn.youtubemusic.music;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.youtubemusic.storage.Storage;
import vn.youtubemusic.audio.AudioBackend;
import vn.youtubemusic.audio.ApiAudioBackend;
import vn.youtubemusic.audio.JavaAudioDelivery;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** Core queue/state engine. Audio delivery is deliberately separated from this class. */
public final class MusicManager {
    private final JavaPlugin plugin; private final Storage storage; private final Deque<Track> queue = new ArrayDeque<>(); private final Map<UUID,Integer> volumes = new ConcurrentHashMap<>(); private final Map<UUID,Long> cooldowns = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(2, r -> { Thread t = new Thread(r, "YouTubeMusic-worker"); t.setDaemon(true); return t; });
    private Track current; private boolean paused; private final LyricsManager lyrics; private boolean loop; private boolean shuffle; private boolean radio; private final AudioBackend audioBackend; private JavaAudioDelivery audioDelivery; private final Map<String,ScheduledFuture<?>> endTasks = new ConcurrentHashMap<>(); private final Map<String,java.nio.file.Path> audioFiles = new ConcurrentHashMap<>(); private long playbackGeneration;
    public MusicManager(JavaPlugin plugin, Storage storage) { this.plugin=plugin; this.storage=storage; loop=plugin.getConfig().getBoolean("queue.loop",false); shuffle=plugin.getConfig().getBoolean("queue.shuffle",false); radio=plugin.getConfig().getBoolean("queue.radio",false); audioBackend=new ApiAudioBackend(plugin); lyrics=new LyricsManager(plugin); }
    public void request(Player who,String url) { if(!validYoutube(url)){who.sendMessage("§cURL YouTube không hợp lệ.");return;} long now=System.currentTimeMillis(); long cooldown=plugin.getConfig().getLong("request.cooldown-seconds",5)*1000L; long last=cooldowns.getOrDefault(who.getUniqueId(),0L); if(!who.hasPermission("youtubemusic.bypass")&&now-last<cooldown){long left=Math.max(1,(cooldown-(now-last))/1000L);who.sendMessage("§eVui lòng chờ "+left+" giây.");return;} synchronized(this){if(queue.size()>=plugin.getConfig().getInt("queue.max-size",25)){who.sendMessage("§cHàng đợi đã đầy.");return;}} cooldowns.put(who.getUniqueId(),now); who.sendMessage("§e⏳ Đang phân tích và tải âm thanh YouTube..."); audioBackend.resolveAndDownload(url).whenComplete((resolved,error)->Bukkit.getScheduler().runTask(plugin,()->{if(error!=null){Throwable cause=error instanceof CompletionException&&error.getCause()!=null?error.getCause():error;who.sendMessage("§c✖ Không thể xử lý YouTube: §f"+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()));return;} Track source=resolved.track(); Track track=new Track(source.title(),source.url(),source.durationSeconds(),source.id(),who.getUniqueId()); addResolved(who,new AudioBackend.ResolvedAudio(track,resolved.audioFile()));})); }
    private void addResolved(Player who,AudioBackend.ResolvedAudio resolved){Track track=resolved.track(); synchronized(this){queue.add(track);who.sendMessage("§a✔ Đã thêm §f"+track.title()+" §avào hàng đợi.");if(current==null)next(resolved);} CompletableFuture.runAsync(()->storage.history(who.getUniqueId(),track));}
    public void search(Player who, String query, java.util.function.Consumer<java.util.List<Track>> callback) {
        if (!(audioBackend instanceof ApiAudioBackend api)) { callback.accept(java.util.List.of()); return; }
        api.search(query, 10).whenComplete((list, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause()!=null ? error.getCause() : error;
                who.sendMessage("§c✖ Tìm kiếm thất bại: §f" + (cause.getMessage()==null ? cause.getClass().getSimpleName() : cause.getMessage()));
                callback.accept(java.util.List.of());
            } else callback.accept(list);
        }));
    }
    public void playSaved(Player p, Track t) { if (t != null) request(p, t.url()); }
    public void playSearchResult(Player p, Track t) { if (t != null) request(p, t.url()); }
    public synchronized boolean toggleFavorite(Player p) {
        Track t=current; if(t==null) return false;
        if(storage.isFavorite(p.getUniqueId(),t.url())) { storage.unfavorite(p.getUniqueId(),t.url()); return false; }
        storage.favorite(p.getUniqueId(),t); return true;
    }
    private boolean validYoutube(String url){if(url==null||url.isBlank()||url.length()>plugin.getConfig().getInt("security.max-url-length",2048))return false;try{String h=URI.create(url.trim()).getHost();return h!=null&&(h.equalsIgnoreCase("youtube.com")||h.endsWith(".youtube.com")||h.equalsIgnoreCase("youtu.be"));}catch(Exception e){return false;}}
    public synchronized void next(){next(null);}
    private synchronized void next(AudioBackend.ResolvedAudio resolvedHint){if(loop&&current!=null){play(current,audioFiles.get(current.id()));return;} current=shuffle&&!queue.isEmpty()?removeRandom():queue.pollFirst(); paused=false; final Track selected=current; final long generation=++playbackGeneration; if(selected!=null){if(resolvedHint!=null&&resolvedHint.track().id().equals(selected.id())){play(selected,resolvedHint.audioFile());return;} audioBackend.resolveAndDownload(selected.url()).whenComplete((r,e)->Bukkit.getScheduler().runTask(plugin,()->{synchronized(MusicManager.this){if(generation!=playbackGeneration||current==null||!current.id().equals(selected.id()))return;if(e!=null){audioFailed(selected,e.getMessage());return;}play(selected,r.audioFile());}}));}else if(radio){Bukkit.getOnlinePlayers().forEach(p->p.sendMessage("§d📻 Radio đang chờ bài tiếp theo. Hãy thêm một bài vào hàng đợi."));}}
    private Track removeRandom(){int n=ThreadLocalRandom.current().nextInt(queue.size());Iterator<Track> it=queue.iterator();while(n-->0)it.next();Track t=it.next();it.remove();return t;}
    private synchronized void play(Track t,java.nio.file.Path audioFile){if(t==null||current==null||!current.id().equals(t.id()))return;if(audioFile!=null)audioFiles.put(t.id(),audioFile);for(Player p:Bukkit.getOnlinePlayers())p.sendMessage("§b🎵 Đang phát: §f"+t.title());if(audioFile==null){plugin.getLogger().warning("Thiếu audio file cho track "+t.id());audioFailed(t,"Thiếu file âm thanh đã tải.");return;}if(audioDelivery==null)audioDelivery=new JavaAudioDelivery(plugin,this);audioDelivery.prepareAndPlay(new AudioBackend.ResolvedAudio(t,audioFile));}
    public synchronized void audioPlaybackStarted(Track t){if(current==null||t==null||!current.id().equals(t.id())||paused)return; lyrics.start(t);ScheduledFuture<?> old=endTasks.remove(t.id());if(old!=null)old.cancel(false);long seconds=Math.max(1,t.durationSeconds());endTasks.put(t.id(),pool.schedule(()->Bukkit.getScheduler().runTask(plugin,()->{synchronized(MusicManager.this){if(current!=null&&current.id().equals(t.id())&&!paused)next();}}),seconds+1,TimeUnit.SECONDS));}
    public synchronized void audioFailed(Track t,String reason){if(t!=null&&current!=null&&current.id().equals(t.id())){playbackGeneration++;for(Player p:Bukkit.getOnlinePlayers())p.sendMessage("§c✖ Không thể phát: §f"+t.title());ScheduledFuture<?> f=endTasks.remove(t.id());if(f!=null)f.cancel(false);current=null;next();}plugin.getLogger().warning("Audio playback failed: "+(reason==null||reason.isBlank()?"không rõ nguyên nhân":reason));}
    public synchronized void stop(){playbackGeneration++; lyrics.stop();Track old=current;if(audioDelivery!=null)audioDelivery.stopCurrent(old);current=null;queue.clear();paused=false;audioFiles.clear();endTasks.values().forEach(f->f.cancel(false));endTasks.clear();}
    public synchronized void pause(){if(current!=null&&!paused){paused=true; lyrics.pause();ScheduledFuture<?> f=endTasks.remove(current.id());if(f!=null)f.cancel(false);if(audioDelivery!=null)audioDelivery.stopCurrent(current);}}
    public synchronized void resume(){if(current!=null&&paused){paused=false; lyrics.resume();java.nio.file.Path file=audioFiles.get(current.id());if(file!=null){if(audioDelivery==null)audioDelivery=new JavaAudioDelivery(plugin,this);audioDelivery.prepareAndPlay(new AudioBackend.ResolvedAudio(current,file));}else{audioBackend.resolveAndDownload(current.url()).whenComplete((r,e)->Bukkit.getScheduler().runTask(plugin,()->{synchronized(MusicManager.this){if(e!=null){audioFailed(current,e.getMessage());return;}if(current!=null){audioFiles.put(current.id(),r.audioFile());play(current,r.audioFile());}}}));}}}
    public synchronized void skip(){playbackGeneration++; lyrics.skip();Track old=current;if(audioDelivery!=null)audioDelivery.stopCurrent(old);if(old!=null){ScheduledFuture<?> f=endTasks.remove(old.id());if(f!=null)f.cancel(false);}next();}
    public synchronized void reloadSettings(){loop=plugin.getConfig().getBoolean("queue.loop",loop);shuffle=plugin.getConfig().getBoolean("queue.shuffle",shuffle);radio=plugin.getConfig().getBoolean("queue.radio",radio);int max=Math.max(1,plugin.getConfig().getInt("queue.max-size",25));while(queue.size()>max)queue.removeLast();}
    public synchronized void toggleLoop(){loop=!loop;} public synchronized void toggleShuffle(){shuffle=!shuffle;} public synchronized void toggleRadio(){radio=!radio;}
    public synchronized void setVolume(Player p,int v){int max=Math.max(1,Math.min(100,plugin.getConfig().getInt("player.max-volume",100)));volumes.put(p.getUniqueId(),Math.max(0,Math.min(max,v)));if(current!=null&&audioDelivery!=null)audioDelivery.updatePlayerVolume(p,volumes.get(p.getUniqueId()));}
    public int volume(Player p){return volumes.getOrDefault(p.getUniqueId(),Math.max(0,Math.min(100,plugin.getConfig().getInt("player.default-volume",80))));}
    public synchronized void remove(int index){if(index<1||index>queue.size())return;Iterator<Track> i=queue.iterator();for(int n=1;n<index;n++)i.next();i.next();i.remove();} public synchronized void clearQueue(){queue.clear();} public synchronized Track current(){return current;} public LyricsManager lyrics(){return lyrics;} public synchronized Deque<Track> queue(){return new ArrayDeque<>(queue);} public synchronized boolean paused(){return paused;} public synchronized boolean loop(){return loop;} public synchronized boolean shuffle(){return shuffle;} public synchronized boolean radio(){return radio;}
    public synchronized void shutdown(){playbackGeneration++; lyrics.shutdown();if(audioDelivery!=null)audioDelivery.stopCurrent(current);endTasks.values().forEach(f->f.cancel(false));endTasks.clear();pool.shutdownNow();if(audioBackend instanceof ApiAudioBackend a)a.shutdown();if(audioDelivery!=null)audioDelivery.shutdown();}
}
