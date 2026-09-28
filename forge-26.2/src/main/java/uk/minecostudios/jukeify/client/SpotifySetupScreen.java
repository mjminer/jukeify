package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import uk.minecostudios.jukeify.spotify.SpotifyAuth;
import uk.minecostudios.jukeify.spotify.SpotifyConfig;

public final class SpotifySetupScreen extends Screen {
    private final Screen parent;

    public SpotifySetupScreen(Screen parent) {
        super(Component.literal("Spotify"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        SpotifyAuth.restoreSessionAsync();

        int cx = width / 2;
        int cy = height / 2;

        addRenderableWidget(Button.builder(
                Component.literal(SpotifyAuth.isConnected() ? "Reconnect Spotify" : "Connect Spotify"),
                b -> {
                    if (SpotifyConfig.isConfigured()) {
                        SpotifyAuth.beginLogin(SpotifyConfig.loadClientId());
                    }
                })
                .bounds(cx - 120, cy - 8, 240, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Disconnect / Forget Login"), b -> SpotifyAuth.disconnect())
                .bounds(cx - 120, cy + 20, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 4, cy + 20, 116, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int cx = width / 2;
        int cy = height / 2;

        g.centeredText(font, title, cx, cy - 70, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Connect Jukeify to your Spotify account"), cx, cy - 46, 0xFFE7C27D);
        g.centeredText(font, Component.literal("Your browser will open so you can sign in and approve access."), cx, cy - 28, 0xFFBBBBBB);

        String status = SpotifyConfig.isConfigured()
                ? SpotifyAuth.getStatusMessage()
                : "Jukeify Spotify Client ID has not been configured yet.";

        int statusColor = SpotifyAuth.isConnected() ? 0xFF1DB954
                : (!SpotifyConfig.isConfigured() || SpotifyAuth.getStatus() == SpotifyAuth.Status.ERROR) ? 0xFFFF7777
                : 0xFFE7C27D;

        g.centeredText(font, Component.literal(status), cx, cy + 54, statusColor);
        g.centeredText(font, Component.literal("Jukeify never asks for or stores your Spotify password."), cx, cy + 76, 0xFF8FCA9A);
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
