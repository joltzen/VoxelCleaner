/**
 * Defines and registers all Brigadier commands and connects them to the underlying logic.
 */
package jason.voxelcleaner.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import jason.voxelcleaner.config.VoxelConfig;
import jason.voxelcleaner.core.VoxelOperations;
import jason.voxelcleaner.history.HistoryService;
import jason.voxelcleaner.model.VoxelModels.Result;
import jason.voxelcleaner.util.CommandUtil;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.CommandDispatcher;

import java.util.function.UnaryOperator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class VoxelCommands {

    private static final VoxelOperations OPS = new VoxelOperations();
    private static final HistoryService HISTORY = new HistoryService();

    private VoxelCommands() {}

    public static void register(CommandManager.RegistrationEnvironment dispatcherEnv) {
        // not used
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {

        // voxelcleaner / vc
        UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildCleaner =
                root -> root
                        .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                        .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))

                                                .executes(ctx -> runClean(ctx, null, false, false))

                                                .then(argument("force", BoolArgumentType.bool())
                                                        .executes(ctx -> runClean(ctx, null, BoolArgumentType.getBool(ctx, "force"), false))
                                                        .then(argument("loot", BoolArgumentType.bool())
                                                                .executes(ctx -> runClean(ctx, null,
                                                                        BoolArgumentType.getBool(ctx, "force"),
                                                                        BoolArgumentType.getBool(ctx, "loot")))
                                                        )
                                                )

                                                .then(argument("loot", BoolArgumentType.bool())
                                                        .executes(ctx -> runClean(ctx, null, false, BoolArgumentType.getBool(ctx, "loot")))
                                                )

                                                .then(argument("material", BlockStateArgumentType.blockState(registryAccess))

                                                        .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), false, false))

                                                        .then(argument("force2", BoolArgumentType.bool())
                                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"),
                                                                        BoolArgumentType.getBool(ctx, "force2"), false))
                                                                .then(argument("loot2", BoolArgumentType.bool())
                                                                        .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"),
                                                                                BoolArgumentType.getBool(ctx, "force2"),
                                                                                BoolArgumentType.getBool(ctx, "loot2")))
                                                                )
                                                        )

                                                        .then(argument("loot2", BoolArgumentType.bool())
                                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"),
                                                                        false, BoolArgumentType.getBool(ctx, "loot2")))
                                                        )
                                                )
                                        )
                                )
                        );

        dispatcher.register(buildCleaner.apply(literal("voxelcleaner")));
        dispatcher.register(buildCleaner.apply(literal("vc")));

        // voxelroom / vr
        UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildRoom =
                root -> root
                        .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                        .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))
                                                .then(argument("walls", BlockStateArgumentType.blockState(registryAccess))
                                                        .then(argument("floor", BlockStateArgumentType.blockState(registryAccess))
                                                                .then(argument("ceiling", BlockStateArgumentType.blockState(registryAccess))

                                                                        .executes(ctx -> runRoom(ctx, false, false))

                                                                        .then(argument("force", BoolArgumentType.bool())
                                                                                .executes(ctx -> runRoom(ctx, BoolArgumentType.getBool(ctx, "force"), false))
                                                                                .then(argument("loot", BoolArgumentType.bool())
                                                                                        .executes(ctx -> runRoom(ctx,
                                                                                                BoolArgumentType.getBool(ctx, "force"),
                                                                                                BoolArgumentType.getBool(ctx, "loot")))
                                                                                )
                                                                        )

                                                                        .then(argument("loot", BoolArgumentType.bool())
                                                                                .executes(ctx -> runRoom(ctx, false, BoolArgumentType.getBool(ctx, "loot")))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        );

        dispatcher.register(buildRoom.apply(literal("voxelroom")));
        dispatcher.register(buildRoom.apply(literal("vr")));

        // undo / redo / history
        dispatcher.register(literal("voxelundo")
                .executes(ctx -> undo(ctx, 1))
                .then(argument("count", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

        dispatcher.register(literal("vcu")
                .executes(ctx -> undo(ctx, 1))
                .then(argument("count", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

        dispatcher.register(literal("voxelredo")
                .executes(ctx -> redo(ctx, 1))
                .then(argument("count", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> redo(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

        dispatcher.register(literal("vcr")
                .executes(ctx -> redo(ctx, 1))
                .then(argument("count", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> redo(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

        dispatcher.register(literal("voxelhistory")
                .executes(ctx -> history(ctx, 5))
                .then(argument("count", IntegerArgumentType.integer(1, VoxelConfig.MAX_HISTORY_LINES))
                        .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

        dispatcher.register(literal("vch")
                .executes(ctx -> history(ctx, 5))
                .then(argument("count", IntegerArgumentType.integer(1, VoxelConfig.MAX_HISTORY_LINES))
                        .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int runClean(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                Block shell, boolean force, boolean loot) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int w = IntegerArgumentType.getInteger(ctx, "width");
        int h = IntegerArgumentType.getInteger(ctx, "height");
        int d = IntegerArgumentType.getInteger(ctx, "depth");

        Result r = OPS.hollow(player, w, h, d, shell, force, loot);

        if (!r.action().snapshots().isEmpty()) {
            HISTORY.pushUndo(player.getUuid(), r.action());
            HISTORY.clearRedo(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Text.literal("VoxelCleaner: " + r.action().changed()), false);
        if (loot && !player.isCreative()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Loot: " + r.action().lootItems()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runRoom(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                               boolean force, boolean loot) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int w = IntegerArgumentType.getInteger(ctx, "width");
        int h = IntegerArgumentType.getInteger(ctx, "height");
        int d = IntegerArgumentType.getInteger(ctx, "depth");

        Block walls = CommandUtil.getBlock(ctx, "walls");
        Block floor = CommandUtil.getBlock(ctx, "floor");
        Block ceiling = CommandUtil.getBlock(ctx, "ceiling");

        if (walls == Blocks.AIR || floor == Blocks.AIR || ceiling == Blocks.AIR) {
            ctx.getSource().sendError(Text.literal("AIR ist für walls/floor/ceiling nicht erlaubt."));
            return 0;
        }

        Result r = OPS.room(player, w, h, d, walls, floor, ceiling, force, loot);

        if (!r.action().snapshots().isEmpty()) {
            HISTORY.pushUndo(player.getUuid(), r.action());
            HISTORY.clearRedo(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Text.literal("VoxelRoom: " + r.action().changed()), false);
        if (loot && !player.isCreative()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Loot: " + r.action().lootItems()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int undo(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, int count) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int restoredTotal = 0;
        for (int i = 0; i < count; i++) {
            int restored = HISTORY.undoOne(player);
            if (restored == 0) break;
            restoredTotal += restored;
        }

        if (restoredTotal == 0) {
            ctx.getSource().sendError(Text.literal("VoxelCleaner: nichts zum Undo"));
            return 0;
        }

        int finalRestoredTotal = restoredTotal;
        ctx.getSource().sendFeedback(() -> Text.literal("VoxelCleaner Undo: " + finalRestoredTotal), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int redo(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, int count) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int appliedTotal = 0;
        for (int i = 0; i < count; i++) {
            int applied = HISTORY.redoOne(player);
            if (applied == 0) break;
            appliedTotal += applied;
        }

        if (appliedTotal == 0) {
            ctx.getSource().sendError(Text.literal("VoxelCleaner: nichts zum Redo"));
            return 0;
        }

        int finalAppliedTotal = appliedTotal;
        ctx.getSource().sendFeedback(() -> Text.literal("VoxelCleaner Redo: " + finalAppliedTotal), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int history(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, int count) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        HISTORY.sendHistory(player, count);
        return Command.SINGLE_SUCCESS;
    }
}
