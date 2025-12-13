/**
 * Entry point of the mod that registers all voxel-related commands during initialization.
 */

package jason.voxelcleaner;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import jason.voxelcleaner.command.VoxelCommands;

public class VoxelCleaner implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			VoxelCommands.register(dispatcher, registryAccess);
		});
	}
}
