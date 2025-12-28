/**
 * Entry point of the mod that registers all voxel-related commands during initialization.
 */
package jason.voxelcleaner;

import jason.voxelcleaner.command.VoxelCommands;
import jason.voxelcleaner.core.PreviewService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class VoxelCleaner implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			VoxelCommands.register(dispatcher, registryAccess);
		});

		// Refresh particle previews so they stay visible longer
		ServerTickEvents.END_SERVER_TICK.register(PreviewService::tick);
	}
}

