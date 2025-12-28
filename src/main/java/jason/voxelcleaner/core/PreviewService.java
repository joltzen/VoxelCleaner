/**
 * Renders temporary particle previews for upcoming voxel operations.
 *
 * The preview is sent only to the requesting player (server-safe).
 */
package jason.voxelcleaner.core;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class PreviewService {

    // Keep it lightweight and visible.
    private static final ParticleEffect P = ParticleTypes.END_ROD;

    /**
     * Preview an oriented box using the same coordinate mapping as VoxelOperations (base + f/s axes).
     *
     * @param iw inner width (or plain width, depending on caller)
     * @param ih inner height
     * @param id inner depth
     * @param addShellPadding if true, adds +2 padding like hollow/room outer box
     */
    public void previewBox(ServerPlayerEntity player, int iw, int ih, int id, boolean addShellPadding) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        int ow = addShellPadding ? iw + 2 : iw;
        int oh = addShellPadding ? ih + 2 : ih;
        int od = addShellPadding ? id + 2 : id;

        Direction f = player.getHorizontalFacing();
        Direction s = f.rotateYClockwise();

        BlockPos base = player.getBlockPos().down().offset(f, 1);

        int minW = -(ow / 2);
        int maxW = minW + ow - 1;

        // Outline only (edges), sparsified.
        for (int dz = 0; dz < od; dz++) {
            for (int dx = minW; dx <= maxW; dx++) {
                for (int dy = 0; dy < oh; dy++) {

                    boolean onX = dx == minW || dx == maxW;
                    boolean onY = dy == 0 || dy == oh - 1;
                    boolean onZ = dz == 0 || dz == od - 1;

                    // edges = at least 2 planes
                    int planes = (onX ? 1 : 0) + (onY ? 1 : 0) + (onZ ? 1 : 0);
                    if (planes < 2) continue;

                    // sparsify a bit
                    if (((dx - minW) + dy + dz) % 2 != 0) continue;

                    BlockPos p = base.offset(f, dz).offset(s, dx).up(dy);
                    spawnToPlayer(world, player, p);
                }
            }
        }
    }

    public void previewSphere(ServerPlayerEntity player, int radius) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        Direction f = player.getHorizontalFacing();
        BlockPos center = player.getBlockPos().offset(f, 1).up(radius / 2);

        int r2 = radius * radius;
        int inner2 = (radius - 1) * (radius - 1);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > r2) continue;
                    // boundary only
                    if (d2 <= inner2) continue;

                    if (((x + y + z) & 1) != 0) continue;

                    BlockPos p = center.add(x, y, z);
                    spawnToPlayer(world, player, p);
                }
            }
        }
    }

    public void previewCylinder(ServerPlayerEntity player, int radius, int height) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        Direction f = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(f, 1);

        int r2 = radius * radius;
        int inner2 = (radius - 1) * (radius - 1);

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int d2 = x * x + z * z;
                    if (d2 > r2) continue;

                    boolean boundary = d2 > inner2 || y == 0 || y == height - 1;
                    if (!boundary) continue;

                    if (((x + y + z) & 1) != 0) continue;

                    BlockPos p = base.add(x, y, z);
                    spawnToPlayer(world, player, p);
                }
            }
        }
    }

    public void previewPyramid(ServerPlayerEntity player, int baseSize, int height) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        Direction f = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(f, 1);

        int half = baseSize / 2;

        for (int y = 0; y < height; y++) {
            // linearly shrink
            int layerHalf = Math.max(0, half - (int) Math.floor((double) y * (double) half / (double) Math.max(1, height - 1)));
            int min = -layerHalf;
            int max = layerHalf;

            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    boolean boundary = x == min || x == max || z == min || z == max || y == 0 || y == height - 1;
                    if (!boundary) continue;
                    if (((x + y + z) & 1) != 0) continue;

                    BlockPos p = base.add(x, y, z);
                    spawnToPlayer(world, player, p);
                }
            }
        }
    }

    private static void spawnToPlayer(ServerWorld world, ServerPlayerEntity player, BlockPos p) {
        world.spawnParticles(
                player,
                P,
                true,
                false,
                p.getX() + 0.5,
                p.getY() + 0.5,
                p.getZ() + 0.5,
                1,
                0.0,
                0.0,
                0.0,
                0.0
        );
    }
}
