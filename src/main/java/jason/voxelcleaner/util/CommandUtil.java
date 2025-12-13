/**
 * Provides helper methods for command-related tasks like resolving players and blocks.
 */

package jason.voxelcleaner.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class CommandUtil {
    private CommandUtil() {}

    public static ServerPlayerEntity player(ServerCommandSource src) {
        try {
            return src.getPlayerOrThrow();
        } catch (CommandSyntaxException e) {
            src.sendError(Text.literal("Player only"));
            return null;
        }
    }

    public static Block getBlock(CommandContext<ServerCommandSource> ctx, String argName) {
        BlockStateArgument arg = BlockStateArgumentType.getBlockState(ctx, argName);
        return arg.getBlockState().getBlock();
    }
}
