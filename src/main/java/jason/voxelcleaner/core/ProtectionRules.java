/**
 * Encapsulates rules that prevent protected blocks from being modified.
 */

package jason.voxelcleaner.core;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public final class ProtectionRules {
    private ProtectionRules() {}

    public static boolean isProtected(BlockState s) {
        if (s.hasBlockEntity()) return true;
        return s.getBlock() == Blocks.SPAWNER;
    }
}
