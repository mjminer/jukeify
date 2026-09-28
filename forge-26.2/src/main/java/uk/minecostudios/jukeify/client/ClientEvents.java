package uk.minecostudios.jukeify.client;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import uk.minecostudios.jukeify.spotify.SpotifyWebPlayerBridge;

public final class ClientEvents {
    private ClientEvents() {}

    public static void register() {
        PlayerInteractEvent.RightClickBlock.BUS.addListener(
                (Predicate<PlayerInteractEvent.RightClickBlock>) ClientEvents::onRightClickBlock);
        TickEvent.ClientTickEvent.BUS.addListener(event -> {
            if (event.phase == TickEvent.Phase.END) {
                SpotifyWebPlayerBridge.onClientTick();
            }
        });
    }

    private static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return false;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() != Blocks.JUKEBOX) return false;

        if (state.hasProperty(JukeboxBlock.HAS_RECORD) && state.getValue(JukeboxBlock.HAS_RECORD)) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isShiftKeyDown()) return false;
        if (event.getItemStack().has(DataComponents.JUKEBOX_PLAYABLE)) return false;

        event.setUseBlock(Result.DENY);
        event.setUseItem(Result.DENY);
        event.setCancellationResult(InteractionResult.SUCCESS);

        var pos = event.getPos().immutable();
        minecraft.execute(() -> minecraft.gui.setScreen(new SpotifyJukeboxScreen(pos)));
        return true;
    }
}
