package uk.minecostudios.jukeify.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SpotifyAuth {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String SCOPES = "streaming user-read-email user-read-private user-read-playback-state user-read-currently-playing user-modify-playback-state";

    private static volatile Status status = Status.IDLE;
    private static volatile String statusMessage = "Not connected";
    private static volatile String accessToken;
    private static volatile String refreshToken;
    private static volatile long expiresAtMillis;

    private SpotifyAuth() {}

    public enum Status { IDLE, WAITING_FOR_BROWSER, EXCHANGING_CODE, CONNECTED, ERROR }

    public static Status getStatus() { return status; }
    public static String getStatusMessage() { return statusMessage; }
    public static boolean isConnected() { return status == Status.CONNECTED && accessToken != null; }

    public static synchronized String getValidAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < expiresAtMillis) return accessToken;
        String clientId = SpotifyConfig.loadClientId();
        if (clientId.isBlank()) return null;
        if (refreshToken == null || refreshToken.isBlank()) refreshToken = SpotifyTokenStore.loadRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) return null;
        return refreshNow(clientId) ? accessToken : null;
    }

    public static void restoreSessionAsync() {
        if (isConnected()) return;
        String clientId = SpotifyConfig.loadClientId();
        String stored = SpotifyTokenStore.loadRefreshToken();
        if (clientId.isBlank() || stored.isBlank()) return;
        refreshToken = stored;
        statusMessage = "Restoring Spotify session...";
        CompletableFuture.runAsync(() -> refreshNow(clientId));
    }

    public static void disconnect() {
        accessToken = null;
        refreshToken = null;
        expiresAtMillis = 0L;
        SpotifyTokenStore.clear();
        status = Status.IDLE;
        statusMessage = "Not connected";
    }

    public static void beginLogin(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            fail("Enter your Spotify Client ID first.");
            return;
        }
        if (status == Status.WAITING_FOR_BROWSER || status == Status.EXCHANGING_CODE) return;

        CompletableFuture.runAsync(() -> {
            try (ServerSocket server = new ServerSocket(43821, 1, InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(180_000);
                String verifier = randomBase64Url(64);
                String challenge = base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
                String state = randomBase64Url(24);
                String redirectUri = "http://127.0.0.1:43821/callback";

                URI authorizeUri = URI.create("https://accounts.spotify.com/authorize"
                        + "?client_id=" + enc(clientId.trim())
                        + "&response_type=code"
                        + "&redirect_uri=" + enc(redirectUri)
                        + "&scope=" + enc(SCOPES)
                        + "&code_challenge_method=S256"
                        + "&code_challenge=" + enc(challenge)
                        + "&state=" + enc(state));

                status = Status.WAITING_FOR_BROWSER;
                statusMessage = "Browser opened - approve Jukeify in Spotify";
                Util.getPlatform().openUri(authorizeUri);

                Callback callback = waitForCallback(server);
                if (callback.error != null) {
                    fail("Spotify login was cancelled: " + callback.error);
                    return;
                }
                if (!state.equals(callback.state)) {
                    fail("Spotify login failed: state check did not match.");
                    return;
                }
                if (callback.code == null || callback.code.isBlank()) {
                    fail("Spotify login failed: no authorization code returned.");
                    return;
                }

                status = Status.EXCHANGING_CODE;
                statusMessage = "Finishing Spotify login...";
                String body = "grant_type=authorization_code"
                        + "&code=" + enc(callback.code)
                        + "&redirect_uri=" + enc(redirectUri)
                        + "&client_id=" + enc(clientId.trim())
                        + "&code_verifier=" + enc(verifier);

                HttpResponse<String> response = sendTokenRequest(body);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    fail("Spotify token request failed (HTTP " + response.statusCode() + ").");
                    return;
                }
                acceptTokenResponse(response.body(), true);
            } catch (java.net.SocketTimeoutException e) {
                fail("Spotify login timed out. Try Connect again.");
            } catch (Exception e) {
                fail("Spotify login failed: " + safeMessage(e));
            }
        });
    }

    private static synchronized boolean refreshNow(String clientId) {
        if (refreshToken == null || refreshToken.isBlank()) return false;
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + enc(refreshToken)
                    + "&client_id=" + enc(clientId);
            HttpResponse<String> response = sendTokenRequest(body);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                fail("Spotify session expired. Connect again.");
                return false;
            }
            acceptTokenResponse(response.body(), false);
            return true;
        } catch (Exception e) {
            fail("Could not refresh Spotify session.");
            return false;
        }
    }

    private static HttpResponse<String> sendTokenRequest(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void acceptTokenResponse(String body, boolean requireRefreshToken) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        String token = json.has("access_token") ? json.get("access_token").getAsString() : null;
        if (token == null || token.isBlank()) {
            fail("Spotify did not return an access token.");
            return;
        }
        accessToken = token;
        if (json.has("refresh_token")) {
            refreshToken = json.get("refresh_token").getAsString();
            SpotifyTokenStore.saveRefreshToken(refreshToken);
        } else if (requireRefreshToken && (refreshToken == null || refreshToken.isBlank())) {
            fail("Spotify did not return a refresh token.");
            return;
        }
        int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
        expiresAtMillis = System.currentTimeMillis() + Math.max(60, expiresIn - 30) * 1000L;
        status = Status.CONNECTED;
        statusMessage = "Connected to Spotify";
    }

    private static Callback waitForCallback(ServerSocket server) throws IOException {
        try (Socket socket = server.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream out = socket.getOutputStream()) {
            String requestLine = reader.readLine();
            String target = "/";
            if (requestLine != null) {
                String[] parts = requestLine.split(" ");
                if (parts.length >= 2) target = parts[1];
            }
            Map<String, String> params = parseQuery(target);
            String error = params.get("error");
            String html = error == null
                    ? "<!doctype html><html><body style='font-family:sans-serif;background:#121212;color:white;padding:40px'><h2>Jukeify connected</h2><p>You can close this tab and return to Minecraft.</p></body></html>"
                    : "<!doctype html><html><body style='font-family:sans-serif;background:#121212;color:white;padding:40px'><h2>Jukeify login cancelled</h2></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
            return new Callback(params.get("code"), params.get("state"), error);
        }
    }

    private static Map<String, String> parseQuery(String target) {
        Map<String, String> result = new HashMap<>();
        int q = target.indexOf('?');
        if (q < 0 || q + 1 >= target.length()) return result;
        for (String pair : target.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static byte[] sha256(byte[] input) throws Exception { return MessageDigest.getInstance("SHA-256").digest(input); }
    private static String randomBase64Url(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }
    private static String base64Url(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    private static void fail(String message) {
        accessToken = null;
        expiresAtMillis = 0L;
        status = Status.ERROR;
        statusMessage = message;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 140 ? message.substring(0, 140) : message;
    }

    private record Callback(String code, String state, String error) {}
}
