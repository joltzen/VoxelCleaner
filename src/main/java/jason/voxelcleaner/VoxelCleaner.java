package jason.voxelcleaner;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VoxelCleaner implements ModInitializer {
	public static final String MOD_ID = "voxelcleaner";


	private static final int MAX_W = 64;
	private static final int MAX_H = 64;
	private static final int MAX_D = 64;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("voxelcleaner")
							//.requires(src -> src.hasPermissionLevel(2))
							.then(argument("width", IntegerArgumentType.integer(3, MAX_W))
									.then(argument("height", IntegerArgumentType.integer(3, MAX_H))
											.then(argument("depth", IntegerArgumentType.integer(3, MAX_D))
													.executes(ctx -> {
														var src = ctx.getSource();

														final ServerPlayerEntity player;
														try {
															player = src.getPlayerOrThrow();
														} catch (CommandSyntaxException e) {
															src.sendError(Text.literal("The command can only be executed by one player."));
															return 0;
														}

														int w = IntegerArgumentType.getInteger(ctx, "width");
														int h = IntegerArgumentType.getInteger(ctx, "height");
														int d = IntegerArgumentType.getInteger(ctx, "depth");

														int cleared = hollowRoomInFront(player, w, h, d);
														src.sendFeedback(() -> Text.literal("🏗️ CozyHollow: Innenraum geleert: " + cleared + " Blöcke."), false);

														return Command.SINGLE_SUCCESS;
													})
											)))
			);
		});
	}

	/**
	 * Creates a cuboid wxhxd in front of the player and hollows it out
	 */
	private static int hollowRoomInFront(ServerPlayerEntity player, int innerWidth, int innerHeight, int innerDepth) {
		var world = player.getEntityWorld();

		// Außenmaße (inkl. Wände/Boden/Decke)
		int outerWidth  = innerWidth + 2;
		int outerHeight = innerHeight + 2;
		int outerDepth  = innerDepth + 2;

		Direction facing = player.getHorizontalFacing();
		Direction sideways = facing.rotateYClockwise();

		// 1 Block tiefer + 1 Block vor dem Spieler
		BlockPos base = player.getBlockPos().down().offset(facing, 1);

		// Breite für Außenquader sauber zentrieren
		int minW = -(outerWidth / 2);
		int maxW = minW + (outerWidth - 1);

		int cleared = 0;

		for (int dOffset = 0; dOffset < outerDepth; dOffset++) {
			for (int wOffset = minW; wOffset <= maxW; wOffset++) {
				for (int hOffset = 0; hOffset < outerHeight; hOffset++) {

					// Außenhülle bleibt stehen
					boolean isShell =
							(dOffset == 0 || dOffset == outerDepth - 1) ||
									(wOffset == minW || wOffset == maxW) ||
									(hOffset == 0 || hOffset == outerHeight - 1);

					if (isShell) continue;

					BlockPos target = base
							.offset(facing, dOffset)
							.offset(sideways, wOffset)
							.up(hOffset);

					BlockState state = world.getBlockState(target);

					if (state.isAir()) continue;
					if (state.getBlock() == Blocks.BEDROCK) continue;

					if (player.isCreative()) {
						world.setBlockState(target, Blocks.AIR.getDefaultState(), 3);
					} else {
						world.breakBlock(target, true, player);
					}

					cleared++;
				}
			}
		}

		return cleared;
	}

}
