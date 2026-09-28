package uk.minecostudios.jukeify.spotify;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

final class SpotifyTokenStore {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("jukeify-spotify-session.properties");
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private SpotifyTokenStore() {}

    static String loadRefreshToken() {
        if (!Files.exists(PATH)) return "";
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            properties.load(in);
            return properties.getProperty(KEY_REFRESH_TOKEN, "").trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    static void saveRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        Properties properties = new Properties();
        properties.setProperty(KEY_REFRESH_TOKEN, refreshToken.trim());
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                properties.store(out, "Jukeify Spotify session. Keep this file private.");
            }
            restrictPermissionsBestEffort();
        } catch (IOException ignored) {
        }
    }

    static void clear() {
        try {
            Files.deleteIfExists(PATH);
        } catch (IOException ignored) {
        }
    }

    private static void restrictPermissionsBestEffort() {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(PATH, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows does not expose POSIX permissions through this API.
        }
    }
}
