/**
 * Manages undo/redo stacks and restores or reapplies previous world changes.
 */
package jason.voxelcleaner.history;

import jason.voxelcleaner.config.VoxelConfig;
import jason.voxelcleaner.model.VoxelModels.Action;
import jason.voxelcleaner.model.VoxelModels.Snapshot;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import java.time.Instant;
import java.util.*;

public final class HistoryService {

    private static final Map<UUID, Deque<Action>> UNDO = new HashMap<>();
    private static final Map<UUID, Deque<Action>> REDO = new HashMap<>();

    /**
     * Avoid repeated disk reads; stacks are loaded lazily when a player uses history/undo/redo.
     */
    private static final Set<UUID> LOADED_FROM_DISK = new HashSet<>();

    public void pushUndo(UUID playerId, Action action) {
        Deque<Action> stack = UNDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        stack.push(action);
        while (stack.size() > VoxelConfig.MAX_ACTIONS_PER_PLAYER) stack.removeLast();

        if (VoxelConfig.PERSIST_HISTORY) savePlayer(playerId);
    }

    public void pushRedo(UUID playerId, Action action) {
        Deque<Action> stack = REDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        stack.push(action);
        while (stack.size() > VoxelConfig.MAX_ACTIONS_PER_PLAYER) stack.removeLast();

        if (VoxelConfig.PERSIST_HISTORY) savePlayer(playerId);
    }

    public void clearRedo(UUID playerId) {
        Deque<Action> stack = REDO.get(playerId);
        if (stack != null) stack.clear();

        if (VoxelConfig.PERSIST_HISTORY) savePlayer(playerId);
    }

    public int undoOne(ServerPlayerEntity player) {
        World w0 = player.getEntityWorld();
        if (!(w0 instanceof ServerWorld world)) return 0;

        ensureLoaded(player.getUuid(), world);

        Deque<Action> stack = UNDO.get(player.getUuid());
        if (stack == null || stack.isEmpty()) return 0;

        String currentDim = world.getRegistryKey().getValue().toString();
        Action action = stack.pop();

        if (!action.dimensionId().equals(currentDim)) {
            stack.push(action);
            return 0;
        }

        int restored = 0;
        List<Snapshot> snaps = action.snapshots();
        for (int i = snaps.size() - 1; i >= 0; i--) {
            Snapshot s = snaps.get(i);
            world.setBlockState(s.pos(), s.before(), 3);
            restored++;
        }

        pushRedo(player.getUuid(), action);
        if (VoxelConfig.PERSIST_HISTORY) savePlayer(player.getUuid());
        return restored;
    }

    public int redoOne(ServerPlayerEntity player) {
        World w0 = player.getEntityWorld();
        if (!(w0 instanceof ServerWorld world)) return 0;

        ensureLoaded(player.getUuid(), world);

        Deque<Action> stack = REDO.get(player.getUuid());
        if (stack == null || stack.isEmpty()) return 0;

        String currentDim = world.getRegistryKey().getValue().toString();
        Action action = stack.pop();

        if (!action.dimensionId().equals(currentDim)) {
            stack.push(action);
            return 0;
        }

        int applied = 0;
        List<Snapshot> snaps = action.snapshots();
        for (int i = snaps.size() - 1; i >= 0; i--) {
            Snapshot s = snaps.get(i);
            world.setBlockState(s.pos(), s.after(), 3);
            applied++;
        }

        pushUndo(player.getUuid(), action);
        if (VoxelConfig.PERSIST_HISTORY) savePlayer(player.getUuid());
        return applied;
    }

    public boolean hasUndo(ServerPlayerEntity player) {
        World w0 = player.getEntityWorld();
        if (VoxelConfig.PERSIST_HISTORY && w0 instanceof ServerWorld world) {
            ensureLoaded(player.getUuid(), world);
        }
        Deque<Action> stack = UNDO.get(player.getUuid());
        return stack != null && !stack.isEmpty();
    }

