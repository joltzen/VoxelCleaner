/**
 * Centralizes configuration constants and limits used across the mod.
 */

package jason.voxelcleaner.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class VoxelConfig {
    private VoxelConfig() {}

    public static final int MAX_W = 64;
    public static final int MAX_H = 64;
    public static final int MAX_D = 64;

    public static final int MAX_ACTIONS_PER_PLAYER = 10;
    public static final int MAX_HISTORY_LINES = 20;

    public static final boolean PERSIST_HISTORY = true;

    public static final int PERSIST_MAX_ACTIONS_PER_PLAYER = MAX_ACTIONS_PER_PLAYER;

    public static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
}
