package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import uk.minecostudios.jukeify.spotify.SpotifyAuth;
import uk.minecostudios.jukeify.spotify.SpotifyConfig;
import uk.minecostudios.jukeify.spotify.SpotifyPlayback;
import uk.minecostudios.jukeify.spotify.SpotifyWebPlayerBridge;

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
        SpotifyAuth.restoreSessionAsync();
        SpotifyWebPlayerBridge.setActiveJukebox(jukeboxPos);
        SpotifyPlayback.refresh();

        int cx = width / 2;
        int cy = height / 2;

        addRenderableWidget(Button.builder(Component.literal("<<"), b -> SpotifyPlayback.previous())
                .bounds(cx + 34, cy + 6, 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Play / Pause"), b -> SpotifyPlayback.togglePlayPause())
                .bounds(cx + 78, cy + 6, 92, 20).build());

        addRenderableWidget(Button.builder(Component.literal(">>"), b -> SpotifyPlayback.next())
                .bounds(cx + 174, cy + 6, 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Vol -"), b -> {
            if (SpotifyWebPlayerBridge.isReady()) {
                SpotifyWebPlayerBridge.adjustBaseVolume(-0.10);
            } else {
                int volume = SpotifyPlayback.getState().volume();
                SpotifyPlayback.setVolume(volume < 0 ? 40 : volume - 10);
            }
        }).bounds(cx + 34, cy + 32, 64, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Vol +"), b -> {
            if (SpotifyWebPlayerBridge.isReady()) {
                SpotifyWebPlayerBridge.adjustBaseVolume(0.10);
            } else {
                int volume = SpotifyPlayback.getState().volume();
                SpotifyPlayback.setVolume(volume < 0 ? 60 : volume + 10);
            }
        }).bounds(cx + 102, cy + 32, 64, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Local Jukebox"), b -> {
            if (minecraft != null) minecraft.gui.setScreen(new LocalJukeboxScreen(this, jukeboxPos));
        }).bounds(cx + 170, cy + 32, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Spotify Player"), b -> {
            if (SpotifyAuth.isConnected()) SpotifyWebPlayerBridge.startForJukebox(jukeboxPos);
        }).bounds(cx + 34, cy + 58, 112, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Spotify Settings"), b -> {
            if (minecraft != null) minecraft.gui.setScreen(new SpotifySetupScreen(this));
        }).bounds(cx + 150, cy + 58, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 254, cy + 58, 70, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        SpotifyPlayback.refreshIfNeeded();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        g.fill(cx - 194, cy - 109, cx + 286, cy + 99, 0xD0180E08);
        g.fill(cx - 188, cy - 103, cx + 280, cy + 93, 0xFF68401F);
        g.fill(cx - 181, cy - 96, cx + 273, cy + 86, 0xFF2A170D);
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
        SpotifyPlayback.TrackState track = SpotifyPlayback.getState();

        drawVinyl(g, cx - 88, cy - 6);

        g.text(font, "JUKEIFY", cx + 34, cy - 82, 0xFFE7C27D, false);
        g.text(font, "NOW PLAYING", cx + 34, cy - 64, 0xFF1DB954, false);

        if (track.hasTrack()) {
            g.text(font, trim(track.title(), 35), cx + 34, cy - 47, 0xFFFFFFFF, false);
            g.text(font, trim(track.artist(), 35), cx + 34, cy - 32, 0xFFBBBBBB, false);
            if (!track.album().isBlank()) {
                g.text(font, trim(track.album(), 35), cx + 34, cy - 17, 0xFF888888, false);
            }

            String device = SpotifyWebPlayerBridge.isReady()
                    ? "Jukeify Jukebox"
                    : (track.deviceName().isBlank() ? "Spotify device" : track.deviceName());
            String volume = SpotifyWebPlayerBridge.isReady()
                    ? "  Vol " + SpotifyWebPlayerBridge.getEffectiveVolumePercent() + "%"
                    : (track.volume() < 0 ? "" : "  Vol " + track.volume() + "%");
            g.text(font, (track.playing() ? "Playing - " : "Paused - ") + trim(device, 22) + volume,
                    cx + 34, cy - 2, track.playing() ? 0xFF1DB954 : 0xFFE7C27D, false);

            if (SpotifyWebPlayerBridge.isReady()) {
                int dist = (int)Math.round(SpotifyWebPlayerBridge.getDistance());
                g.text(font, "Jukebox audio range: " + dist + "m / 32m", cx + 34, cy + 60, 0xFFAAAAAA, false);
            } else if (SpotifyAuth.isConnected()) {
                g.text(font, trim(SpotifyWebPlayerBridge.getStatusMessage(), 42), cx + 34, cy + 60, 0xFFE7C27D, false);
            }
        } else {
            String message;
            if (SpotifyConfig.loadClientId().isBlank()) {
                message = "First-time Spotify setup required";
            } else if (!SpotifyAuth.isConnected()) {
                message = SpotifyAuth.getStatusMessage();
            } else {
                message = track.message();
            }
            g.text(font, trim(message, 42), cx + 34, cy - 43, 0xFFFFFFFF, false);
        }

        g.text(font, "Jukebox: " + jukeboxPos.toShortString(), cx - 170, cy + 72, 0xFF777777, false);
        g.text(font, "Local Jukebox = real positional sound from this block", cx - 170, cy + 84, 0xFFB9A58A, false);
        g.text(font, "Spotify Player = Spotify Connect browser player", cx + 34, cy + 84, 0xFF777777, false);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
