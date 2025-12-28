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



    // ---------------------------------------------------------------------
    // Replace
    // ---------------------------------------------------------------------

    public Result replace(ServerPlayerEntity player,
                          int w, int h, int d,
                          Block from, Block to,
                          boolean force,
                          boolean shellOnly,
                          boolean insideOnly,
                          int chancePercent) {

        World w0 = player.getEntityWorld();
        if (!(w0 instanceof ServerWorld world)) {
            String meta = "replace:from=" + (from == null ? "null" : Registries.BLOCK.getId(from)) +
                    ",to=" + (to == null ? "null" : Registries.BLOCK.getId(to)) +
                    ",mode=" + (shellOnly ? "shell" : insideOnly ? "inside" : "all") +
                    ",chance=" + chancePercent;
            return emptyResult(w, h, d, meta, force, false);
        }

        String dim = world.getRegistryKey().getValue().toString();
        long now = System.currentTimeMillis();

        Direction f = player.getHorizontalFacing();
        Direction s = f.rotateYClockwise();

        BlockPos base = player.getBlockPos().down().offset(f, 1);

        int minW = -(w / 2);
        int maxW = minW + w - 1;

        BlockState fromState = from.getDefaultState();
        BlockState toState = to.getDefaultState();

        List<Snapshot> snaps = new ArrayList<>();
        int changed = 0;

        int safeChance = Math.max(0, Math.min(100, chancePercent));

        for (int dz = 0; dz < d; dz++) {
            for (int dx = minW; dx <= maxW; dx++) {
                for (int dy = 0; dy < h; dy++) {

                    boolean onShell = dx == minW || dx == maxW || dz == 0 || dz == d - 1 || dy == 0 || dy == h - 1;
                    if (shellOnly && !onShell) continue;
                    if (insideOnly && onShell) continue;

                    if (safeChance < 100) {
                        // deterministic-ish per-position chance (no Random instance needed)
                        int hash = (dx * 73471) ^ (dy * 91283) ^ (dz * 39017);
                        int roll = Math.floorMod(hash, 100);
                        if (roll >= safeChance) continue;
                    }

                    BlockPos p = base.offset(f, dz).offset(s, dx).up(dy);
                    BlockState st = world.getBlockState(p);

                    if (!st.equals(fromState)) continue;
                    if (!force && isProtected(st)) continue;
                    if (st.equals(toState)) continue;

                    snaps.add(new Snapshot(p, st, toState));
                    world.setBlockState(p, toState, 3);
                    changed++;
                }
            }
        }

        String meta = "replace:from=" + Registries.BLOCK.getId(from) +
                ",to=" + Registries.BLOCK.getId(to) +
                ",mode=" + (shellOnly ? "shell" : insideOnly ? "inside" : "all") +
                ",chance=" + safeChance;

        Action action = new Action(dim, now, w, h, d, meta, force, false, changed, 0, snaps);
        return new Result(action);
    }

    // ---------------------------------------------------------------------
    // Shapes
    // ---------------------------------------------------------------------

    public Result shapeSphere(ServerPlayerEntity player, int radius, Block material, boolean hollow, boolean force) {
        World w0 = player.getEntityWorld();
        String meta = "shape:sphere r=" + radius + " block=" + Registries.BLOCK.getId(material) + " hollow=" + hollow;

        if (!(w0 instanceof ServerWorld world)) return emptyResult(radius, radius, radius, meta, force, false);

        String dim = world.getRegistryKey().getValue().toString();
        long now = System.currentTimeMillis();

        Direction f = player.getHorizontalFacing();
        BlockPos center = player.getBlockPos().offset(f, Math.max(2, radius + 2)).up(radius);

        int r2 = radius * radius;
        int inner2 = (radius - 1) * (radius - 1);

        BlockState target = material.getDefaultState();

        List<Snapshot> snaps = new ArrayList<>();
        int changed = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    int d2 = x * x + y * y + z * z;
                    if (d2 > r2) continue;
                    if (hollow && d2 <= inner2) continue;

                    BlockPos p = center.add(x, y, z);
                    BlockState st = world.getBlockState(p);

                    if (!force && isProtected(st)) continue;
                    if (st.equals(target)) continue;

                    snaps.add(new Snapshot(p, st, target));
                    world.setBlockState(p, target, 3);
                    changed++;
                }
            }
        }

        Action action = new Action(dim, now, radius, radius, radius, meta, force, false, changed, 0, snaps);
        return new Result(action);
    }

    public Result shapeCylinder(ServerPlayerEntity player, int radius, int height, Block material, boolean hollow, boolean force) {
        World w0 = player.getEntityWorld();
        String meta = "shape:cylinder r=" + radius + " h=" + height + " block=" + Registries.BLOCK.getId(material) + " hollow=" + hollow;

        if (!(w0 instanceof ServerWorld world)) return emptyResult(radius, height, radius, meta, force, false);

        String dim = world.getRegistryKey().getValue().toString();
        long now = System.currentTimeMillis();

        Direction f = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(f, Math.max(2, radius + 2));

        int r2 = radius * radius;
        int inner2 = (radius - 1) * (radius - 1);

        BlockState target = material.getDefaultState();

        List<Snapshot> snaps = new ArrayList<>();
        int changed = 0;

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {

                    int d2 = x * x + z * z;
                    if (d2 > r2) continue;
                    if (hollow && d2 <= inner2 && y != 0 && y != height - 1) continue;

                    BlockPos p = base.add(x, y, z);
                    BlockState st = world.getBlockState(p);

                    if (!force && isProtected(st)) continue;
                    if (st.equals(target)) continue;

                    snaps.add(new Snapshot(p, st, target));
                    world.setBlockState(p, target, 3);
                    changed++;
                }
            }
        }

        Action action = new Action(dim, now, radius, height, radius, meta, force, false, changed, 0, snaps);
        return new Result(action);
    }

    public Result shapePyramid(ServerPlayerEntity player, int baseSize, int height, Block material, boolean hollow, boolean force) {
        World w0 = player.getEntityWorld();
        String meta = "shape:pyramid base=" + baseSize + " h=" + height + " block=" + Registries.BLOCK.getId(material) + " hollow=" + hollow;

        if (!(w0 instanceof ServerWorld world)) return emptyResult(baseSize, height, baseSize, meta, force, false);

        String dim = world.getRegistryKey().getValue().toString();
        long now = System.currentTimeMillis();

        Direction f = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(f, Math.max(2, (baseSize / 2) + 2));

        int half = baseSize / 2;
        BlockState target = material.getDefaultState();

        List<Snapshot> snaps = new ArrayList<>();
        int changed = 0;

        for (int y = 0; y < height; y++) {
            int layerHalf = Math.max(0, half - (int) Math.floor((double) y * (double) half / (double) Math.max(1, height - 1)));
            int min = -layerHalf;
            int max = layerHalf;

            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {

                    boolean boundary = x == min || x == max || z == min || z == max || y == 0 || y == height - 1;
                    if (hollow && !boundary) continue;

                    BlockPos p = base.add(x, y, z);
                    BlockState st = world.getBlockState(p);

                    if (!force && isProtected(st)) continue;
                    if (st.equals(target)) continue;

                    snaps.add(new Snapshot(p, st, target));
                    world.setBlockState(p, target, 3);
                    changed++;
                }
            }
        }

        Action action = new Action(dim, now, baseSize, height, baseSize, meta, force, false, changed, 0, snaps);
        return new Result(action);
    }

    private Result emptyResult(int iw, int ih, int id, String shellId, boolean force, boolean loot) {
        Action action = new Action("?", System.currentTimeMillis(), iw, ih, id, shellId, force, loot, 0, 0, List.of());
        return new Result(action);
    }
}
