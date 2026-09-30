package uk.minecostudios.jukeify.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import uk.minecostudios.jukeify.audio.LocalMusicLibrary;
import uk.minecostudios.jukeify.audio.NativeJukeboxAudio;

import java.nio.file.Path;
import java.util.List;

public final class LocalJukeboxScreen extends Screen {
    private static final int PAGE_SIZE = 7;

    private final Screen parent;
    private final BlockPos jukeboxPos;
    private int page;
    private List<Path> tracks = List.of();

    public LocalJukeboxScreen(Screen parent, BlockPos jukeboxPos) {
        super(Component.literal("Local Jukebox"));
        this.parent = parent;
        this.jukeboxPos = jukeboxPos;
    }

    @Override
    protected void init() {
        tracks = LocalMusicLibrary.scan();
        int maxPage = Math.max(0, (tracks.size() - 1) / PAGE_SIZE);
        page = Math.min(page, maxPage);

        int cx = width / 2;
        int top = height / 2 - 105;
        int start = page * PAGE_SIZE;
        int end = Math.min(tracks.size(), start + PAGE_SIZE);

        for (int i = start; i < end; i++) {
            Path track = tracks.get(i);
            String name = track.getFileName().toString();
            int row = i - start;
            addRenderableWidget(Button.builder(Component.literal(trim(name, 42)), b -> {
                NativeJukeboxAudio.play(track, jukeboxPos);
            }).bounds(cx - 160, top + 30 + row * 23, 320, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("< Page"), b -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(cx - 160, top + 195, 72, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Page >"), b -> {
            if ((page + 1) * PAGE_SIZE < tracks.size()) {
                page++;
                rebuildWidgets();
            }
        }).bounds(cx - 84, top + 195, 72, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Open Music Folder"), b -> LocalMusicLibrary.openFolder())
                .bounds(cx - 8, top + 195, 118, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> rebuildWidgets())
                .bounds(cx + 114, top + 195, 72, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Play / Pause"), b -> NativeJukeboxAudio.togglePause())
                .bounds(cx - 160, top + 220, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> NativeJukeboxAudio.stop())
                .bounds(cx - 56, top + 220, 68, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Vol -"), b ->
                NativeJukeboxAudio.setVolumePercent(NativeJukeboxAudio.getVolumePercent() - 10))
                .bounds(cx + 16, top + 220, 64, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Vol +"), b ->
                NativeJukeboxAudio.setVolumePercent(NativeJukeboxAudio.getVolumePercent() + 10))
                .bounds(cx + 84, top + 220, 64, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            if (minecraft != null) minecraft.gui.setScreen(parent);
        }).bounds(cx + 152, top + 220, 60, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        g.fill(cx - 180, cy - 125, cx + 220, cy + 145, 0xE0180E08);
        g.fill(cx - 174, cy - 119, cx + 214, cy + 139, 0xFF68401F);
        g.fill(cx - 168, cy - 113, cx + 208, cy + 133, 0xFF2A170D);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = height / 2 - 105;

        g.text(font, "LOCAL JUKEBOX - TRUE 3D AUDIO", cx - 160, top + 4, 0xFFE7C27D, false);
        if (tracks.isEmpty()) {
            g.text(font, "No .ogg or .wav files found.", cx - 160, top + 36, 0xFFFFFFFF, false);
            g.text(font, "Use Open Music Folder, add tracks, then press Refresh.", cx - 160, top + 52, 0xFFAAAAAA, false);
        }

        String playing = NativeJukeboxAudio.getTrackName();
        if (!playing.isBlank()) {
            g.text(font, "Now: " + trim(playing, 38), cx - 160, top + 178, 0xFF1DB954, false);
        } else {
            g.text(font, NativeJukeboxAudio.getStatus(), cx - 160, top + 178, 0xFFAAAAAA, false);
        }

        g.text(font, "Uses Minecraft/OpenAL positional audio at the jukebox block.",
                cx - 160, top + 246, 0xFFB9A58A, false);
        g.text(font, "Volume follows Master + Jukebox/Note Blocks sound settings.",
                cx - 160, top + 258, 0xFF888888, false);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
