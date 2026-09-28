package uk.minecostudios.jukeify.spotify;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SpotifyWebPlayerBridge {
    private static final int PORT = 43822;
    private static final double FULL_VOLUME_DISTANCE = 4.0;
    private static final double MAX_DISTANCE = 32.0;

    private static volatile boolean serverStarted;
    private static volatile boolean pageOpened;
    private static volatile String deviceId = "";
    private static volatile String statusMessage = "Jukeify player not started";
    private static volatile BlockPos activeJukebox;
    private static volatile double attenuation;
    private static volatile double distance = Double.POSITIVE_INFINITY;
    private static volatile double baseVolume = 0.65;

    private SpotifyWebPlayerBridge() {}

    public static void setActiveJukebox(BlockPos pos) {
        activeJukebox = pos == null ? null : pos.immutable();
    }

    public static boolean isReady() {
        return !deviceId.isBlank();
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static String getDeviceId() {
        return deviceId;
    }

    public static int getBaseVolumePercent() {
        return (int)Math.round(baseVolume * 100.0);
    }

    public static int getEffectiveVolumePercent() {
        return (int)Math.round(baseVolume * attenuation * 100.0);
    }

    public static double getDistance() {
        return distance;
    }

    public static void adjustBaseVolume(double delta) {
        baseVolume = Math.max(0.0, Math.min(1.0, baseVolume + delta));
    }

    public static void startForJukebox(BlockPos pos) {
        setActiveJukebox(pos);
        ensureServerStarted();
        pageOpened = true;
        statusMessage = "Opening Jukeify Spotify player...";
        Util.getPlatform().openUri(URI.create("http://127.0.0.1:" + PORT + "/player"));
    }

    public static void ensureServerStarted() {
        if (serverStarted) return;
        synchronized (SpotifyWebPlayerBridge.class) {
            if (serverStarted) return;
            serverStarted = true;
            CompletableFuture.runAsync(SpotifyWebPlayerBridge::serverLoop);
        }
    }

    public static void onClientTick() {
        BlockPos pos = activeJukebox;
        Minecraft minecraft = Minecraft.getInstance();
        if (pos == null || minecraft.player == null) {
            distance = Double.POSITIVE_INFINITY;
            attenuation = 0.0;
            return;
        }

        double dx = minecraft.player.getX() - (pos.getX() + 0.5);
        double dy = minecraft.player.getY() - (pos.getY() + 0.5);
        double dz = minecraft.player.getZ() - (pos.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        distance = d;

        if (d <= FULL_VOLUME_DISTANCE) {
            attenuation = 1.0;
        } else if (d >= MAX_DISTANCE) {
            attenuation = 0.0;
        } else {
            attenuation = (MAX_DISTANCE - d) / (MAX_DISTANCE - FULL_VOLUME_DISTANCE);
        }
    }

    private static void serverLoop() {
        try (ServerSocket server = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))) {
            statusMessage = "Jukeify player server ready";
            while (true) {
                try (Socket socket = server.accept()) {
                    handle(socket);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            serverStarted = false;
            statusMessage = "Could not start Jukeify player on port " + PORT;
        }
    }

    private static void handle(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) return;

        String[] parts = requestLine.split(" ");
        String target = parts.length >= 2 ? parts[1] : "/";
        while (true) {
            String line = reader.readLine();
            if (line == null || line.isBlank()) break;
        }

        int q = target.indexOf('?');
        String path = q >= 0 ? target.substring(0, q) : target;
        Map<String, String> query = q >= 0 ? parseQuery(target.substring(q + 1)) : Map.of();

        switch (path) {
            case "/", "/player" -> send(socket, 200, "text/html; charset=utf-8", playerHtml());
            case "/token" -> {
                String token = SpotifyAuth.getValidAccessToken();
                if (token == null) {
                    send(socket, 401, "application/json", "{\"error\":\"not_connected\"}");
                } else {
                    send(socket, 200, "application/json", "{\"access_token\":\"" + json(token) + "\"}");
                }
            }
            case "/ready" -> {
                String id = query.getOrDefault("device_id", "");
                if (!id.isBlank()) {
                    deviceId = id;
                    statusMessage = "Jukeify Jukebox ready";
                    SpotifyPlayback.transferToDevice(id, true);
                }
                send(socket, 200, "application/json", "{\"ok\":true}");
            }
            case "/state" -> {
                String body = "{\"volume\":" + clamp(baseVolume * attenuation)
                        + ",\"base_volume\":" + clamp(baseVolume)
                        + ",\"distance\":" + (Double.isFinite(distance) ? String.format(java.util.Locale.ROOT, "%.2f", distance) : "-1")
                        + ",\"ready\":" + isReady()
                        + "}";
                send(socket, 200, "application/json", body);
            }
            default -> send(socket, 404, "text/plain; charset=utf-8", "Not found");
        }
    }

    private static String playerHtml() {
        return """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Jukeify Spotify Player</title>
<style>
body{margin:0;background:#121212;color:#fff;font-family:system-ui,Segoe UI,sans-serif;display:grid;place-items:center;min-height:100vh}
.card{width:min(560px,90vw);background:#1c1c1c;border-radius:18px;padding:28px;box-shadow:0 12px 40px #0008}
h1{margin:0 0 8px;color:#1ed760}.muted{color:#aaa}.ok{color:#1ed760}.warn{color:#f4c96b}
button{background:#1ed760;border:0;border-radius:999px;padding:12px 18px;font-weight:700;cursor:pointer}
</style>
</head>
<body>
<div class="card">
<h1>Jukeify Jukebox</h1>
<p id="status" class="warn">Starting Spotify player...</p>
<p class="muted">Keep this tab open while using the Minecraft jukebox. You can minimize it.</p>
<button id="activate">Activate Audio</button>
</div>
<script src="https://sdk.scdn.co/spotify-player.js"></script>
<script>
let player;
let lastVolume = -1;
async function token() {
  const r = await fetch('/token', {cache:'no-store'});
  if (!r.ok) throw new Error('Spotify login required');
  return (await r.json()).access_token;
}
window.onSpotifyWebPlaybackSDKReady = async () => {
  player = new Spotify.Player({
    name: 'Jukeify Jukebox',
    getOAuthToken: async cb => { try { cb(await token()); } catch(e) { document.getElementById('status').textContent=e.message; } },
    volume: 0.65
  });
  player.addListener('ready', async ({device_id}) => {
    document.getElementById('status').className='ok';
    document.getElementById('status').textContent='Connected as Jukeify Jukebox';
    await fetch('/ready?device_id='+encodeURIComponent(device_id), {cache:'no-store'});
  });
  player.addListener('not_ready', () => {
    document.getElementById('status').className='warn';
    document.getElementById('status').textContent='Jukeify player went offline';
  });
  player.addListener('authentication_error', ({message}) => {
    document.getElementById('status').textContent='Spotify login error: '+message;
  });
  player.addListener('account_error', ({message}) => {
    document.getElementById('status').textContent='Spotify Premium / account error: '+message;
  });
  player.addListener('initialization_error', ({message}) => {
    document.getElementById('status').textContent='Player error: '+message;
  });
  await player.connect();

  setInterval(async () => {
    if (!player) return;
    try {
      const s = await (await fetch('/state', {cache:'no-store'})).json();
      const v = Math.max(0, Math.min(1, Number(s.volume)||0));
      if (Math.abs(v-lastVolume) >= 0.01) {
        await player.setVolume(v);
        lastVolume = v;
      }
    } catch(e) {}
  }, 300);
};
document.getElementById('activate').onclick = async () => {
  if (!player) return;
  try {
    await player.activateElement();
    document.getElementById('status').textContent='Audio activated - return to Minecraft';
  } catch(e) {
    document.getElementById('status').textContent='Could not activate audio: '+e.message;
  }
};
</script>
</body>
</html>
""";
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace(""", "\\"");
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void send(Socket socket, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 401 ? "Unauthorized" : "Not Found";
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        try (OutputStream out = socket.getOutputStream()) {
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        }
    }
}
