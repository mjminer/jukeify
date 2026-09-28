package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class SpotifyJukeboxScreen extends Screen {
    private final BlockPos jukeboxPos;
    private long openedAt;

    public SpotifyJukeboxScreen(BlockPos jukeboxPos) {
        super(Component.literal("Jukeify"));
        this.jukeboxPos = jukeboxPos;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        int cx = width / 2;
        int cy = height / 2;

        addRenderableWidget(Button.builder(Component.literal("Connect Spotify"), b -> {
            if (minecraft != null) minecraft.gui.setScreen(new SpotifySetupScreen(this));
        }).bounds(cx + 34, cy + 16, 136, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 34, cy + 42, 136, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        g.fill(cx - 194, cy - 109, cx + 194, cy + 99, 0xD0180E08);
        g.fill(cx - 188, cy - 103, cx + 188, cy + 93, 0xFF68401F);
        g.fill(cx - 181, cy - 96, cx + 181, cy + 86, 0xFF2A170D);
    }

    private void drawVinyl(GuiGraphicsExtractor g, int cx, int cy) {
        int radius = 58;
        for (int yy = -radius; yy <= radius; yy++) {
            int xx = (int)Math.sqrt(radius * radius - yy * yy);
            g.fill(cx - xx, cy + yy, cx + xx + 1, cy + yy + 1, 0xFF111111);
        }

        double angle = ((System.currentTimeMillis() - openedAt) / 350.0) * Math.PI * 2.0;
        for (int i = 0; i < 4; i++) {
            double a = angle + i * Math.PI / 2.0;
            int x = cx + (int)(Math.cos(a) * 42);
            int y = cy + (int)(Math.sin(a) * 42);
            g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF333333);
        }

        g.fill(cx - 19, cy - 19, cx + 20, cy + 20, 0xFF1DB954);
        g.fill(cx - 15, cy - 15, cx + 16, cy - 6, 0xFF845EC2);
        g.fill(cx - 15, cy - 4, cx + 16, cy + 6, 0xFFFFA62B);
        g.fill(cx - 15, cy + 8, cx + 16, cy + 16, 0xFF2D7DD2);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int cx = width / 2;
        int cy = height / 2;

        drawVinyl(g, cx - 88, cy - 6);

        g.text(font, "JUKEIFY", cx + 34, cy - 78, 0xFFE7C27D, false);
        g.text(font, "NOW PLAYING", cx + 34, cy - 60, 0xFF1DB954, false);
        g.text(font, "Not connected to Spotify", cx + 34, cy - 43, 0xFFFFFFFF, false);
        g.text(font, "Album art will spin on the record", cx + 34, cy - 27, 0xFFAAAAAA, false);
        g.text(font, "Jukebox: " + jukeboxPos.toShortString(), cx + 34, cy - 9, 0xFF777777, false);
        g.text(font, "Shift + Right Click = vanilla jukebox", cx - 170, cy + 72, 0xFFB9A58A, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
