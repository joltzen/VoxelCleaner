package jason.voxelcleaner;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VoxelCleaner implements ModInitializer {

	private static final int MAX_W = 64;
	private static final int MAX_H = 64;
	private static final int MAX_D = 64;

	private static final Map<UUID, Deque<Action>> UNDO = new HashMap<>();
	private static final Map<UUID, Deque<Action>> REDO = new HashMap<>();

	private static final int MAX_UNDO_ACTIONS_PER_PLAYER = 10;
	private static final int MAX_HISTORY_LINES = 20;

	private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildVoxelCleaner =
					root -> root
							.then(argument("width", IntegerArgumentType.integer(1, MAX_W))
									.then(argument("height", IntegerArgumentType.integer(1, MAX_H))
											.then(argument("depth", IntegerArgumentType.integer(1, MAX_D))
													.executes(ctx -> run(ctx, null, false))
													.then(argument("force", BoolArgumentType.bool())
															.executes(ctx -> run(ctx, null, BoolArgumentType.getBool(ctx, "force")))
													)
													.then(argument("material", BlockStateArgumentType.blockState(registryAccess))
															.executes(ctx -> run(ctx, getBlock(ctx), false))
															.then(argument("force2", BoolArgumentType.bool())
																	.executes(ctx -> run(ctx, getBlock(ctx), BoolArgumentType.getBool(ctx, "force2")))
															)
													)
											)
									)
							);

			dispatcher.register(buildVoxelCleaner.apply(literal("voxelcleaner")));
			dispatcher.register(buildVoxelCleaner.apply(literal("vc")));

			UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildUndo =
					root -> root
							.executes(ctx -> undo(ctx, 1))
							.then(argument("count", IntegerArgumentType.integer(1, 10))
									.executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "count")))
							);

			dispatcher.register(buildUndo.apply(literal("voxelundo")));
			dispatcher.register(buildUndo.apply(literal("vcu")));

			UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildRedo =
					root -> root
							.executes(ctx -> redo(ctx, 1))
							.then(argument("count", IntegerArgumentType.integer(1, 10))
									.executes(ctx -> redo(ctx, IntegerArgumentType.getInteger(ctx, "count")))
							);

			dispatcher.register(buildRedo.apply(literal("voxelredo")));
			dispatcher.register(buildRedo.apply(literal("vcr")));

			UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildHistory =
					root -> root
							.executes(ctx -> history(ctx, 5))
							.then(argument("count", IntegerArgumentType.integer(1, MAX_HISTORY_LINES))
									.executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "count")))
							);

			dispatcher.register(buildHistory.apply(literal("voxelhistory")));
			dispatcher.register(buildHistory.apply(literal("vch")));
		});
	}

	private static int run(CommandContext<ServerCommandSource> ctx, Block shell, boolean force) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		int w = IntegerArgumentType.getInteger(ctx, "width");
		int h = IntegerArgumentType.getInteger(ctx, "height");
		int d = IntegerArgumentType.getInteger(ctx, "depth");

		Result r = hollow(player, w, h, d, shell, force);

		if (!r.action.snapshots.isEmpty()) {
			pushUndo(player.getUuid(), r.action);
			clearRedo(player.getUuid());
		}

		ctx.getSource().sendFeedback(() -> Text.literal("VoxelCleaner: " + r.action.changed), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int undo(CommandContext<ServerCommandSource> ctx, int count) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		int restoredTotal = 0;
		for (int i = 0; i < count; i++) {
			int restored = undoOne(player);
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

	private static int redo(CommandContext<ServerCommandSource> ctx, int count) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		int appliedTotal = 0;
		for (int i = 0; i < count; i++) {
			int applied = redoOne(player);
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

	private static int history(CommandContext<ServerCommandSource> ctx, int count) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		Deque<Action> stack = UNDO.get(player.getUuid());
		if (stack == null || stack.isEmpty()) {
			ctx.getSource().sendError(Text.literal("VoxelCleaner: keine History"));
			return 0;
		}

		int n = Math.min(count, Math.min(stack.size(), MAX_HISTORY_LINES));
		player.sendMessage(Text.literal("VoxelCleaner History (neueste zuerst) [" + n + "/" + stack.size() + "]"), false);

		int i = 1;
		for (Action a : stack) {
			if (i > n) break;
			String ts = TS_FMT.format(Instant.ofEpochMilli(a.epochMs));
			String shell = a.shellId == null ? "-" : a.shellId;
			player.sendMessage(Text.literal(
					"#" + i +
							" " + ts +
							" dim=" + a.dimensionId +
							" inner=" + a.iw + "x" + a.ih + "x" + a.id +
							" shell=" + shell +
							" force=" + a.force +
							" changed=" + a.changed
			), false);
			i++;
		}

		return Command.SINGLE_SUCCESS;
	}

	private static ServerPlayerEntity player(ServerCommandSource src) {
		try {
			return src.getPlayerOrThrow();
		} catch (CommandSyntaxException e) {
			src.sendError(Text.literal("Player only"));
			return null;
		}
	}

	private static Block getBlock(CommandContext<ServerCommandSource> ctx) {
		BlockStateArgument arg = BlockStateArgumentType.getBlockState(ctx, "material");
		return arg.getBlockState().getBlock();
	}

	private static Result hollow(ServerPlayerEntity player, int iw, int ih, int id, Block shell, boolean force) {
		var world = player.getEntityWorld();
		String dim = world.getRegistryKey().getValue().toString();
		long now = System.currentTimeMillis();

		int ow = iw + 2;
		int oh = ih + 2;
		int od = id + 2;

		Direction f = player.getHorizontalFacing();
		Direction s = f.rotateYClockwise();

		BlockPos base = player.getBlockPos().down().offset(f, 1);

		int minW = -(ow / 2);
		int maxW = minW + ow - 1;

		int changed = 0;
		List<Snapshot> snaps = new ArrayList<>();

		BlockState shellState = shell == null ? null : shell.getDefaultState();
		BlockState airState = Blocks.AIR.getDefaultState();

		for (int dz = 0; dz < od; dz++) {
			for (int dx = minW; dx <= maxW; dx++) {
				for (int dy = 0; dy < oh; dy++) {

					BlockPos p = base.offset(f, dz).offset(s, dx).up(dy);
					BlockState st = world.getBlockState(p);

					if (st.getBlock() == Blocks.BEDROCK) continue;

					boolean shellPos =
							dz == 0 || dz == od - 1 ||
									dx == minW || dx == maxW ||
									dy == 0 || dy == oh - 1;

					if (shellPos) {
						if (shellState != null && shell != Blocks.AIR) {
							if (!force && isProtected(st)) continue;
							if (st.getBlock() != shell) {
								snaps.add(new Snapshot(p, st, shellState));
								world.setBlockState(p, shellState, 3);
								changed++;
							}
						}
						continue;
					}

					if (st.isAir()) continue;
					if (!force && isProtected(st)) continue;

					snaps.add(new Snapshot(p, st, airState));

					if (player.isCreative()) {
						world.setBlockState(p, airState, 3);
					} else {
						world.breakBlock(p, true, player);
					}
					changed++;
				}
			}
		}

		String shellId = shell == null ? null : Registries.BLOCK.getId(shell).toString();
		Action action = new Action(dim, now, iw, ih, id, shellId, force, changed, snaps);
		return new Result(action);
	}

	private static boolean isProtected(BlockState s) {
		if (s.hasBlockEntity()) return true;
		return s.getBlock() == Blocks.SPAWNER;
	}

	private static void pushUndo(UUID playerId, Action action) {
		Deque<Action> stack = UNDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
		stack.push(action);
		while (stack.size() > MAX_UNDO_ACTIONS_PER_PLAYER) stack.removeLast();
	}

	private static void pushRedo(UUID playerId, Action action) {
		Deque<Action> stack = REDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
		stack.push(action);
		while (stack.size() > MAX_UNDO_ACTIONS_PER_PLAYER) stack.removeLast();
	}

	private static void clearRedo(UUID playerId) {
		Deque<Action> stack = REDO.get(playerId);
		if (stack != null) stack.clear();
	}

	private static int undoOne(ServerPlayerEntity player) {
		Deque<Action> stack = UNDO.get(player.getUuid());
		if (stack == null || stack.isEmpty()) return 0;

		var world = player.getEntityWorld();
		String currentDim = world.getRegistryKey().getValue().toString();

		Action action = stack.pop();
		if (!action.dimensionId.equals(currentDim)) {
			stack.push(action);
			return 0;
		}

		int restored = 0;
		List<Snapshot> snaps = action.snapshots;

		for (int i = snaps.size() - 1; i >= 0; i--) {
			Snapshot s = snaps.get(i);
			world.setBlockState(s.pos, s.before, 3);
			restored++;
		}

		pushRedo(player.getUuid(), action);
		return restored;
	}

	private static int redoOne(ServerPlayerEntity player) {
		Deque<Action> stack = REDO.get(player.getUuid());
		if (stack == null || stack.isEmpty()) return 0;

		var world = player.getEntityWorld();
		String currentDim = world.getRegistryKey().getValue().toString();

		Action action = stack.pop();
		if (!action.dimensionId.equals(currentDim)) {
			stack.push(action);
			return 0;
		}

		int applied = 0;
		List<Snapshot> snaps = action.snapshots;

		for (int i = snaps.size() - 1; i >= 0; i--) {
			Snapshot s = snaps.get(i);
			world.setBlockState(s.pos, s.after, 3);
			applied++;
		}

		pushUndo(player.getUuid(), action);
		return applied;
	}

	private record Snapshot(BlockPos pos, BlockState before, BlockState after) {}
	private record Action(String dimensionId, long epochMs, int iw, int ih, int id, String shellId, boolean force, int changed, List<Snapshot> snapshots) {}
	private record Result(Action action) {}
}
