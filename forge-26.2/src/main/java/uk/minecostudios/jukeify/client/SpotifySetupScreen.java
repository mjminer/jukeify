package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import uk.minecostudios.jukeify.spotify.SpotifyAuth;
import uk.minecostudios.jukeify.spotify.SpotifyConfig;

public final class SpotifySetupScreen extends Screen {
    private final Screen parent;
    private EditBox clientIdBox;
    private String saveMessage = "";

    public SpotifySetupScreen(Screen parent) {
        super(Component.literal("Spotify Setup"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        clientIdBox = new EditBox(font, cx - 120, cy - 28, 240, 20, Component.literal("Spotify Client ID"));
        clientIdBox.setMaxLength(128);
        clientIdBox.setValue(SpotifyConfig.loadClientId());
        addRenderableWidget(clientIdBox);

        addRenderableWidget(Button.builder(Component.literal("Save Client ID"), b -> {
            saveMessage = SpotifyConfig.saveClientId(clientIdBox.getValue())
                    ? "Client ID saved"
                    : "Could not save Client ID";
        }).bounds(cx - 120, cy + 2, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Connect Spotify"), b -> {
            String clientId = clientIdBox.getValue().trim();
            if (!SpotifyConfig.saveClientId(clientId)) {
                saveMessage = "Could not save Client ID";
                return;
            }
            saveMessage = "";
            SpotifyAuth.beginLogin(clientId);
        }).bounds(cx + 4, cy + 2, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Disconnect"), b -> SpotifyAuth.disconnect())
                .bounds(cx - 120, cy + 28, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 4, cy + 28, 116, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int cx = width / 2;
        int cy = height / 2;

        g.centeredText(font, title, cx, cy - 78, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Paste the Client ID from your Spotify Developer app"), cx, cy - 55, 0xFFBBBBBB);

        int statusColor = SpotifyAuth.isConnected() ? 0xFF1DB954
                : SpotifyAuth.getStatus() == SpotifyAuth.Status.ERROR ? 0xFFFF7777
                : 0xFFE7C27D;
        g.centeredText(font, Component.literal(SpotifyAuth.getStatusMessage()), cx, cy + 58, statusColor);

        if (!saveMessage.isBlank()) {
            g.centeredText(font, Component.literal(saveMessage), cx, cy + 76, 0xFFAAAAAA);
        }

        g.centeredText(font, Component.literal("Redirect URI: http://127.0.0.1/callback"), cx, cy + 94, 0xFF777777);
        g.centeredText(font, Component.literal("No Spotify password or Client Secret is stored by Jukeify."), cx, cy + 110, 0xFF8FCA9A);
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