    public void sendHistory(ServerPlayerEntity player, int count) {
        World w0 = player.getEntityWorld();
        if (VoxelConfig.PERSIST_HISTORY && w0 instanceof ServerWorld world) {
            ensureLoaded(player.getUuid(), world);
        }

        Deque<Action> stack = UNDO.get(player.getUuid());
        if (stack == null || stack.isEmpty()) {
            player.sendMessage(Text.literal("VoxelCleaner: keine History"), false);
            return;
        }

        int n = Math.min(count, Math.min(stack.size(), VoxelConfig.MAX_HISTORY_LINES));
        player.sendMessage(Text.literal("VoxelCleaner History (neueste zuerst) [" + n + "/" + stack.size() + "]"), false);

        int i = 1;
        for (Action a : stack) {
            if (i > n) break;

            String ts = VoxelConfig.TS_FMT.format(Instant.ofEpochMilli(a.epochMs()));
            String shell = a.shellId() == null ? "-" : a.shellId();

            player.sendMessage(Text.literal(
                    "#" + i +
                            " " + ts +
                            " dim=" + a.dimensionId() +
                            " inner=" + a.iw() + "x" + a.ih() + "x" + a.id() +
                            " shell=" + shell +
                            " force=" + a.force() +
                            " loot=" + a.loot() +
                            " changed=" + a.changed()
            ), false);
            i++;
        }
    }

    // ---------------------------------------------------------------------
    // Persistence (NBT)
    // ---------------------------------------------------------------------

    private static void ensureLoaded(UUID playerId, ServerWorld world) {
        if (!VoxelConfig.PERSIST_HISTORY) return;
        if (LOADED_FROM_DISK.contains(playerId)) return;

        LOADED_FROM_DISK.add(playerId);
        loadPlayer(playerId, world);
    }

    private static java.nio.file.Path playerFile(UUID playerId) {
        // config/voxelcleaner/history/<uuid>.nbt
        java.nio.file.Path dir = FabricLoader.getInstance().getConfigDir()
                .resolve("voxelcleaner")
                .resolve("history");
        return dir.resolve(playerId.toString() + ".nbt");
    }

    private static void savePlayer(UUID playerId) {
        try {
            java.nio.file.Path file = playerFile(playerId);
            java.nio.file.Files.createDirectories(file.getParent());

            NbtCompound root = new NbtCompound();
            root.put("undo", writeStack(UNDO.get(playerId)));
            root.put("redo", writeStack(REDO.get(playerId)));

            NbtIo.writeCompressed(root, file);
        } catch (Exception ignored) {
        }
    }

    private static void loadPlayer(UUID playerId, ServerWorld world) {
        try {
            java.nio.file.Path file = playerFile(playerId);
            if (!java.nio.file.Files.exists(file)) return;

            // Newer API requires NbtSizeTracker
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            if (root == null) return;

            // Newer API: Optional-based getters
            NbtList undoList = root.getList("undo").orElse(new NbtList());
            NbtList redoList = root.getList("redo").orElse(new NbtList());

            Deque<Action> undo = readStack(undoList, world);
            Deque<Action> redo = readStack(redoList, world);

            if (!undo.isEmpty()) UNDO.put(playerId, undo);
            if (!redo.isEmpty()) REDO.put(playerId, redo);
        } catch (Exception ignored) {
            // Optional feature; ignore failures.
        }
    }

    private static NbtList writeStack(Deque<Action> stack) {
        NbtList list = new NbtList();
        if (stack == null || stack.isEmpty()) return list;

        int i = 0;
        for (Action a : stack) {
            if (i >= VoxelConfig.PERSIST_MAX_ACTIONS_PER_PLAYER) break;
            list.add(writeAction(a));
            i++;
        }
        return list;
    }

