/**
 * Handles block breaking, item collection, chest placement, and loot distribution.
 */

package jason.voxelcleaner.core;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public final class LootService {

    public int breakAndCollect(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, List<ItemStack> out) {
        BlockEntity be = world.getBlockEntity(pos);
        ItemStack tool = player.getMainHandStack();

        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, be, player, tool);

        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        if (be != null) world.removeBlockEntity(pos);

        int c = 0;
        for (ItemStack st : drops) {
            if (st == null || st.isEmpty()) continue;
            out.add(st.copy());
            c += st.getCount();
        }
        return c;
    }

    public int placeLootChestsAndFill(ServerWorld world,
                                      BlockPos base,
                                      Direction facing,
                                      Direction sideways,
                                      int outerWidth,
                                      int outerDepth,
                                      int minW,
                                      int maxW,
                                      List<ItemStack> stacks,
                                      Direction chestFacing) {

        if (stacks.isEmpty()) return 0;

        int interiorY = base.getY() + 1;
        int minDepth = 1;
        int maxDepth = outerDepth - 2;
        int minWidth = minW + 1;
        int maxWidth = maxW - 1;

        List<BlockPos> chestSlots = new ArrayList<>();

        int centerDepth = (outerDepth - 1) / 2;
        int centerWidth = minW + (outerWidth - 1) / 2;

        List<Integer> depthOrder = new ArrayList<>();
        depthOrder.add(centerDepth);
        for (int step = 1; step <= Math.max(centerDepth - minDepth, maxDepth - centerDepth); step++) {
            int a = centerDepth - step;
            int b = centerDepth + step;
            if (a >= minDepth) depthOrder.add(a);
            if (b <= maxDepth) depthOrder.add(b);
        }

        List<Integer> widthOrder = new ArrayList<>();
        widthOrder.add(centerWidth);
        for (int step = 1; step <= Math.max(centerWidth - minWidth, maxWidth - centerWidth); step++) {
            int a = centerWidth - step;
            int b = centerWidth + step;
            if (a >= minWidth) widthOrder.add(a);
            if (b <= maxWidth) widthOrder.add(b);
        }

        for (int dz : depthOrder) {
            for (int w : widthOrder) {
                int w2 = w + 1;
                if (w < minWidth || w > maxWidth) continue;
                if (w2 < minWidth || w2 > maxWidth) continue;

                BlockPos p1 = base.offset(facing, dz).offset(sideways, w).withY(interiorY);
                BlockPos p2 = base.offset(facing, dz).offset(sideways, w2).withY(interiorY);

                if (!isPlaceableChestSpot(world, p1) || !isPlaceableChestSpot(world, p2)) continue;

                if (!placeChest(world, p1, chestFacing)) continue;
                if (!placeChest(world, p2, chestFacing)) continue;

                chestSlots.add(p1);
                chestSlots.add(p2);

                if (tryFill(world, chestSlots, stacks)) {
                    return chestSlots.size();
                }
            }
        }

        tryFill(world, chestSlots, stacks);
        return chestSlots.size();
    }

    public void dropStacks(ServerWorld world, BlockPos pos, List<ItemStack> stacks) {
        for (ItemStack st : stacks) {
            if (st == null || st.isEmpty()) continue;
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), st);
        }
        stacks.clear();
    }

    public BlockPos roomCenterPos(BlockPos base, Direction facing, Direction sideways, int outerWidth, int outerDepth, int minW) {
        int centerDepth = (outerDepth - 1) / 2;
        int centerWidth = minW + (outerWidth - 1) / 2;
        return base.offset(facing, centerDepth).offset(sideways, centerWidth).up(1);
    }

    private boolean isPlaceableChestSpot(ServerWorld world, BlockPos pos) {
        BlockState st = world.getBlockState(pos);
        if (st.getBlock() == Blocks.BEDROCK) return false;
        if (!st.isAir() && st.getBlock() != Blocks.AIR) return false;
        BlockState above = world.getBlockState(pos.up());
        return above.isAir();
    }

    private boolean placeChest(ServerWorld world, BlockPos pos, Direction facing) {
        BlockState chest = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing);
        return world.setBlockState(pos, chest, 3);
    }

    private boolean tryFill(ServerWorld world, List<BlockPos> chestPositions, List<ItemStack> stacks) {
        if (chestPositions.isEmpty()) return false;

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack st = stacks.get(i);
            if (st == null || st.isEmpty()) continue;

            for (BlockPos cp : chestPositions) {
                BlockEntity be = world.getBlockEntity(cp);
                if (!(be instanceof ChestBlockEntity chest)) continue;

                st = insertIntoInventory(chest, st);
                if (st.isEmpty()) break;
            }

            stacks.set(i, st);
        }

        for (ItemStack st : stacks) {
            if (st != null && !st.isEmpty()) return false;
        }
        return true;
    }

    private ItemStack insertIntoInventory(Inventory inv, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);
            if (!slot.isEmpty()
                    && ItemStack.areItemsAndComponentsEqual(slot, stack)
                    && slot.getCount() < slot.getMaxCount()) {

                int canMove = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                slot.increment(canMove);
                stack.decrement(canMove);
                inv.setStack(i, slot);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }

        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) {
                inv.setStack(i, stack);
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }
}
