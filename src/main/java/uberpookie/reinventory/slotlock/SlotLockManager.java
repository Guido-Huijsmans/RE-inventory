package uberpookie.reinventory.slotlock;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

/**
 * Shared helpers for reading/writing slot lock state on a {@link ScreenHandler}.
 */
public final class SlotLockManager {
    private SlotLockManager() {}

    // Persist within a play session; keyed by inventory instance (block entities reuse the same Inventory)
    private static final Map<Inventory, IntSet> PERSISTENT_LOCKS =
            Collections.synchronizedMap(new HashMap<>());

    public static boolean isLocked(ScreenHandler handler, int slotId) {
        if (handler instanceof SlotLockState state && state.reinventory$isSlotLocked(slotId)) {
            return true;
        }
        return isPersistentlyLocked(handler, slotId);
    }

    public static boolean isLocked(ScreenHandler handler, Slot slot) {
        return slot != null && isLocked(handler, slot.id);
    }

    public static boolean isLockable(Slot slot, ScreenHandler handler, PlayerEntity player) {
        if (slot == null || slot.inventory == null) {
            return false;
        }

        if (slot.inventory instanceof PlayerInventory) {
            int index = slot.getIndex();
            return index >= 9 && index < 36; // main inventory only
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
            return slot.getIndex() >= 2; // skip saddle/armor
        }

        return false;
    }

    public static void setLocked(ScreenHandler handler, int slotId, boolean locked) {
        if (!(handler instanceof SlotLockState state)) {
            return;
        }
        state.reinventory$setSlotLocked(slotId, locked);

        Slot slot = handler.getSlot(slotId);
        if (slot != null && slot.inventory != null) {
            IntSet set = PERSISTENT_LOCKS.computeIfAbsent(slot.inventory, inv -> new IntOpenHashSet());
            if (locked) {
                set.add(slot.getIndex());
            } else {
                set.remove(slot.getIndex());
                if (set.isEmpty()) {
                    PERSISTENT_LOCKS.remove(slot.inventory);
                }
            }
        }
    }

    public static IntSet getLocked(ScreenHandler handler) {
        if (handler instanceof SlotLockState state) {
            return state.reinventory$getLockedSlots();
        }
        return null;
    }

    private static boolean isPersistentlyLocked(ScreenHandler handler, int slotId) {
        if (slotId < 0 || slotId >= handler.slots.size()) {
            return false;
        }
        Slot slot = handler.getSlot(slotId);
        if (slot == null || slot.inventory == null) {
            return false;
        }
        IntSet set = PERSISTENT_LOCKS.get(slot.inventory);
        if (set == null) {
            return false;
        }
        return set.contains(slot.getIndex());
    }
}
