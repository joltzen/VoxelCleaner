/**
 * Contains the core world-editing logic for hollowing areas and generating rooms.
 */

package jason.voxelcleaner.core;

import jason.voxelcleaner.model.VoxelModels.Action;
import jason.voxelcleaner.model.VoxelModels.Result;
import jason.voxelcleaner.model.VoxelModels.Snapshot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static jason.voxelcleaner.core.ProtectionRules.isProtected;

public final class VoxelOperations {

    private final LootService lootService = new LootService();

    public Result room(ServerPlayerEntity player, int iw, int ih, int id,
                       Block walls, Block floor, Block ceiling,
                       boolean force, boolean loot) {

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
                        lootItems += lootService.breakAndCollect(world, player, p, st, collected);
                    }

                    changed++;
                }
            }
        }

        if (loot && !player.isCreative() && collected != null && !collected.isEmpty()) {
            int placed = lootService.placeLootChestsAndFill(
                    world, base, f, s,
                    ow, od, minW, maxW,
                    collected,
                    player.getHorizontalFacing().getOpposite()
            );

            if (placed == 0) {
                BlockPos dropAt = lootService.roomCenterPos(base, f, s, ow, od, minW);
                lootService.dropStacks(world, dropAt, collected);
            }
        }

        String shellId = "room:walls=" + Registries.BLOCK.getId(walls)
                + ",floor=" + Registries.BLOCK.getId(floor)
                + ",ceiling=" + Registries.BLOCK.getId(ceiling);

        Action action = new Action(dim, now, iw, ih, id, shellId, force, loot, changed, lootItems, snaps);
        return new Result(action);
    }

    public Result hollow(ServerPlayerEntity player, int iw, int ih, int id,
                         Block shell, boolean force, boolean loot) {

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
                        lootItems += lootService.breakAndCollect(world, player, p, st, collected);
                    }

                    changed++;
                }
            }
        }

        if (loot && !player.isCreative() && collected != null && !collected.isEmpty()) {
            int placed = lootService.placeLootChestsAndFill(
                    world, base, f, s,
                    ow, od, minW, maxW,
                    collected,
                    player.getHorizontalFacing().getOpposite()
            );

            if (placed == 0) {
                BlockPos dropAt = lootService.roomCenterPos(base, f, s, ow, od, minW);
                lootService.dropStacks(world, dropAt, collected);
            }
        }

        String shellId = shell == null ? null : Registries.BLOCK.getId(shell).toString();
        Action action = new Action(dim, now, iw, ih, id, shellId, force, loot, changed, lootItems, snaps);
        return new Result(action);
    }

    private Result emptyResult(int iw, int ih, int id, String shellId, boolean force, boolean loot) {
        Action action = new Action("?", System.currentTimeMillis(), iw, ih, id, shellId, force, loot, 0, 0, List.of());
        return new Result(action);
    }
}
