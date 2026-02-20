package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.inventory.InventorySorter;
import uberpookie.reinventory.slotlock.SlotLockManager;

import java.util.Comparator;
import java.util.List;

public record SortInventoryPayload(int syncId, int slotId) implements CustomPayload {
    public static final CustomPayload.Id<SortInventoryPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "sort_inventory"));

    public static final PacketCodec<PacketByteBuf, SortInventoryPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SortInventoryPayload::syncId,
                    PacketCodecs.VAR_INT, SortInventoryPayload::slotId,
                    SortInventoryPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, SortInventoryPayload::handleSortRequest);
    }

    private static void handleSortRequest(SortInventoryPayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;

        if (handler == null || handler.syncId != payload.syncId()) {
            return;
        }

        if (payload.slotId() < 0 || payload.slotId() >= handler.slots.size()) {
            return;
        }

        Slot targetSlot = handler.getSlot(payload.slotId());
        if (targetSlot == null || targetSlot.inventory == null) {
            return;
        }

        if (!isSortableSlot(targetSlot, handler, player)) {
            return;
        }

        Inventory targetInventory = targetSlot.inventory;
        List<Slot> targetSlots = handler.slots.stream()
                .filter(slot -> slot.inventory == targetInventory && isSortableSlot(slot, handler, player))
                .filter(slot -> !SlotLockManager.isLocked(handler, slot.id))
                .sorted(Comparator.comparingInt(Slot::getIndex))
                .toList();

        if (targetSlots.isEmpty()) {
            return;
        }

        ItemStack[] tempStacks = new ItemStack[targetSlots.size()];
        for (int i = 0; i < targetSlots.size(); i++) {
            tempStacks[i] = targetSlots.get(i).getStack().copy();
        }

        InventorySorter.sortInventoryArray(tempStacks, 0, tempStacks.length);

        for (int i = 0; i < targetSlots.size(); i++) {
            targetSlots.get(i).setStack(tempStacks[i]);
        }

        handler.sendContentUpdates();
    }

    private static boolean isSortableSlot(Slot slot, ScreenHandler handler, ServerPlayerEntity player) {
        Inventory inventory = slot.inventory;

        if (inventory instanceof PlayerInventory) {
            int index = slot.getIndex();
            return index >= 9 && index < 36; // only main inventory, not hotbar/equipment
        }

        boolean isContainerHandler =
                handler instanceof GenericContainerScreenHandler ||
                        handler instanceof Generic3x3ContainerScreenHandler ||
                        handler instanceof HopperScreenHandler ||
                        handler instanceof ShulkerBoxScreenHandler;

        if (isContainerHandler) {
            return true;
        }

        if (handler instanceof HorseScreenHandler) {
            // Chest slots start at index 2; skip saddle/armor slots
            return slot.getIndex() >= 2;
        }

        return false;
    }
}