    private static Deque<Action> readStack(NbtList list, ServerWorld world) {
        Deque<Action> persistedOrder = new ArrayDeque<>();
        if (list == null || list.isEmpty()) return persistedOrder;

        for (int i = 0; i < list.size(); i++) {
            var aTagOpt = list.getCompound(i); // Optional<NbtCompound>
            if (aTagOpt.isEmpty()) continue;

            Action a = readAction(aTagOpt.get(), world);
            if (a != null) persistedOrder.addLast(a);
        }

        // persisted newest-first; convert into push/pop stack by reversing
        Deque<Action> asStack = new ArrayDeque<>();
        List<Action> tmp = new ArrayList<>(persistedOrder);
        for (int i = tmp.size() - 1; i >= 0; i--) asStack.push(tmp.get(i));
        return asStack;
    }

    private static NbtCompound writeAction(Action a) {
        NbtCompound tag = new NbtCompound();
        tag.putString("dim", a.dimensionId());
        tag.putLong("ts", a.epochMs());
        tag.putInt("iw", a.iw());
        tag.putInt("ih", a.ih());
        tag.putInt("id", a.id());
        if (a.shellId() != null) tag.putString("shell", a.shellId());
        tag.putBoolean("force", a.force());
        tag.putBoolean("loot", a.loot());
        tag.putInt("changed", a.changed());
        tag.putInt("lootItems", a.lootItems());

        NbtList snaps = new NbtList();
        for (Snapshot s : a.snapshots()) {
            snaps.add(writeSnapshot(s));
        }
        tag.put("snaps", snaps);
        return tag;
    }

    private static Action readAction(NbtCompound tag, ServerWorld world) {
        if (tag == null) return null;

        // Optional-based getters
        String dim = tag.getString("dim").orElse("");
        long ts = tag.getLong("ts").orElse(0L);
        int iw = tag.getInt("iw").orElse(0);
        int ih = tag.getInt("ih").orElse(0);
        int id = tag.getInt("id").orElse(0);

        String shell = tag.getString("shell").orElse(null);

        boolean force = tag.getBoolean("force").orElse(false);
        boolean loot = tag.getBoolean("loot").orElse(false);
        int changed = tag.getInt("changed").orElse(0);
        int lootItems = tag.getInt("lootItems").orElse(0);

        NbtList snapsTag = tag.getList("snaps").orElse(new NbtList());
        List<Snapshot> snaps = new ArrayList<>(snapsTag.size());

        for (int i = 0; i < snapsTag.size(); i++) {
            var snapOpt = snapsTag.getCompound(i);
            if (snapOpt.isEmpty()) continue;

            Snapshot s = readSnapshot(snapOpt.get(), world);
            if (s != null) snaps.add(s);
        }

        return new Action(dim, ts, iw, ih, id, shell, force, loot, changed, lootItems, snaps);
    }

    private static NbtCompound writeSnapshot(Snapshot s) {
        NbtCompound tag = new NbtCompound();
        tag.putInt("x", s.pos().getX());
        tag.putInt("y", s.pos().getY());
        tag.putInt("z", s.pos().getZ());
        tag.put("before", NbtHelper.fromBlockState(s.before()));
        tag.put("after", NbtHelper.fromBlockState(s.after()));
        return tag;
    }

    private static Snapshot readSnapshot(NbtCompound tag, ServerWorld world) {
        if (tag == null) return null;

        int x = tag.getInt("x").orElse(0);
        int y = tag.getInt("y").orElse(0);
        int z = tag.getInt("z").orElse(0);

        var beforeOpt = tag.getCompound("before");
        var afterOpt  = tag.getCompound("after");
        if (beforeOpt.isEmpty() || afterOpt.isEmpty()) return null;

        var pos = new net.minecraft.util.math.BlockPos(x, y, z);

        RegistryEntryLookup<Block> blockLookup =
                world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);

        var before = NbtHelper.toBlockState(blockLookup, beforeOpt.get());
        var after  = NbtHelper.toBlockState(blockLookup, afterOpt.get());

        return new Snapshot(pos, before, after);
    }

}
