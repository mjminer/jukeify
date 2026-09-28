package uk.minecostudios.jukeify.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SpotifyPlayback {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private static volatile TrackState state = TrackState.empty("Waiting for Spotify...");
    private static volatile long lastRefreshMillis;

    private SpotifyPlayback() {}

    public static TrackState getState() {
        return state;
    }

    public static void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < 2000L) return;
        refresh();
    }

    public static void refresh() {
        if (!REFRESHING.compareAndSet(false, true)) return;
        lastRefreshMillis = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                String token = SpotifyAuth.getValidAccessToken();
                if (token == null) {
                    state = TrackState.empty(SpotifyAuth.getStatusMessage());
                    return;
                }

                HttpRequest request = authorizedRequest("https://api.spotify.com/v1/me/player", token)
                        .GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204) {
                    state = TrackState.empty("No active Spotify playback");
                    return;
                }
                if (response.statusCode() == 401) {
                    state = TrackState.empty("Spotify session needs reconnecting");
                    return;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    state = TrackState.empty("Spotify playback error (HTTP " + response.statusCode() + ")");
                    return;
                }

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject item = root.has("item") && root.get("item").isJsonObject() ? root.getAsJsonObject("item") : null;
                JsonObject device = root.has("device") && root.get("device").isJsonObject() ? root.getAsJsonObject("device") : null;

                if (item == null) {
                    state = TrackState.empty("Nothing playing");
                    return;
                }

                String title = text(item, "name", "Unknown track");
                String artist = artists(item);
                String album = "";
                String albumArt = "";
                if (item.has("album") && item.get("album").isJsonObject()) {
                    JsonObject albumObj = item.getAsJsonObject("album");
                    album = text(albumObj, "name", "");
                    if (albumObj.has("images") && albumObj.get("images").isJsonArray()) {
                        JsonArray images = albumObj.getAsJsonArray("images");
                        if (!images.isEmpty() && images.get(0).isJsonObject()) {
                            albumArt = text(images.get(0).getAsJsonObject(), "url", "");
                        }
                    }
                }

                boolean playing = root.has("is_playing") && root.get("is_playing").getAsBoolean();
                int progressMs = root.has("progress_ms") && !root.get("progress_ms").isJsonNull() ? root.get("progress_ms").getAsInt() : 0;
                int durationMs = item.has("duration_ms") ? item.get("duration_ms").getAsInt() : 0;
                int volume = device != null && device.has("volume_percent") && !device.get("volume_percent").isJsonNull()
                        ? device.get("volume_percent").getAsInt() : -1;
                String deviceName = device == null ? "" : text(device, "name", "");

                state = new TrackState(true, playing, title, artist, album, albumArt, deviceName, volume, progressMs, durationMs, "");
            } catch (Exception e) {
                state = TrackState.empty("Could not reach Spotify");
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    public static void togglePlayPause() {
        TrackState current = state;
        sendCommand(current.playing() ? "PUT" : "PUT",
                current.playing() ? "/v1/me/player/pause" : "/v1/me/player/play", null);
    }

    public static void previous() {
        sendCommand("POST", "/v1/me/player/previous", null);
    }

    public static void next() {
        sendCommand("POST", "/v1/me/player/next", null);
    }

    public static void setVolume(int volume) {
        int clamped = Math.max(0, Math.min(100, volume));
        sendCommand("PUT", "/v1/me/player/volume?volume_percent=" + clamped, null);
    }

    private static void sendCommand(String method, String path, String body) {
        CompletableFuture.runAsync(() -> {
            try {
                String token = SpotifyAuth.getValidAccessToken();
                if (token == null) return;
                HttpRequest.Builder builder = authorizedRequest("https://api.spotify.com" + path, token);
                if ("POST".equals(method)) {
                    builder.POST(HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.PUT(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
                }
                HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 403) {
                    state = TrackState.empty("Spotify Premium is required for playback controls");
                } else if (response.statusCode() == 404) {
                    state = TrackState.empty("No active Spotify device");
                }
                lastRefreshMillis = 0L;
                refresh();
            } catch (Exception ignored) {
            }
        });
    }

    private static HttpRequest.Builder authorizedRequest(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }

    private static String text(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static String artists(JsonObject item) {
        if (!item.has("artists") || !item.get("artists").isJsonArray()) return "";
        JsonArray artists = item.getAsJsonArray("artists");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            if (!artists.get(i).isJsonObject()) continue;
            String name = text(artists.get(i).getAsJsonObject(), "name", "");
            if (name.isBlank()) continue;
            if (!result.isEmpty()) result.append(", ");
            result.append(name);
        }
        return result.toString();
    }

    public record TrackState(
            boolean hasTrack,
            boolean playing,
            String title,
            String artist,
            String album,
            String albumArtUrl,
            String deviceName,
            int volume,
            int progressMs,
            int durationMs,
            String message
    ) {
        static TrackState empty(String message) {
            return new TrackState(false, false, "", "", "", "", "", -1, 0, 0, message);
        }
    }
}
