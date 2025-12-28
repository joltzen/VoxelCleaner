/**
 * Defines and registers all Brigadier commands and connects them to the underlying logic.
 */
package jason.voxelcleaner.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import jason.voxelcleaner.config.VoxelConfig;
import jason.voxelcleaner.core.VoxelOperations;
import jason.voxelcleaner.core.PreviewService;
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

import java.util.function.UnaryOperator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class VoxelCommands {

    private static final VoxelOperations OPS = new VoxelOperations();
    private static final PreviewService PREVIEW = new PreviewService();
    private static final HistoryService HISTORY = new HistoryService();

    private VoxelCommands() {}

    public static void register(CommandManager.RegistrationEnvironment dispatcherEnv) {
        // not used
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {

        // -----------------------------------------------------------------
        // voxelcleaner / vc
        // Syntax: /vc <w> <h> <d> [loot|drops] [force|override]
        //         /vc <w> <h> <d> <material> [loot|drops] [force|override]
        // Extra:  /vc help
        //         /vc undo [count]
        //         /vc redo [count]
        //         /vc history [count]
        // -----------------------------------------------------------------

        UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildCleaner =
                root -> root
                        .then(literal("help").executes(VoxelCommands::help))
                        .then(literal("undo")
                                .executes(ctx -> undo(ctx, 1))
                                .then(argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(literal("redo")
                                .executes(ctx -> redo(ctx, 1))
                                .then(argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> redo(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(literal("history")
                                .executes(ctx -> history(ctx, 5))
                                .then(argument("count", IntegerArgumentType.integer(1, VoxelConfig.MAX_HISTORY_LINES))
                                        .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "count")))))


                        // -----------------------------------------------------------------
                        // replace
                        // Syntax: /vc replace <w> <h> <d> <from> <to> [shell|inside] [chance <1..100>] [force|override]
                        // -----------------------------------------------------------------
                        .then(literal("replace")
                                .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                        .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                                .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))
                                                        .then(argument("from", BlockStateArgumentType.blockState(registryAccess))
                                                                .then(argument("to", BlockStateArgumentType.blockState(registryAccess))
                                                                        .executes(ctx -> runReplace(ctx, false, false, 100, false))
                                                                        .then(literal("shell")
                                                                                .executes(ctx -> runReplace(ctx, true, false, 100, false))
                                                                                .then(literal("force").executes(ctx -> runReplace(ctx, true, false, 100, true)))
                                                                                .then(literal("override").executes(ctx -> runReplace(ctx, true, false, 100, true)))
                                                                                .then(literal("chance")
                                                                                        .then(argument("percent", IntegerArgumentType.integer(1, 100))
                                                                                                .executes(ctx -> runReplace(ctx, true, false, IntegerArgumentType.getInteger(ctx, "percent"), false))
                                                                                                .then(literal("force").executes(ctx -> runReplace(ctx, true, false, IntegerArgumentType.getInteger(ctx, "percent"), true)))
                                                                                                .then(literal("override").executes(ctx -> runReplace(ctx, true, false, IntegerArgumentType.getInteger(ctx, "percent"), true))))))
                                                                        .then(literal("inside")
                                                                                .executes(ctx -> runReplace(ctx, false, true, 100, false))
                                                                                .then(literal("force").executes(ctx -> runReplace(ctx, false, true, 100, true)))
                                                                                .then(literal("override").executes(ctx -> runReplace(ctx, false, true, 100, true)))
                                                                                .then(literal("chance")
                                                                                        .then(argument("percent", IntegerArgumentType.integer(1, 100))
                                                                                                .executes(ctx -> runReplace(ctx, false, true, IntegerArgumentType.getInteger(ctx, "percent"), false))
                                                                                                .then(literal("force").executes(ctx -> runReplace(ctx, false, true, IntegerArgumentType.getInteger(ctx, "percent"), true)))
                                                                                                .then(literal("override").executes(ctx -> runReplace(ctx, false, true, IntegerArgumentType.getInteger(ctx, "percent"), true))))))
                                                                        .then(literal("chance")
                                                                                .then(argument("percent", IntegerArgumentType.integer(1, 100))
                                                                                        .executes(ctx -> runReplace(ctx, false, false, IntegerArgumentType.getInteger(ctx, "percent"), false))
                                                                                        .then(literal("force").executes(ctx -> runReplace(ctx, false, false, IntegerArgumentType.getInteger(ctx, "percent"), true)))
                                                                                        .then(literal("override").executes(ctx -> runReplace(ctx, false, false, IntegerArgumentType.getInteger(ctx, "percent"), true)))))
                                                                        .then(literal("force").executes(ctx -> runReplace(ctx, false, false, 100, true)))
                                                                        .then(literal("override").executes(ctx -> runReplace(ctx, false, false, 100, true)))
                                                                ))))))

        // -----------------------------------------------------------------
        // shapes
        // Syntax: /vc shape sphere <radius> <material> [hollow] [force|override]
        //         /vc shape cylinder <radius> <height> <material> [hollow] [force|override]
        //         /vc shape pyramid <base> <height> <material> [hollow] [force|override]
        // -----------------------------------------------------------------
                        .then(literal("shape")
                .then(literal("sphere")
                        .then(argument("radius", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                .then(argument("material", BlockStateArgumentType.blockState(registryAccess))
                                        .executes(ctx -> runShapeSphere(ctx, false, false))
                                        .then(literal("hollow").executes(ctx -> runShapeSphere(ctx, true, false))
                                                .then(literal("force").executes(ctx -> runShapeSphere(ctx, true, true)))
                                                .then(literal("override").executes(ctx -> runShapeSphere(ctx, true, true))))
                                        .then(literal("force").executes(ctx -> runShapeSphere(ctx, false, true)))
                                        .then(literal("override").executes(ctx -> runShapeSphere(ctx, false, true)))
                                )))
                .then(literal("cylinder")
                        .then(argument("radius", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                        .then(argument("material", BlockStateArgumentType.blockState(registryAccess))
                                                .executes(ctx -> runShapeCylinder(ctx, false, false))
                                                .then(literal("hollow").executes(ctx -> runShapeCylinder(ctx, true, false))
                                                        .then(literal("force").executes(ctx -> runShapeCylinder(ctx, true, true)))
                                                        .then(literal("override").executes(ctx -> runShapeCylinder(ctx, true, true))))
                                                .then(literal("force").executes(ctx -> runShapeCylinder(ctx, false, true)))
                                                .then(literal("override").executes(ctx -> runShapeCylinder(ctx, false, true)))
                                        ))))
                .then(literal("pyramid")
                        .then(argument("base", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                        .then(argument("material", BlockStateArgumentType.blockState(registryAccess))
                                                .executes(ctx -> runShapePyramid(ctx, false, false))
                                                .then(literal("hollow").executes(ctx -> runShapePyramid(ctx, true, false))
                                                        .then(literal("force").executes(ctx -> runShapePyramid(ctx, true, true)))
                                                        .then(literal("override").executes(ctx -> runShapePyramid(ctx, true, true))))
                                                .then(literal("force").executes(ctx -> runShapePyramid(ctx, false, true)))
                                                .then(literal("override").executes(ctx -> runShapePyramid(ctx, false, true)))
                                        ))))
        )

                // -----------------------------------------------------------------
                // preview (particles)
                // Syntax: /vc preview clean|room|replace <...>
                //         /vc preview shape sphere|cylinder|pyramid <...>
                // -----------------------------------------------------------------
                .then(literal("preview")
                        .then(literal("clean")
                                .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                        .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                                .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))
                                                        .executes(ctx -> runPreviewBox(ctx, true))))))
                        .then(literal("room")
                                .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                        .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                                .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))
                                                        .executes(ctx -> runPreviewBox(ctx, true))))))
                        .then(literal("replace")
                                .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                        .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                                .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))
                                                        .executes(ctx -> runPreviewBox(ctx, false))))))
                        .then(literal("shape")
                                .then(literal("sphere")
                                        .then(argument("radius", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                                .executes(ctx -> runPreviewSphere(ctx))))
                                .then(literal("cylinder")
                                        .then(argument("radius", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                                        .executes(ctx -> runPreviewCylinder(ctx)))))
                                .then(literal("pyramid")
                                        .then(argument("base", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                                        .executes(ctx -> runPreviewPyramid(ctx)))))
                        )
                )


                // main operation: clean/hollow
                .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                        .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))

                                        .executes(ctx -> runClean(ctx, null, false, false))

                                        // options: [loot|drops] [force|override]
                                        .then(literal("loot")
                                                .executes(ctx -> runClean(ctx, null, false, true))
                                                .then(literal("force")
                                                        .executes(ctx -> runClean(ctx, null, true, true)))
                                                .then(literal("override")
                                                        .executes(ctx -> runClean(ctx, null, true, true))))
                                        .then(literal("drops")
                                                .executes(ctx -> runClean(ctx, null, false, true))
                                                .then(literal("force")
                                                        .executes(ctx -> runClean(ctx, null, true, true)))
                                                .then(literal("override")
                                                        .executes(ctx -> runClean(ctx, null, true, true))))

                                        // allow force-only (still shows loot first in tab completion)
                                        .then(literal("force")
                                                .executes(ctx -> runClean(ctx, null, true, false)))
                                        .then(literal("override")
                                                .executes(ctx -> runClean(ctx, null, true, false)))

                                        // material variant
                                        .then(argument("material", BlockStateArgumentType.blockState(registryAccess))

                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), false, false))

                                                .then(literal("loot")
                                                        .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), false, true))
                                                        .then(literal("force")
                                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), true, true)))
                                                        .then(literal("override")
                                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), true, true))))
                                                .then(literal("drops")
                                                        .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), false, true))
                                                        .then(literal("force")
                                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), true, true)))
                                                        .then(literal("override")
                                                                .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), true, true))))

                                                .then(literal("force")
                                                        .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), true, false)))
                                                .then(literal("override")
                                                        .executes(ctx -> runClean(ctx, CommandUtil.getBlock(ctx, "material"), true, false)))
                                        )
                                )
                        )
                );

        dispatcher.register(buildCleaner.apply(literal("voxelcleaner")));
        dispatcher.register(buildCleaner.apply(literal("vc")));

        // -----------------------------------------------------------------
        // voxelroom / vr
        // Syntax: /vr <w> <h> <d> <walls> <floor> <ceiling> [loot|drops] [force|override]
        // -----------------------------------------------------------------

        UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildRoom =
                root -> root
                        .then(argument("width", IntegerArgumentType.integer(1, VoxelConfig.MAX_W))
                                .then(argument("height", IntegerArgumentType.integer(1, VoxelConfig.MAX_H))
                                        .then(argument("depth", IntegerArgumentType.integer(1, VoxelConfig.MAX_D))
                                                .then(argument("walls", BlockStateArgumentType.blockState(registryAccess))
                                                        .then(argument("floor", BlockStateArgumentType.blockState(registryAccess))
                                                                .then(argument("ceiling", BlockStateArgumentType.blockState(registryAccess))

                                                                        .executes(ctx -> runRoom(ctx, false, false))

                                                                        // options: [loot|drops] [force|override]
                                                                        .then(literal("loot")
                                                                                .executes(ctx -> runRoom(ctx, false, true))
                                                                                .then(literal("force")
                                                                                        .executes(ctx -> runRoom(ctx, true, true)))
                                                                                .then(literal("override")
                                                                                        .executes(ctx -> runRoom(ctx, true, true))))
                                                                        .then(literal("drops")
                                                                                .executes(ctx -> runRoom(ctx, false, true))
                                                                                .then(literal("force")
                                                                                        .executes(ctx -> runRoom(ctx, true, true)))
                                                                                .then(literal("override")
                                                                                        .executes(ctx -> runRoom(ctx, true, true))))

                                                                        .then(literal("force")
                                                                                .executes(ctx -> runRoom(ctx, true, false)))
                                                                        .then(literal("override")
                                                                                .executes(ctx -> runRoom(ctx, true, false)))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        );

        dispatcher.register(buildRoom.apply(literal("voxelroom")));
        dispatcher.register(buildRoom.apply(literal("vr")));

        // -----------------------------------------------------------------
        // Keep existing standalone aliases (backward compatible)
        // -----------------------------------------------------------------

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

    private static int help(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        player.sendMessage(Text.literal("VoxelCleaner Commands:"), false);
        player.sendMessage(Text.literal("/vc <w> <h> <d> [loot|drops] [force|override]"), false);
        player.sendMessage(Text.literal("  Beispiele: /vc 9 5 9"), false);
        player.sendMessage(Text.literal("            /vc 9 5 9 loot"), false);
        player.sendMessage(Text.literal("            /vc 9 5 9 loot force"), false);

        player.sendMessage(Text.literal("/vc <w> <h> <d> <material> [loot|drops] [force|override]"), false);
        player.sendMessage(Text.literal("  Beispiel:  /vc 9 5 9 minecraft:stone loot"), false);

        player.sendMessage(Text.literal("/vr <w> <h> <d> <walls> <floor> <ceiling> [loot|drops] [force|override]"), false);
        player.sendMessage(Text.literal("  Beispiel:  /vr 9 5 9 minecraft:stone minecraft:oak_planks minecraft:oak_planks"), false);

        player.sendMessage(Text.literal("Undo/Redo/History:"), false);
        player.sendMessage(Text.literal("/vc undo [count]   (z.B. /vc undo 3)"), false);
        player.sendMessage(Text.literal("/vc redo [count]"), false);
        player.sendMessage(Text.literal("/vc history [count]"), false);

        if (VoxelConfig.PERSIST_HISTORY) {
            player.sendMessage(Text.literal("Hinweis: Undo/Redo ist persistent (über Server-Neustart hinweg)."), false);
        }


        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("/vc replace <w> <h> <d> <from> <to> [shell|inside] [chance <1..100>] [force|override]"), false);
        player.sendMessage(Text.literal("  Beispiel:  /vc replace 21 9 21 minecraft:stone minecraft:andesite chance 35"), false);

        player.sendMessage(Text.literal("/vc shape sphere <radius> <material> [hollow] [force|override]"), false);
        player.sendMessage(Text.literal("/vc shape cylinder <radius> <height> <material> [hollow] [force|override]"), false);
        player.sendMessage(Text.literal("/vc shape pyramid <base> <height> <material> [hollow] [force|override]"), false);

        player.sendMessage(Text.literal("/vc preview clean|room|replace <w> <h> <d>"), false);
        player.sendMessage(Text.literal("/vc preview shape sphere <radius>"), false);

        return Command.SINGLE_SUCCESS;
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

    private static int runReplace(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                  boolean shellOnly, boolean insideOnly, int chancePercent, boolean force) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int w = IntegerArgumentType.getInteger(ctx, "width");
        int h = IntegerArgumentType.getInteger(ctx, "height");
        int d = IntegerArgumentType.getInteger(ctx, "depth");

        Block from = CommandUtil.getBlock(ctx, "from");
        Block to = CommandUtil.getBlock(ctx, "to");

        Result r = OPS.replace(player, w, h, d, from, to, force, shellOnly, insideOnly, chancePercent);

        if (!r.action().snapshots().isEmpty()) {
            HISTORY.pushUndo(player.getUuid(), r.action());
            HISTORY.clearRedo(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Text.literal("VoxelReplace: " + r.action().changed()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runShapeSphere(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                      boolean hollow, boolean force) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        Block material = CommandUtil.getBlock(ctx, "material");

        Result r = OPS.shapeSphere(player, radius, material, hollow, force);

        if (!r.action().snapshots().isEmpty()) {
            HISTORY.pushUndo(player.getUuid(), r.action());
            HISTORY.clearRedo(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Text.literal("VoxelShape Sphere: " + r.action().changed()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runShapeCylinder(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                        boolean hollow, boolean force) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        Block material = CommandUtil.getBlock(ctx, "material");

        Result r = OPS.shapeCylinder(player, radius, height, material, hollow, force);

        if (!r.action().snapshots().isEmpty()) {
            HISTORY.pushUndo(player.getUuid(), r.action());
            HISTORY.clearRedo(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Text.literal("VoxelShape Cylinder: " + r.action().changed()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runShapePyramid(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                       boolean hollow, boolean force) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int base = IntegerArgumentType.getInteger(ctx, "base");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        Block material = CommandUtil.getBlock(ctx, "material");

        Result r = OPS.shapePyramid(player, base, height, material, hollow, force);

        if (!r.action().snapshots().isEmpty()) {
            HISTORY.pushUndo(player.getUuid(), r.action());
            HISTORY.clearRedo(player.getUuid());
        }

        ctx.getSource().sendFeedback(() -> Text.literal("VoxelShape Pyramid: " + r.action().changed()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runPreviewBox(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, boolean addShellPadding) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int w = IntegerArgumentType.getInteger(ctx, "width");
        int h = IntegerArgumentType.getInteger(ctx, "height");
        int d = IntegerArgumentType.getInteger(ctx, "depth");

        PREVIEW.previewBox(player, w, h, d, addShellPadding);
        ctx.getSource().sendFeedback(() -> Text.literal("VoxelPreview: OK"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runPreviewSphere(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        PREVIEW.previewSphere(player, radius);
        ctx.getSource().sendFeedback(() -> Text.literal("VoxelPreview: OK"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runPreviewCylinder(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        PREVIEW.previewCylinder(player, radius, height);
        ctx.getSource().sendFeedback(() -> Text.literal("VoxelPreview: OK"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int runPreviewPyramid(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = CommandUtil.player(ctx.getSource());
        if (player == null) return 0;

        int base = IntegerArgumentType.getInteger(ctx, "base");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        PREVIEW.previewPyramid(player, base, height);
        ctx.getSource().sendFeedback(() -> Text.literal("VoxelPreview: OK"), false);
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
