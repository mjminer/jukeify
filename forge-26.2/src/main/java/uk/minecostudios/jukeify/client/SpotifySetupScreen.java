package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import uk.minecostudios.jukeify.spotify.SpotifyAuth;
import uk.minecostudios.jukeify.spotify.SpotifyConfig;

import java.awt.Desktop;
import java.net.URI;

public final class SpotifySetupScreen extends Screen {
    private static final String DASHBOARD_URL = "https://developer.spotify.com/dashboard";
    private final Screen parent;
    private EditBox clientIdBox;
    private String saveMessage = "";

    public SpotifySetupScreen(Screen parent) {
        super(Component.literal("Jukeify - First-time Spotify Setup"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        SpotifyAuth.restoreSessionAsync();

        int cx = width / 2;
        int cy = height / 2;

        clientIdBox = new EditBox(font, cx - 140, cy - 24, 280, 20, Component.literal("Spotify Client ID"));
        clientIdBox.setMaxLength(128);
        clientIdBox.setValue(SpotifyConfig.loadClientId());
        addRenderableWidget(clientIdBox);

        addRenderableWidget(Button.builder(Component.literal("1. Open Spotify Developer Dashboard"), b -> openDashboard())
                .bounds(cx - 140, cy + 4, 280, 20).build());

        addRenderableWidget(Button.builder(Component.literal("2. Save & Connect Spotify"), b -> {
            String clientId = clientIdBox.getValue().trim();
            if (clientId.isBlank()) {
                saveMessage = "Paste your Client ID first";
                return;
            }
            if (!SpotifyConfig.saveClientId(clientId)) {
                saveMessage = "Could not save Client ID";
                return;
            }
            saveMessage = "";
            SpotifyAuth.beginLogin(clientId);
        }).bounds(cx - 140, cy + 30, 280, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Disconnect / Forget Login"), b -> {
            SpotifyAuth.disconnect();
            saveMessage = "Spotify session removed";
        }).bounds(cx - 140, cy + 56, 136, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 4, cy + 56, 136, 20).build());
    }

    private void openDashboard() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(DASHBOARD_URL));
                saveMessage = "Spotify Developer Dashboard opened";
            } else {
                saveMessage = "Could not open browser";
            }
        } catch (Exception e) {
            saveMessage = "Could not open browser";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int cx = width / 2;
        int cy = height / 2;

        g.centeredText(font, title, cx, cy - 96, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("This is a one-time setup. Normal use will just open the jukebox player."), cx, cy - 76, 0xFFE7C27D);
        g.centeredText(font, Component.literal("Create a Spotify app, add the redirect URI below, then paste its Client ID."), cx, cy - 58, 0xFFBBBBBB);

        int statusColor = SpotifyAuth.isConnected() ? 0xFF1DB954
                : SpotifyAuth.getStatus() == SpotifyAuth.Status.ERROR ? 0xFFFF7777
                : 0xFFE7C27D;
        g.centeredText(font, Component.literal(SpotifyAuth.getStatusMessage()), cx, cy + 86, statusColor);

        if (!saveMessage.isBlank()) {
            g.centeredText(font, Component.literal(saveMessage), cx, cy + 102, 0xFFAAAAAA);
        }

        g.centeredText(font, Component.literal("Redirect URI to register: http://127.0.0.1/callback"), cx, cy + 120, 0xFFAAAAAA);
        g.centeredText(font, Component.literal("Never enter your Spotify password or Client Secret into Jukeify."), cx, cy + 136, 0xFF8FCA9A);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
