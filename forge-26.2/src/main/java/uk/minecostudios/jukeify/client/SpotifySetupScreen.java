package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SpotifySetupScreen extends Screen {
    private final Screen parent;

    public SpotifySetupScreen(Screen parent) {
        super(Component.literal("Spotify Setup"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(width / 2 - 75, height / 2 + 38, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, height / 2 - 56, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Spotify PKCE login is the next milestone."), width / 2, height / 2 - 20, 0xFFBBBBBB);
        g.centeredText(font, Component.literal("The mod will never store your Spotify password."), width / 2, height / 2, 0xFF8FCA9A);
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
