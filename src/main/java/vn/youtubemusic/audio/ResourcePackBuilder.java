package vn.youtubemusic.audio;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.*;

/** Builds a Java resource pack containing one OGG custom sound. */
public final class ResourcePackBuilder {
    private ResourcePackBuilder() {}
    public static Pack build(Path audio, Path outputDir, String soundId, String description) throws Exception {
        Files.createDirectories(outputDir); Path pack = outputDir.resolve(soundId.replace(':', '_') + ".zip");
        String[] split = soundId.split(":", 2); String namespace = split.length == 2 ? split[0] : "youtubemusic"; String name = split.length == 2 ? split[1] : soundId;
        Path temp = Files.createTempDirectory("ym-pack-");
        try {
            Path sounds = temp.resolve("assets").resolve(namespace).resolve("sounds"); Files.createDirectories(sounds);
            Files.copy(audio, sounds.resolve(name + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
            String json = "{\n  \"" + name + "\": {\"sounds\":[{\"name\":\"" + name + "\",\"stream\":true}]}\n}\n";
            Files.writeString(temp.resolve("assets").resolve(namespace).resolve("sounds.json"), json);
            Files.writeString(temp.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":88,\"min_format\":[88,0],\"max_format\":[88,0],\"description\":\"" + escape(description) + "\"}}\n");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) { Files.walk(temp).filter(Files::isRegularFile).forEach(p -> { try { ZipEntry e = new ZipEntry(temp.relativize(p).toString().replace('\\','/')); zip.putNextEntry(e); Files.copy(p, zip); zip.closeEntry(); } catch (IOException ex) { throw new UncheckedIOException(ex); } }); }
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(pack)); return new Pack(pack, hash);
        } finally { deleteTree(temp); }
    }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static void deleteTree(Path p) throws IOException { if (!Files.exists(p)) return; try (var st = Files.walk(p)) { st.sorted((a,b)->b.getNameCount()-a.getNameCount()).forEach(x -> { try { Files.deleteIfExists(x); } catch(IOException ignored){} }); } }
    public record Pack(Path zip, byte[] sha1) {}
}
