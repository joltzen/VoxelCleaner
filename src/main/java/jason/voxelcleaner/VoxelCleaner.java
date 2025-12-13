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
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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

	private static final int MAX_ACTIONS_PER_PLAYER = 10;
	private static final int MAX_HISTORY_LINES = 20;

	private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildCleaner =
					root -> root
							.then(argument("width", IntegerArgumentType.integer(1, MAX_W))
									.then(argument("height", IntegerArgumentType.integer(1, MAX_H))
											.then(argument("depth", IntegerArgumentType.integer(1, MAX_D))

													.executes(ctx -> runClean(ctx, null, false, false))

													.then(argument("force", BoolArgumentType.bool())
															.executes(ctx -> runClean(ctx, null, BoolArgumentType.getBool(ctx, "force"), false))
															.then(argument("loot", BoolArgumentType.bool())
																	.executes(ctx -> runClean(ctx, null, BoolArgumentType.getBool(ctx, "force"), BoolArgumentType.getBool(ctx, "loot")))
															)
													)

													.then(argument("loot", BoolArgumentType.bool())
															.executes(ctx -> runClean(ctx, null, false, BoolArgumentType.getBool(ctx, "loot")))
													)

													.then(argument("material", BlockStateArgumentType.blockState(registryAccess))

															.executes(ctx -> runClean(ctx, getBlock(ctx, "material"), false, false))

															.then(argument("force2", BoolArgumentType.bool())
																	.executes(ctx -> runClean(ctx, getBlock(ctx, "material"), BoolArgumentType.getBool(ctx, "force2"), false))
																	.then(argument("loot2", BoolArgumentType.bool())
																			.executes(ctx -> runClean(ctx, getBlock(ctx, "material"), BoolArgumentType.getBool(ctx, "force2"), BoolArgumentType.getBool(ctx, "loot2")))
																	)
															)

															.then(argument("loot2", BoolArgumentType.bool())
																	.executes(ctx -> runClean(ctx, getBlock(ctx, "material"), false, BoolArgumentType.getBool(ctx, "loot2")))
															)
													)
											)
									)
							);

			dispatcher.register(buildCleaner.apply(literal("voxelcleaner")));
			dispatcher.register(buildCleaner.apply(literal("vc")));

			UnaryOperator<com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>> buildRoom =
					root -> root
							.then(argument("width", IntegerArgumentType.integer(1, MAX_W))
									.then(argument("height", IntegerArgumentType.integer(1, MAX_H))
											.then(argument("depth", IntegerArgumentType.integer(1, MAX_D))
													.then(argument("walls", BlockStateArgumentType.blockState(registryAccess))
															.then(argument("floor", BlockStateArgumentType.blockState(registryAccess))
																	.then(argument("ceiling", BlockStateArgumentType.blockState(registryAccess))

																			.executes(ctx -> runRoom(ctx, false, false))

																			.then(argument("force", BoolArgumentType.bool())
																					.executes(ctx -> runRoom(ctx, BoolArgumentType.getBool(ctx, "force"), false))
																					.then(argument("loot", BoolArgumentType.bool())
																							.executes(ctx -> runRoom(ctx, BoolArgumentType.getBool(ctx, "force"), BoolArgumentType.getBool(ctx, "loot")))
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

	private static int runClean(CommandContext<ServerCommandSource> ctx, Block shell, boolean force, boolean loot) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		int w = IntegerArgumentType.getInteger(ctx, "width");
		int h = IntegerArgumentType.getInteger(ctx, "height");
		int d = IntegerArgumentType.getInteger(ctx, "depth");

		Result r = hollow(player, w, h, d, shell, force, loot);

		if (!r.action.snapshots.isEmpty()) {
			pushUndo(player.getUuid(), r.action);
			clearRedo(player.getUuid());
		}

		ctx.getSource().sendFeedback(() -> Text.literal("VoxelCleaner: " + r.action.changed), false);
		if (loot && !player.isCreative()) {
			ctx.getSource().sendFeedback(() -> Text.literal("Loot: " + r.action.lootItems), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int runRoom(CommandContext<ServerCommandSource> ctx, boolean force, boolean loot) {
		ServerPlayerEntity player = player(ctx.getSource());
		if (player == null) return 0;

		int w = IntegerArgumentType.getInteger(ctx, "width");
		int h = IntegerArgumentType.getInteger(ctx, "height");
		int d = IntegerArgumentType.getInteger(ctx, "depth");

		Block walls = getBlock(ctx, "walls");
		Block floor = getBlock(ctx, "floor");
		Block ceiling = getBlock(ctx, "ceiling");

		if (walls == Blocks.AIR || floor == Blocks.AIR || ceiling == Blocks.AIR) {
			ctx.getSource().sendError(Text.literal("AIR ist für walls/floor/ceiling nicht erlaubt."));
			return 0;
		}

		Result r = room(player, w, h, d, walls, floor, ceiling, force, loot);

		if (!r.action.snapshots.isEmpty()) {
			pushUndo(player.getUuid(), r.action);
			clearRedo(player.getUuid());
		}

		ctx.getSource().sendFeedback(() -> Text.literal("VoxelRoom: " + r.action.changed), false);
		if (loot && !player.isCreative()) {
			ctx.getSource().sendFeedback(() -> Text.literal("Loot: " + r.action.lootItems), false);
		}
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
							" loot=" + a.loot +
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

	private static Block getBlock(CommandContext<ServerCommandSource> ctx, String argName) {
		BlockStateArgument arg = BlockStateArgumentType.getBlockState(ctx, argName);
		return arg.getBlockState().getBlock();
	}

	private static Result room(ServerPlayerEntity player, int iw, int ih, int id, Block walls, Block floor, Block ceiling, boolean force, boolean loot) {
		World w0 = player.getEntityWorld();
		if (!(w0 instanceof ServerWorld world)) return emptyResult(iw, ih, id, "room", force, loot);

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
		int lootItems = 0;
		List<ItemStack> collected = loot && !player.isCreative() ? new ArrayList<>() : null;

		List<Snapshot> snaps = new ArrayList<>();

		BlockState wallsState = walls.getDefaultState();
		BlockState floorState = floor.getDefaultState();
		BlockState ceilState = ceiling.getDefaultState();
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
						if (!force && isProtected(st)) continue;

						BlockState targetState = (dy == 0) ? floorState : (dy == oh - 1) ? ceilState : wallsState;

						if (!st.equals(targetState)) {
							snaps.add(new Snapshot(p, st, targetState));
							world.setBlockState(p, targetState, 3);
							changed++;
						}
						continue;
					}

					if (st.isAir()) continue;
					if (!force && isProtected(st)) continue;

					snaps.add(new Snapshot(p, st, airState));

					if (player.isCreative() || !loot) {
						if (player.isCreative()) world.setBlockState(p, airState, 3);
						else world.breakBlock(p, true, player);
					} else {
						lootItems += breakAndCollect(world, player, p, st, collected);
					}

					changed++;
				}
			}
		}

		if (loot && !player.isCreative() && collected != null && !collected.isEmpty()) {
			int placed = placeLootChestsAndFill(world, base, f, s, ow, oh, od, minW, maxW, collected, player.getHorizontalFacing().getOpposite());
			if (placed == 0) {
				BlockPos dropAt = roomCenterPos(base, f, s, ow, od, minW);
				dropStacks(world, dropAt, collected);
			}
		}

		String shellId = "room:walls=" + Registries.BLOCK.getId(walls) + ",floor=" + Registries.BLOCK.getId(floor) + ",ceiling=" + Registries.BLOCK.getId(ceiling);
		Action action = new Action(dim, now, iw, ih, id, shellId, force, loot, changed, lootItems, snaps);
		return new Result(action);
	}

	private static Result hollow(ServerPlayerEntity player, int iw, int ih, int id, Block shell, boolean force, boolean loot) {
		World w0 = player.getEntityWorld();
		if (!(w0 instanceof ServerWorld world)) return emptyResult(iw, ih, id, null, force, loot);

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
		int lootItems = 0;
		List<ItemStack> collected = loot && !player.isCreative() ? new ArrayList<>() : null;

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
							if (!st.equals(shellState)) {
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

					if (player.isCreative() || !loot) {
						if (player.isCreative()) world.setBlockState(p, airState, 3);
						else world.breakBlock(p, true, player);
					} else {
						lootItems += breakAndCollect(world, player, p, st, collected);
					}

					changed++;
				}
			}
		}

		if (loot && !player.isCreative() && collected != null && !collected.isEmpty()) {
			int placed = placeLootChestsAndFill(world, base, f, s, ow, oh, od, minW, maxW, collected, player.getHorizontalFacing().getOpposite());
			if (placed == 0) {
				BlockPos dropAt = roomCenterPos(base, f, s, ow, od, minW);
				dropStacks(world, dropAt, collected);
			}
		}

		String shellId = shell == null ? null : Registries.BLOCK.getId(shell).toString();
		Action action = new Action(dim, now, iw, ih, id, shellId, force, loot, changed, lootItems, snaps);
		return new Result(action);
	}

	private static int breakAndCollect(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, List<ItemStack> out) {
		BlockEntity be = world.getBlockEntity(pos);
		ItemStack tool = player.getMainHandStack();

		List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, player, tool);

		world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
		if (be != null) world.removeBlockEntity(pos);

		int c = 0;
		for (ItemStack st : drops) {
			if (st == null || st.isEmpty()) continue;
			out.add(st.copy());
			c += st.getCount();
		}
		return c;
	}

	private static int placeLootChestsAndFill(ServerWorld world,
											  BlockPos base,
											  Direction facing,
											  Direction sideways,
											  int outerWidth,
											  int outerHeight,
											  int outerDepth,
											  int minW,
											  int maxW,
											  List<ItemStack> stacks,
											  Direction chestFacing) {

		if (stacks.isEmpty()) return 0;

		int interiorY = base.getY() + 1;
		int minDepth = 1;
		int maxDepth = outerDepth - 2;
		int minWidth = minW + 1;
		int maxWidth = maxW - 1;

		List<BlockPos> chestSlots = new ArrayList<>();

		int centerDepth = (outerDepth - 1) / 2;
		int centerWidth = minW + (outerWidth - 1) / 2;

		List<Integer> depthOrder = new ArrayList<>();
		depthOrder.add(centerDepth);
		for (int step = 1; step <= Math.max(centerDepth - minDepth, maxDepth - centerDepth); step++) {
			int a = centerDepth - step;
			int b = centerDepth + step;
			if (a >= minDepth) depthOrder.add(a);
			if (b <= maxDepth) depthOrder.add(b);
		}

		List<Integer> widthOrder = new ArrayList<>();
		widthOrder.add(centerWidth);
		for (int step = 1; step <= Math.max(centerWidth - minWidth, maxWidth - centerWidth); step++) {
			int a = centerWidth - step;
			int b = centerWidth + step;
			if (a >= minWidth) widthOrder.add(a);
			if (b <= maxWidth) widthOrder.add(b);
		}

		for (int dz : depthOrder) {
			for (int w : widthOrder) {
				int w2 = w + 1;
				if (w < minWidth || w > maxWidth) continue;
				if (w2 < minWidth || w2 > maxWidth) continue;

				BlockPos p1 = base.offset(facing, dz).offset(sideways, w).withY(interiorY);
				BlockPos p2 = base.offset(facing, dz).offset(sideways, w2).withY(interiorY);

				if (!isPlaceableChestSpot(world, p1) || !isPlaceableChestSpot(world, p2)) continue;

				if (!placeChest(world, p1, chestFacing)) continue;
				if (!placeChest(world, p2, chestFacing)) continue;

				chestSlots.add(p1);
				chestSlots.add(p2);

				if (tryFill(world, chestSlots, stacks)) {
					return chestSlots.size();
				}
			}
		}

		tryFill(world, chestSlots, stacks);
		return chestSlots.size();
	}

	private static boolean isPlaceableChestSpot(ServerWorld world, BlockPos pos) {
		BlockState st = world.getBlockState(pos);
		if (st.getBlock() == Blocks.BEDROCK) return false;
		if (!st.isAir() && st.getBlock() != Blocks.AIR) return false;
		BlockState above = world.getBlockState(pos.up());
		return above.isAir();
	}

	private static boolean placeChest(ServerWorld world, BlockPos pos, Direction facing) {
		BlockState chest = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing);
		return world.setBlockState(pos, chest, 3);
	}

	private static boolean tryFill(ServerWorld world, List<BlockPos> chestPositions, List<ItemStack> stacks) {
		if (chestPositions.isEmpty()) return false;

		for (int i = 0; i < stacks.size(); i++) {
			ItemStack st = stacks.get(i);
			if (st == null || st.isEmpty()) continue;

			for (BlockPos cp : chestPositions) {
				BlockEntity be = world.getBlockEntity(cp);
				if (!(be instanceof ChestBlockEntity chest)) continue;

                st = insertIntoInventory(chest, st);

				if (st.isEmpty()) break;
			}

			stacks.set(i, st);
		}

		for (ItemStack st : stacks) {
			if (st != null && !st.isEmpty()) return false;
		}
		return true;
	}

	private static ItemStack insertIntoInventory(Inventory inv, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;

		for (int i = 0; i < inv.size(); i++) {
			ItemStack slot = inv.getStack(i);
			if (!slot.isEmpty()
					&& ItemStack.areItemsAndComponentsEqual(slot, stack)
					&& slot.getCount() < slot.getMaxCount()) {

				int canMove = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
				slot.increment(canMove);
				stack.decrement(canMove);
				inv.setStack(i, slot);
				if (stack.isEmpty()) return ItemStack.EMPTY;
			}
		}

		for (int i = 0; i < inv.size(); i++) {
			ItemStack slot = inv.getStack(i);
			if (slot.isEmpty()) {
				inv.setStack(i, stack);
				return ItemStack.EMPTY;
			}
		}

		return stack;
	}

	private static BlockPos roomCenterPos(BlockPos base, Direction facing, Direction sideways, int outerWidth, int outerDepth, int minW) {
		int centerDepth = (outerDepth - 1) / 2;
		int centerWidth = minW + (outerWidth - 1) / 2;
		return base.offset(facing, centerDepth).offset(sideways, centerWidth).up(1);
	}

	private static void dropStacks(ServerWorld world, BlockPos pos, List<ItemStack> stacks) {
		for (ItemStack st : stacks) {
			if (st == null || st.isEmpty()) continue;
			ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), st);
		}
		stacks.clear();
	}

	private static boolean isProtected(BlockState s) {
		if (s.hasBlockEntity()) return true;
		return s.getBlock() == Blocks.SPAWNER;
	}

	private static void pushUndo(UUID playerId, Action action) {
		Deque<Action> stack = UNDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
		stack.push(action);
		while (stack.size() > MAX_ACTIONS_PER_PLAYER) stack.removeLast();
	}

	private static void pushRedo(UUID playerId, Action action) {
		Deque<Action> stack = REDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
		stack.push(action);
		while (stack.size() > MAX_ACTIONS_PER_PLAYER) stack.removeLast();
	}

	private static void clearRedo(UUID playerId) {
		Deque<Action> stack = REDO.get(playerId);
		if (stack != null) stack.clear();
	}

	private static int undoOne(ServerPlayerEntity player) {
		Deque<Action> stack = UNDO.get(player.getUuid());
		if (stack == null || stack.isEmpty()) return 0;

		World w0 = player.getEntityWorld();
		if (!(w0 instanceof ServerWorld world)) return 0;
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

		World w0 = player.getEntityWorld();
		if (!(w0 instanceof ServerWorld world)) return 0;
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

	private static Result emptyResult(int iw, int ih, int id, String shellId, boolean force, boolean loot) {
		Action action = new Action("?", System.currentTimeMillis(), iw, ih, id, shellId, force, loot, 0, 0, List.of());
		return new Result(action);
	}

	private record Snapshot(BlockPos pos, BlockState before, BlockState after) {}
	private record Action(String dimensionId, long epochMs, int iw, int ih, int id, String shellId, boolean force, boolean loot, int changed, int lootItems, List<Snapshot> snapshots) {}
	private record Result(Action action) {}
}
