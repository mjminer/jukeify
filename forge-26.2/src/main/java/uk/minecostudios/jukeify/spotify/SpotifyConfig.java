package uk.minecostudios.jukeify.spotify;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SpotifyConfig {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("jukeify-spotify.properties");
    private static final String KEY_CLIENT_ID = "client_id";

    private SpotifyConfig() {}

    public static String loadClientId() {
        if (!Files.exists(PATH)) return "";
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            properties.load(in);
            return properties.getProperty(KEY_CLIENT_ID, "").trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    public static boolean saveClientId(String clientId) {
        Properties properties = new Properties();
        properties.setProperty(KEY_CLIENT_ID, clientId == null ? "" : clientId.trim());
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                properties.store(out, "Jukeify Spotify settings - Client ID is public; OAuth tokens are not stored here.");
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
