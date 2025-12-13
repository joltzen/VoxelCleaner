package jason.voxelcleaner;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VoxelCleaner implements ModInitializer {

	private static final int MAX_W = 64;
	private static final int MAX_H = 64;
	private static final int MAX_D = 64;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("voxelcleaner")
							.then(argument("width", IntegerArgumentType.integer(1, MAX_W))
									.then(argument("height", IntegerArgumentType.integer(1, MAX_H))
											.then(argument("depth", IntegerArgumentType.integer(1, MAX_D))

													// Variante 1: ohne Material (nur aushöhlen)
													.executes(ctx -> {
														var src = ctx.getSource();

														final ServerPlayerEntity player;
														try {
															player = src.getPlayerOrThrow();
														} catch (CommandSyntaxException e) {
															src.sendError(Text.literal("Dieser Command kann nur von einem Spieler ausgeführt werden."));
															return 0;
														}

														int w = IntegerArgumentType.getInteger(ctx, "width");
														int h = IntegerArgumentType.getInteger(ctx, "height");
														int d = IntegerArgumentType.getInteger(ctx, "depth");

														int cleared = hollowRoomInFront(player, w, h, d, null);
														src.sendFeedback(() -> Text.literal("🏗️ CozyHollow: Innenraum geleert: " + cleared + " Blöcke."), false);
														return Command.SINGLE_SUCCESS;
													})

													// Variante 2: mit Material (Hülle bauen)
													.then(argument("material", StringArgumentType.word())
															.executes(ctx -> {
																var src = ctx.getSource();

																final ServerPlayerEntity player;
																try {
																	player = src.getPlayerOrThrow();
																} catch (CommandSyntaxException e) {
																	src.sendError(Text.literal("Dieser Command kann nur von einem Spieler ausgeführt werden."));
																	return 0;
																}

																int w = IntegerArgumentType.getInteger(ctx, "width");
																int h = IntegerArgumentType.getInteger(ctx, "height");
																int d = IntegerArgumentType.getInteger(ctx, "depth");

																String mat = StringArgumentType.getString(ctx, "material");
																Block shellMaterial = parseBlockOrNull(mat);

																if (shellMaterial == null || shellMaterial == Blocks.AIR) {
																	src.sendError(Text.literal("Unbekannter Block: '" + mat + "'. Beispiel: minecraft:stone_bricks"));
																	return 0;
																}

																int cleared = hollowRoomInFront(player, w, h, d, shellMaterial);
																src.sendFeedback(() -> Text.literal("🧱 CozyHollow: Innenraum geleert: " + cleared + " | Hülle: " + mat), false);
																return Command.SINGLE_SUCCESS;
															})
													)
											)
									)
							)
			);
		});
	}


	private static int hollowRoomInFront(ServerPlayerEntity player, int innerWidth, int innerHeight, int innerDepth, Block shellMaterial) {
		var world = player.getEntityWorld();

		int outerWidth  = innerWidth + 2;
		int outerHeight = innerHeight + 2;
		int outerDepth  = innerDepth + 2;

		Direction facing = player.getHorizontalFacing();
		Direction sideways = facing.rotateYClockwise();

		BlockPos base = player.getBlockPos().down().offset(facing, 1);

		int minW = -(outerWidth / 2);
		int maxW = minW + (outerWidth - 1);

		int cleared = 0;

		for (int dOffset = 0; dOffset < outerDepth; dOffset++) {
			for (int wOffset = minW; wOffset <= maxW; wOffset++) {
				for (int hOffset = 0; hOffset < outerHeight; hOffset++) {

					BlockPos target = base
							.offset(facing, dOffset)
							.offset(sideways, wOffset)
							.up(hOffset);

					BlockState state = world.getBlockState(target);

					if (state.getBlock() == Blocks.BEDROCK) continue;

					boolean isShell =
							(dOffset == 0 || dOffset == outerDepth - 1) ||
									(wOffset == minW || wOffset == maxW) ||
									(hOffset == 0 || hOffset == outerHeight - 1);

					if (isShell) {
						if (shellMaterial != null) {
							// optional: Air als Material vermeiden
							if (shellMaterial != Blocks.AIR) {
								// BlockEntities nicht überschreiben (Kisten/Öfen/etc.)
								if (!state.hasBlockEntity()) {
									// nicht unnötig setzen, wenn schon richtiges Material
									if (state.getBlock() != shellMaterial) {
										world.setBlockState(target, shellMaterial.getDefaultState(), 3);
									}
								}
							}
						}
						continue;
					}


					// Innenraum leeren
					if (state.isAir()) continue;

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

	private static Block parseBlockOrNull(String id) {
		if (!id.contains(":")) {
			id = "minecraft:" + id;
		}

		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) return null;

		if (!Registries.BLOCK.containsId(identifier)) {
			return null;
		}

		return Registries.BLOCK.get(identifier);
	}

}
