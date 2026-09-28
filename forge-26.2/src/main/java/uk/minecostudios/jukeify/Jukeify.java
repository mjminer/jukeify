package uk.minecostudios.jukeify;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import uk.minecostudios.jukeify.client.ClientEvents;

@Mod(Jukeify.MOD_ID)
public final class Jukeify {
    public static final String MOD_ID = "jukeify";

    public Jukeify(FMLJavaModLoadingContext context) {
        ClientEvents.register();
    }
}
