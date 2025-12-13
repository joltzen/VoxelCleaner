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
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VoxelCleaner implements ModInitializer {

	private static final int MAX_W = 64;
	private static final int MAX_H = 64;
	private static final int MAX_D = 64;

	private static final Map<UUID, Deque<UndoAction>> UNDO = new HashMap<>();
	private static final int MAX_UNDO_ACTIONS_PER_PLAYER = 10;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			dispatcher.register(
					literal("voxelcleaner")
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
							)
			);

			dispatcher.register(
					literal("voxelundo")
							.executes(ctx -> undo(ctx, 1))
							.then(argument("count", IntegerArgumentType.integer(1, 10))
									.executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "count")))
							)
			);
		});
	}

	private static int run(CommandContext<ServerCommandSource> ctx, Block shell, boolean force) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		int w = IntegerArgumentType.getInteger(ctx, "width");
		int h = IntegerArgumentType.getInteger(ctx, "height");
		int d = IntegerArgumentType.getInteger(ctx, "depth");

		Result r = hollow(player, w, h, d, shell, force);
		if (!r.snapshots.isEmpty()) pushUndo(player.getUuid(), r.dimensionId, r.snapshots);

		ctx.getSource().sendFeedback(() -> Text.literal("VoxelCleaner: " + r.changed), false);
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
						if (shell != null && shell != Blocks.AIR) {
							if (!force && isProtected(st)) continue;
							if (st.getBlock() != shell) {
								snaps.add(new Snapshot(p, st));
								world.setBlockState(p, shell.getDefaultState(), 3);
								changed++;
							}
						}
						continue;
					}

					if (st.isAir()) continue;
					if (!force && isProtected(st)) continue;

					snaps.add(new Snapshot(p, st));

					if (player.isCreative()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
					} else {
						world.breakBlock(p, true, player);
					}
					changed++;
				}
			}
		}

		return new Result(dim, changed, snaps);
	}

	private static boolean isProtected(BlockState s) {
		if (s.hasBlockEntity()) return true;
		return s.getBlock() == Blocks.SPAWNER;
	}

	private static void pushUndo(UUID playerId, String dimensionId, List<Snapshot> snapshots) {
		Deque<UndoAction> stack = UNDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
		stack.push(new UndoAction(dimensionId, snapshots));
		while (stack.size() > MAX_UNDO_ACTIONS_PER_PLAYER) stack.removeLast();
	}

	private static int undoOne(ServerPlayerEntity player) {
		Deque<UndoAction> stack = UNDO.get(player.getUuid());
		if (stack == null || stack.isEmpty()) return 0;

		var world = player.getEntityWorld();
		String currentDim = world.getRegistryKey().getValue().toString();

		UndoAction action = stack.pop();
		if (!action.dimensionId.equals(currentDim)) {
			stack.push(action);
			return 0;
		}

		int restored = 0;
		List<Snapshot> snaps = action.snapshots;
		for (int i = snaps.size() - 1; i >= 0; i--) {
			Snapshot s = snaps.get(i);
			world.setBlockState(s.pos, s.state, 3);
			restored++;
		}

		return restored;
	}

	private record Snapshot(BlockPos pos, BlockState state) {}
	private record UndoAction(String dimensionId, List<Snapshot> snapshots) {}
	private record Result(String dimensionId, int changed, List<Snapshot> snapshots) {}
}
