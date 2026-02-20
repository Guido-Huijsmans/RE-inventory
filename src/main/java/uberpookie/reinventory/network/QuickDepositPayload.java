package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.slotlock.SlotLockManager;

import java.util.Comparator;
import java.util.List;

public record QuickDepositPayload(int syncId, int slotId) implements CustomPayload {
    public static final CustomPayload.Id<QuickDepositPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "quick_deposit"));

    public static final PacketCodec<PacketByteBuf, QuickDepositPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, QuickDepositPayload::syncId,
                    PacketCodecs.VAR_INT, QuickDepositPayload::slotId,
                    QuickDepositPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, QuickDepositPayload::handle);
    }

    private static void handle(QuickDepositPayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        process(player, payload);
    }

    private static void process(ServerPlayerEntity player, QuickDepositPayload payload) {
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != payload.syncId()) {
            return;
        }
        if (handler instanceof PlayerScreenHandler) {
            return; // no meaningful target inventory on player inventory screen
        }
        if (payload.slotId() < 0 || payload.slotId() >= handler.slots.size()) {
            return;
        }

        Slot clicked = handler.getSlot(payload.slotId());
        if (clicked == null || clicked.inventory == null || !clicked.hasStack()) {
            return;
        }

        if (SlotLockManager.isLocked(handler, clicked.id)) {
            return;
        }

        Inventory sourceInv = clicked.inventory;
        PlayerInventory playerInv = player.getInventory();
        Inventory targetInv = findTargetInventory(handler, sourceInv, playerInv);
        if (targetInv == null) {
            return;
        }

        ItemStack match = clicked.getStack().copy();

        boolean changed = moveMatchingStacks(player, handler, sourceInv, targetInv, match);
        if (changed) {
            handler.sendContentUpdates();
        }
    }

    private static boolean moveMatchingStacks(ServerPlayerEntity player, ScreenHandler handler, Inventory sourceInv, Inventory targetInv, ItemStack match) {
        List<Slot> sourceSlots = handler.slots.stream()
                .filter(slot -> slot.inventory == sourceInv)
                .filter(slot -> !SlotLockManager.isLocked(handler, slot.id))
                .filter(slot -> slot.canTakeItems(player))
                .sorted(Comparator.comparingInt(Slot::getIndex))
                .toList();

        List<Slot> targetSlots = handler.slots.stream()
                .filter(slot -> slot.inventory == targetInv)
                .filter(slot -> !SlotLockManager.isLocked(handler, slot.id))
                .sorted(Comparator.comparingInt(Slot::getIndex))
                .toList();

        if (targetSlots.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (Slot sourceSlot : sourceSlots) {
            ItemStack stack = sourceSlot.getStack();
            if (stack.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(stack, match)) continue;

            int moved = transferStack(stack, sourceSlot, targetSlots);
            if (moved > 0) {
                changed = true;
            }
        }
        return changed;
    }

    private static int transferStack(ItemStack stack, Slot sourceSlot, List<Slot> targetSlots) {
        int moved = 0;

        // Merge into existing stacks first
        for (Slot target : targetSlots) {
            ItemStack targetStack = target.getStack();
            if (targetStack.isEmpty()) continue;
            if (!target.canInsert(stack)) continue;
            if (!ItemStack.areItemsAndComponentsEqual(targetStack, stack)) continue;

            int max = Math.min(target.getMaxItemCount(stack), stack.getMaxCount());
            int space = max - targetStack.getCount();
            if (space <= 0) continue;

            int move = Math.min(space, stack.getCount());
            targetStack.increment(move);
            stack.decrement(move);
            target.markDirty();
            moved += move;

            if (stack.isEmpty()) break;
        }

        // Then fill empty slots
        if (!stack.isEmpty()) {
            for (Slot target : targetSlots) {
                if (!target.getStack().isEmpty()) continue;
                if (!target.canInsert(stack)) continue;

                int move = Math.min(target.getMaxItemCount(stack), stack.getCount());
                ItemStack placed = stack.copyWithCount(move);
                target.setStack(placed);
                stack.decrement(move);
                moved += move;

                if (stack.isEmpty()) break;
            }
        }

        if (moved > 0) {
            if (stack.isEmpty()) {
                sourceSlot.setStack(ItemStack.EMPTY);
            } else {
                sourceSlot.markDirty();
            }
        }

        return moved;
    }

    private static Inventory findTargetInventory(ScreenHandler handler, Inventory source, Inventory playerInv) {
        if (source == playerInv) {
            return findFirstNonPlayerInventory(handler, playerInv);
        }
        return playerInv;
    }

    private static Inventory findFirstNonPlayerInventory(ScreenHandler handler, Inventory playerInv) {
        for (Slot slot : handler.slots) {
            if (slot.inventory != null && slot.inventory != playerInv) {
                return slot.inventory;
            }
        }
        return null;
    }
}
