package uberpookie.reinventory.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import uberpookie.reinventory.slotlock.SlotLockState;

@Mixin(ScreenHandler.class)
public class ScreenHandlerSlotLockMixin implements SlotLockState {
    private final IntSet reinventory$lockedSlots = new IntOpenHashSet();

    @Override
    public boolean reinventory$isSlotLocked(int slotId) {
        return reinventory$lockedSlots.contains(slotId);
    }

    @Override
    public void reinventory$setSlotLocked(int slotId, boolean locked) {
        if (locked) {
            reinventory$lockedSlots.add(slotId);
        } else {
            reinventory$lockedSlots.remove(slotId);
        }
    }

    @Override
    public IntSet reinventory$getLockedSlots() {
        return reinventory$lockedSlots;
    }
}
