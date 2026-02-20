package uberpookie.reinventory.slotlock;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Capability attached to {@link net.minecraft.screen.ScreenHandler} to track locked slot ids.
 */
public interface SlotLockState {
    boolean reinventory$isSlotLocked(int slotId);

    void reinventory$setSlotLocked(int slotId, boolean locked);

    IntSet reinventory$getLockedSlots();
}
