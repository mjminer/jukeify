package uk.minecostudios.jukeify.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
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

        int centreX = width / 2;
        int centreY = height / 2;

        addRenderableWidget(
                Button.builder(Component.literal("Connect Spotify"), button -> {
                            if (minecraft != null) {
                                minecraft.setScreen(new SpotifySetupScreen(this));
                            }
                        })
                        .bounds(centreX + 34, centreY + 16, 136, 20)
                        .build()
        );

        addRenderableWidget(
                Button.builder(Component.literal("Close"), button -> onClose())
                        .bounds(centreX + 34, centreY + 42, 136, 20)
                        .build()
        );
    }

    private void drawVinyl(GuiGraphics graphics, int x, int y) {
        float angle = ((System.currentTimeMillis() - openedAt) / 24.0F) % 360.0F;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));

        int radius = 58;
        for (int yy = -radius; yy <= radius; yy++) {
            int xx = (int) Math.sqrt((radius * radius) - (yy * yy));
            graphics.fill(-xx, yy, xx + 1, yy + 1, 0xFF111111);
        }

        // A few subtle record markings so the spin is visible.
        graphics.fill(-45, -1, 46, 1, 0xFF282828);
        graphics.fill(-1, -45, 1, 46, 0xFF282828);
        graphics.fill(-34, -1, 35, 1, 0xFF202020);
        graphics.fill(-1, -34, 1, 35, 0xFF202020);

        // Temporary album-art centre. Real Spotify artwork replaces this in V0.3.
        graphics.fill(-19, -19, 20, 20, 0xFF1DB954);
        graphics.fill(-15, -15, 16, -6, 0xFF845EC2);
        graphics.fill(-15, -4, 16, 6, 0xFFFFA62B);
        graphics.fill(-15, 8, 16, 16, 0xFF2D7DD2);
        graphics.fill(-3, -3, 4, 4, 0xFF101010);

        graphics.pose().popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int centreX = width / 2;
        int centreY = height / 2;

        // Jukebox-inspired wood frame.
        graphics.fill(centreX - 194, centreY - 109, centreX + 194, centreY + 99, 0xD0180E08);
        graphics.fill(centreX - 188, centreY - 103, centreX + 188, centreY + 93, 0xFF68401F);
        graphics.fill(centreX - 181, centreY - 96, centreX + 181, centreY + 86, 0xFF2A170D);

        drawVinyl(graphics, centreX - 88, centreY - 6);

        graphics.drawString(font, "JUKEIFY", centreX + 34, centreY - 78, 0xFFE7C27D, false);
        graphics.drawString(font, "NOW PLAYING", centreX + 34, centreY - 60, 0xFF1DB954, false);
        graphics.drawString(font, "Not connected to Spotify", centreX + 34, centreY - 43, 0xFFFFFFFF, false);
        graphics.drawString(font, "Album art will spin on the record", centreX + 34, centreY - 27, 0xFFAAAAAA, false);
        graphics.drawString(font, "Jukebox: " + jukeboxPos.toShortString(), centreX + 34, centreY - 9, 0xFF777777, false);

        graphics.drawString(
                font,
                "Shift + Right Click = vanilla jukebox",
                centreX - 170,
                centreY + 72,
                0xFFB9A58A,
                false
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
