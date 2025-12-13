/**
 * Defines immutable data records for actions, snapshots, and execution results.
 */

package jason.voxelcleaner.model;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class VoxelModels {
    private VoxelModels() {}

    public record Snapshot(BlockPos pos, BlockState before, BlockState after) {}

    public record Action(
            String dimensionId,
            long epochMs,
            int iw, int ih, int id,
            String shellId,
            boolean force,
            boolean loot,
            int changed,
            int lootItems,
            List<Snapshot> snapshots
    ) {}

    public record Result(Action action) {}
}
