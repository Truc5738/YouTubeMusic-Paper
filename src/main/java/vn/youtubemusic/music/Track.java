package vn.youtubemusic.music;

import java.util.UUID;

public record Track(String title, String url, long durationSeconds, String id, UUID requestedBy) {
    public Track(String title, String url, long durationSeconds, String id) {
        this(title, url, durationSeconds, id, null);
    }
}
