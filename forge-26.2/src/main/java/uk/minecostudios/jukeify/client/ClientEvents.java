package uk.minecostudios.jukeify.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class ClientEvents {
    private ClientEvents() {}

    public static void register() {
        PlayerInteractEvent.RightClickBlock.BUS.addListener(ClientEvents::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() != Blocks.JUKEBOX) return;

        if (state.hasProperty(JukeboxBlock.HAS_RECORD) && state.getValue(JukeboxBlock.HAS_RECORD)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isShiftKeyDown()) return;
        if (event.getItemStack().has(DataComponents.JUKEBOX_PLAYABLE)) return;

        event.setCancellationResult(InteractionResult.SUCCESS);

        var pos = event.getPos().immutable();
        minecraft.execute(() -> minecraft.gui.setScreen(new SpotifyJukeboxScreen(pos)));
    }
}
