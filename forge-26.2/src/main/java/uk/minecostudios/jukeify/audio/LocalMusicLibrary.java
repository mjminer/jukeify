package uk.minecostudios.jukeify.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class LocalMusicLibrary {
    private LocalMusicLibrary() {}

    public static Path musicDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("jukeify").resolve("music");
    }

    public static List<Path> scan() {
        Path dir = musicDirectory();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> stream = Files.list(dir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(LocalMusicLibrary::supported)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    public static void openFolder() {
        Path dir = musicDirectory();
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openUri(dir.toUri());
        } catch (Exception ignored) {
        }
    }

    private static boolean supported(Path path) {
        String n = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".ogg") || n.endsWith(".wav");
    }
}
