package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record MouseTweaksActionPayload(int syncId, int slotId, Action action) implements CustomPayload {
    public static final CustomPayload.Id<MouseTweaksActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "mouse_tweaks_action"));

    public static final PacketCodec<PacketByteBuf, MouseTweaksActionPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, MouseTweaksActionPayload::syncId,
                    PacketCodecs.VAR_INT, MouseTweaksActionPayload::slotId,
                    PacketCodecs.INTEGER, p -> p.action().ordinal(),
                    (sync, slot, actionOrdinal) -> new MouseTweaksActionPayload(sync, slot, Action.fromOrdinal(actionOrdinal))
            );

    public enum Action {
        RMB_PLACE_ONE,
        LMB_PICKUP_MATCH,
        LMB_SHIFT_WITH_ITEM,
        LMB_SHIFT_NO_ITEM,
        WHEEL_PUSH,
        WHEEL_PULL;

        public static Action fromOrdinal(int ord) {
            Action[] values = values();
            if (ord < 0 || ord >= values.length) {
                return RMB_PLACE_ONE;
            }
            return values[ord];
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, MouseTweaksActionPayload::handle);
    }

    private static void handle(MouseTweaksActionPayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        process(player, payload);
    }

    private static void process(ServerPlayerEntity player, MouseTweaksActionPayload payload) {
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != payload.syncId()) {
            return;
        }
        if (payload.slotId() < 0 || payload.slotId() >= handler.slots.size()) {
            return;
        }

        Slot slot = handler.getSlot(payload.slotId());
        if (slot == null || slot.inventory == null) {
            return;
        }

        switch (payload.action()) {
            case RMB_PLACE_ONE -> placeOne(player, handler, slot);
            case LMB_PICKUP_MATCH -> pickupMatchIntoCursor(player, handler, slot);
            case LMB_SHIFT_WITH_ITEM -> shiftMatch(player, handler, slot);
            case LMB_SHIFT_NO_ITEM -> quickMove(player, handler, slot);
            case WHEEL_PUSH -> wheelPush(player, handler, slot);
            case WHEEL_PULL -> wheelPull(player, handler, slot);
        }
    }

    private static void placeOne(ServerPlayerEntity player, ScreenHandler handler, Slot slot) {
        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) return;

        ItemStack slotStack = slot.getStack();
        if (!slot.canInsert(cursor)) return;
        if (!slotStack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(slotStack, cursor)) return;

        int limit = Math.min(cursor.getCount(), slot.getMaxItemCount(cursor) - slotStack.getCount());
        if (limit <= 0) return;

        if (slotStack.isEmpty()) {
            ItemStack moved = cursor.copy();
            moved.setCount(1);
            slot.setStack(moved);
        } else {
            slotStack.increment(1);
            slot.markDirty();
        }
        cursor.decrement(1);
        handler.setCursorStack(cursor);
        handler.sendContentUpdates();
    }

    private static void pickupMatchIntoCursor(ServerPlayerEntity player, ScreenHandler handler, Slot slot) {
        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) return;

        ItemStack slotStack = slot.getStack();
        if (slotStack.isEmpty()) return;
        if (!slot.canTakeItems(player)) return;
        if (!ItemStack.areItemsAndComponentsEqual(slotStack, cursor)) return;

        int space = cursor.getMaxCount() - cursor.getCount();
        if (space <= 0) return;

        int moved = Math.min(space, slotStack.getCount());
        cursor.increment(moved);
        slotStack.decrement(moved);
        if (slotStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        handler.setCursorStack(cursor);
        handler.sendContentUpdates();
    }

    private static void shiftMatch(ServerPlayerEntity player, ScreenHandler handler, Slot slot) {
        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) return;

        ItemStack slotStack = slot.getStack();
        if (slotStack.isEmpty()) return;
        if (!ItemStack.areItemsAndComponentsEqual(slotStack, cursor)) return;

        if (slot.canTakeItems(player)) {
            handler.quickMove(player, slot.id);
            handler.sendContentUpdates();
        }
    }

    private static void quickMove(ServerPlayerEntity player, ScreenHandler handler, Slot slot) {
        if (slot.hasStack() && slot.canTakeItems(player)) {
            handler.quickMove(player, slot.id);
            handler.sendContentUpdates();
        }
    }

    private static void wheelPush(ServerPlayerEntity player, ScreenHandler handler, Slot fromSlot) {
        if (!fromSlot.hasStack()) return;
        Inventory sourceInv = fromSlot.inventory;
        Inventory targetInv = findOtherInventory(handler, sourceInv);
        if (targetInv == null) return;

        ItemStack fromStack = fromSlot.getStack();
        ItemStack moving = fromStack.copyWithCount(1);

        boolean inserted = insertIntoInventory(handler, targetInv, moving, true);
        if (!inserted) return;

        fromStack.decrement(1);
        if (fromStack.isEmpty()) {
            fromSlot.setStack(ItemStack.EMPTY);
        } else {
            fromSlot.markDirty();
        }
        handler.sendContentUpdates();
    }

    private static void wheelPull(ServerPlayerEntity player, ScreenHandler handler, Slot targetSlot) {
        Inventory targetInv = targetSlot.inventory;
        Inventory sourceInv = findOtherInventory(handler, targetInv);
        if (sourceInv == null) return;

        ItemStack targetStack = targetSlot.getStack();
        boolean matchRequired = !targetStack.isEmpty();
        ItemStack pulled = pullOneFromInventory(handler, sourceInv, targetStack);
        if (pulled.isEmpty()) return;

        if (targetStack.isEmpty()) {
            targetSlot.setStack(pulled);
        } else {
            targetStack.increment(1);
            targetSlot.markDirty();
        }
        handler.sendContentUpdates();
    }

    private static Inventory findOtherInventory(ScreenHandler handler, Inventory current) {
        for (Slot slot : handler.slots) {
            if (slot.inventory != current) {
                return slot.inventory;
            }
        }
        return null;
    }

    private static List<Slot> slotsForInventory(ScreenHandler handler, Inventory inv) {
        List<Slot> list = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot.inventory == inv) {
                list.add(slot);
            }
        }
        return list;
    }

    private static boolean insertIntoInventory(ScreenHandler handler, Inventory targetInv, ItemStack stack, boolean fromLast) {
        if (stack.isEmpty()) return true;

        List<Slot> slots = slotsForInventory(handler, targetInv);
        slots.sort(Comparator.comparingInt(Slot::getIndex));
        if (fromLast) {
            slots = new ArrayList<>(slots);
            java.util.Collections.reverse(slots);
        }

        // Merge with existing stacks first
        for (Slot slot : slots) {
            ItemStack slotStack = slot.getStack();
            if (slotStack.isEmpty()) continue;
            if (!slot.canInsert(stack)) continue;
            if (!ItemStack.areItemsAndComponentsEqual(slotStack, stack)) continue;

            int max = Math.min(slot.getMaxItemCount(stack), stack.getMaxCount());
            int space = max - slotStack.getCount();
            if (space <= 0) continue;

            int moved = Math.min(space, stack.getCount());
            slotStack.increment(moved);
            stack.decrement(moved);
            slot.markDirty();
            if (stack.isEmpty()) return true;
        }

        // Then empty slots
        for (Slot slot : slots) {
            if (!slot.getStack().isEmpty()) continue;
            if (!slot.canInsert(stack)) continue;

            int move = Math.min(stack.getMaxCount(), stack.getCount());
            ItemStack placed = stack.copy();
            placed.setCount(move);
            slot.setStack(placed);
            stack.decrement(move);
            if (stack.isEmpty()) return true;
        }

        return stack.isEmpty();
    }

    private static ItemStack pullOneFromInventory(ScreenHandler handler, Inventory sourceInv, ItemStack targetStack) {
        List<Slot> slots = slotsForInventory(handler, sourceInv);
        slots.sort(Comparator.comparingInt(Slot::getIndex).reversed()); // search from last by default

        for (Slot slot : slots) {
            ItemStack slotStack = slot.getStack();
            if (slotStack.isEmpty()) continue;

            if (!targetStack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(slotStack, targetStack)) {
                continue;
            }

            ItemStack extracted = slotStack.copyWithCount(1);
            slotStack.decrement(1);
            if (slotStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            return extracted;
        }
        return ItemStack.EMPTY;
    }
}
