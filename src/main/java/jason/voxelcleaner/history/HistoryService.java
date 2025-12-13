/**
 * Manages undo/redo stacks and restores or reapplies previous world changes.
 */
package jason.voxelcleaner.history;

import jason.voxelcleaner.config.VoxelConfig;
import jason.voxelcleaner.model.VoxelModels.Action;
import jason.voxelcleaner.model.VoxelModels.Snapshot;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.time.Instant;
import java.util.*;

public final class HistoryService {

    private static final Map<UUID, Deque<Action>> UNDO = new HashMap<>();
    private static final Map<UUID, Deque<Action>> REDO = new HashMap<>();

    public void pushUndo(UUID playerId, Action action) {
        Deque<Action> stack = UNDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        stack.push(action);
        while (stack.size() > VoxelConfig.MAX_ACTIONS_PER_PLAYER) stack.removeLast();
    }

    public void pushRedo(UUID playerId, Action action) {
        Deque<Action> stack = REDO.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        stack.push(action);
        while (stack.size() > VoxelConfig.MAX_ACTIONS_PER_PLAYER) stack.removeLast();
    }

    public void clearRedo(UUID playerId) {
        Deque<Action> stack = REDO.get(playerId);
        if (stack != null) stack.clear();
    }

    public int undoOne(ServerPlayerEntity player) {
        Deque<Action> stack = UNDO.get(player.getUuid());
        if (stack == null || stack.isEmpty()) return 0;

        World w0 = player.getEntityWorld();
        if (!(w0 instanceof ServerWorld world)) return 0;

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
        return restored;
    }

    public int redoOne(ServerPlayerEntity player) {
        Deque<Action> stack = REDO.get(player.getUuid());
        if (stack == null || stack.isEmpty()) return 0;

        World w0 = player.getEntityWorld();
        if (!(w0 instanceof ServerWorld world)) return 0;

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
        return applied;
    }

    public boolean hasUndo(ServerPlayerEntity player) {
        Deque<Action> stack = UNDO.get(player.getUuid());
        return stack != null && !stack.isEmpty();
    }

    public void sendHistory(ServerPlayerEntity player, int count) {
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
}
