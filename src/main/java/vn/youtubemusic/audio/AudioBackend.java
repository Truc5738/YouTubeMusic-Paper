package vn.youtubemusic.audio;

import vn.youtubemusic.music.Track;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface AudioBackend {
    CompletableFuture<ResolvedAudio> resolveAndDownload(String url);
    record ResolvedAudio(Track track, Path audioFile) {}
}
