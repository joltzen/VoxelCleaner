package jason.voxelcleaner.core;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PreviewService {

    private static final ParticleEffect P = ParticleTypes.END_ROD;

    // 20 ticks = 1s
    private static final int DEFAULT_DURATION_TICKS = 20 * 6; // 6s sichtbar
    private static final int DEFAULT_REFRESH_EVERY = 4;       // alle 4 Ticks neu spawnen (~0.2s)

    private static final Map<UUID, PreviewRequest> ACTIVE = new ConcurrentHashMap<>();

    private PreviewService() {}

    /** Muss in VoxelCleaner per ServerTickEvents registriert werden. */
    public static void tick(MinecraftServer server) {
        long now = server.getTicks();

        Iterator<Map.Entry<UUID, PreviewRequest>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PreviewRequest> e = it.next();
            PreviewRequest req = e.getValue();

            if (now > req.expiresAtTick) {
                it.remove();
                continue;
            }

            if (now < req.nextSpawnTick) continue;
            req.nextSpawnTick = now + req.refreshEvery;

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            if (!(player.getEntityWorld() instanceof ServerWorld world)) {
                it.remove();
                continue;
            }

            switch (req.type) {
                case BOX -> renderBox(world, player, req.box);
                case SPHERE -> renderSphere(world, player, req.sphere);
                case CYLINDER -> renderCylinder(world, player, req.cylinder);
                case PYRAMID -> renderPyramid(world, player, req.pyramid);
            }
        }
    }

    public static void previewBox(ServerPlayerEntity player, int iw, int ih, int id, boolean addShellPadding) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        int ow = addShellPadding ? iw + 2 : iw;
        int oh = addShellPadding ? ih + 2 : ih;
        int od = addShellPadding ? id + 2 : id;

        Direction f = player.getHorizontalFacing();
        Direction s = f.rotateYClockwise();
        BlockPos base = player.getBlockPos().down().offset(f, 1);

        int minW = -(ow / 2);
        int maxW = minW + ow - 1;

        assert world.getServer() != null;
        long now = world.getServer().getTicks();
        PreviewRequest req = PreviewRequest.box(
                player.getUuid(), now,
                new BoxData(base, f, s, ow, oh, od, minW, maxW)
        );
        ACTIVE.put(player.getUuid(), req);

        renderBox(world, player, req.box); // sofortiges Feedback
    }

    public static void previewSphere(ServerPlayerEntity player, int radius) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        Direction f = player.getHorizontalFacing();
        BlockPos center = player.getBlockPos().offset(f, 1).up(radius / 2);

        assert world.getServer() != null;
        long now = world.getServer().getTicks();
        PreviewRequest req = PreviewRequest.sphere(
                player.getUuid(), now,
                new SphereData(center, radius)
        );
        ACTIVE.put(player.getUuid(), req);

        renderSphere(world, player, req.sphere);
    }

    public static void previewCylinder(ServerPlayerEntity player, int radius, int height) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        Direction f = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(f, 1);

        assert world.getServer() != null;
        long now = world.getServer().getTicks();
        PreviewRequest req = PreviewRequest.cylinder(
                player.getUuid(), now,
                new CylinderData(base, radius, height)
        );
        ACTIVE.put(player.getUuid(), req);

        renderCylinder(world, player, req.cylinder);
    }

    public static void previewPyramid(ServerPlayerEntity player, int baseSize, int height) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        Direction f = player.getHorizontalFacing();
        BlockPos base = player.getBlockPos().offset(f, 1);

        assert world.getServer() != null;
        long now = world.getServer().getTicks();
        PreviewRequest req = PreviewRequest.pyramid(
                player.getUuid(), now,
                new PyramidData(base, baseSize, height)
        );
        ACTIVE.put(player.getUuid(), req);

        renderPyramid(world, player, req.pyramid);
    }

    // ---------------- Render ----------------

    private static void renderBox(ServerWorld world, ServerPlayerEntity player, BoxData d) {
        for (int dz = 0; dz < d.od; dz++) {
            for (int dx = d.minW; dx <= d.maxW; dx++) {
                for (int dy = 0; dy < d.oh; dy++) {

                    boolean onX = dx == d.minW || dx == d.maxW;
                    boolean onY = dy == 0 || dy == d.oh - 1;
                    boolean onZ = dz == 0 || dz == d.od - 1;

                    if (!(onX || onY || onZ)) continue;

                    if (((dx - d.minW) + dy + dz) % 2 != 0) continue;

                    BlockPos p = d.base.offset(d.f, dz).offset(d.s, dx).up(dy);
                    spawnToPlayer(world, player, p);
                }
            }
        }
    }

    private static void renderSphere(ServerWorld world, ServerPlayerEntity player, SphereData d) {
        int r = d.radius;
        int r2 = r * r;
        int inner = Math.max(0, r - 1);
        int inner2 = inner * inner;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    int dist2 = x * x + y * y + z * z;
                    if (dist2 > r2) continue;
                    if (dist2 <= inner2) continue;
                    if (((x + y + z) & 1) != 0) continue;

                    spawnToPlayer(world, player, d.center.add(x, y, z));
                }
            }
        }
    }

    private static void renderCylinder(ServerWorld world, ServerPlayerEntity player, CylinderData d) {
        int r2 = d.radius * d.radius;
        int inner = Math.max(0, d.radius - 1);
        int inner2 = inner * inner;

        for (int y = 0; y < d.height; y++) {
            for (int x = -d.radius; x <= d.radius; x++) {
                for (int z = -d.radius; z <= d.radius; z++) {
                    int dist2 = x * x + z * z;
                    if (dist2 > r2) continue;

                    boolean boundary = dist2 > inner2 || y == 0 || y == d.height - 1;
                    if (!boundary) continue;

                    if (((x + y + z) & 1) != 0) continue;

                    spawnToPlayer(world, player, d.base.add(x, y, z));
                }
            }
        }
    }

    private static void renderPyramid(ServerWorld world, ServerPlayerEntity player, PyramidData d) {
        int half = d.baseSize / 2;

        for (int y = 0; y < d.height; y++) {
            int layerHalf = Math.max(0, half - (int) Math.floor((double) y * (double) half / (double) Math.max(1, d.height - 1)));
            int min = -layerHalf;

            for (int x = min; x <= layerHalf; x++) {
                for (int z = min; z <= layerHalf; z++) {
                    boolean boundary = x == min || x == layerHalf || z == min || z == layerHalf || y == 0 || y == d.height - 1;
                    if (!boundary) continue;
                    if (((x + y + z) & 1) != 0) continue;

                    spawnToPlayer(world, player, d.base.add(x, y, z));
                }
            }
        }
    }

    private static void spawnToPlayer(ServerWorld world, ServerPlayerEntity player, BlockPos p) {
        world.spawnParticles(
                player,
                P,
                true,   // force
                false,  // important
                p.getX() + 0.5,
                p.getY() + 0.5,
                p.getZ() + 0.5,
                2,
                0.0, 0.03, 0.03,
                0.0
        );
    }

    // ---------------- Data ----------------

    private enum Type { BOX, SPHERE, CYLINDER, PYRAMID }

    private record BoxData(BlockPos base, Direction f, Direction s, int ow, int oh, int od, int minW, int maxW) {}
    private record SphereData(BlockPos center, int radius) {}
    private record CylinderData(BlockPos base, int radius, int height) {}
    private record PyramidData(BlockPos base, int baseSize, int height) {}

    private static final class PreviewRequest {
        final Type type;
        final long expiresAtTick;
        final int refreshEvery;
        long nextSpawnTick;

        final BoxData box;
        final SphereData sphere;
        final CylinderData cylinder;
        final PyramidData pyramid;

        private PreviewRequest(Type type, long expiresAtTick, int refreshEvery, long nextSpawnTick,
                               BoxData box, SphereData sphere, CylinderData cylinder, PyramidData pyramid) {
            this.type = type;
            this.expiresAtTick = expiresAtTick;
            this.refreshEvery = refreshEvery;
            this.nextSpawnTick = nextSpawnTick;
            this.box = box;
            this.sphere = sphere;
            this.cylinder = cylinder;
            this.pyramid = pyramid;
        }

        static PreviewRequest box(UUID playerId, long nowTick, BoxData d) {
            return new PreviewRequest(Type.BOX, nowTick + PreviewService.DEFAULT_DURATION_TICKS, PreviewService.DEFAULT_REFRESH_EVERY, nowTick, d, null, null, null);
        }

        static PreviewRequest sphere(UUID playerId, long nowTick, SphereData d) {
            return new PreviewRequest(Type.SPHERE, nowTick + PreviewService.DEFAULT_DURATION_TICKS, PreviewService.DEFAULT_REFRESH_EVERY, nowTick, null, d, null, null);
        }

        static PreviewRequest cylinder(UUID playerId, long nowTick, CylinderData d) {
            return new PreviewRequest(Type.CYLINDER, nowTick + PreviewService.DEFAULT_DURATION_TICKS, PreviewService.DEFAULT_REFRESH_EVERY, nowTick, null, null, d, null);
        }

        static PreviewRequest pyramid(UUID playerId, long nowTick, PyramidData d) {
            return new PreviewRequest(Type.PYRAMID, nowTick + PreviewService.DEFAULT_DURATION_TICKS, PreviewService.DEFAULT_REFRESH_EVERY, nowTick, null, null, null, d);
        }
    }
}
