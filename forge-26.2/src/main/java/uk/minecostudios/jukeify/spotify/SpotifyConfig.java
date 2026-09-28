package uk.minecostudios.jukeify.spotify;

public final class SpotifyConfig {
    public static final String OFFICIAL_CLIENT_ID = "PASTE_SPOTIFY_CLIENT_ID_HERE";

    private SpotifyConfig() {}

    public static String loadClientId() {
        return OFFICIAL_CLIENT_ID;
    }

    public static boolean isConfigured() {
        return OFFICIAL_CLIENT_ID != null
                && !OFFICIAL_CLIENT_ID.isBlank()
                && !OFFICIAL_CLIENT_ID.equals("PASTE_SPOTIFY_CLIENT_ID_HERE");
    }
}
