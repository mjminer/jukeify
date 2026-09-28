package uk.minecostudios.jukeify;

import net.minecraftforge.fml.common.Mod;

@Mod(Jukeify.MOD_ID)
public final class Jukeify {
    public static final String MOD_ID = "jukeify";

    public Jukeify() {
        // Keep the common entrypoint server-safe.
        // Spotify authentication and UI are client-side only.
    }
}
