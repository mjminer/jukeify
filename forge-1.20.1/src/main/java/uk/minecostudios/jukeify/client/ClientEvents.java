package uk.minecostudios.jukeify.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.minecostudios.jukeify.Jukeify;

@Mod.EventBusSubscriber(
        modid = Jukeify.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() != Blocks.JUKEBOX) {
            return;
        }

        // Never interfere with a jukebox that already contains a vanilla record.
        if (state.hasProperty(JukeboxBlock.HAS_RECORD) && state.getValue(JukeboxBlock.HAS_RECORD)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        // Shift + click is the explicit vanilla bypass.
        if (minecraft.player.isShiftKeyDown()) {
            return;
        }

        // Inserting a normal music disc must remain vanilla behaviour.
        if (event.getItemStack().is(ItemTags.MUSIC_DISCS)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        minecraft.setScreen(new SpotifyJukeboxScreen(event.getPos()));
    }
}
