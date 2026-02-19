package uberpookie.reinventory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import uberpookie.reinventory.mixin.HandledScreenAccessor;
import uberpookie.reinventory.network.SortInventoryPayload;

@Environment(EnvType.CLIENT)
public class InventoryScreenHandler {
   public static void sortInventory() {
      var client = MinecraftClient.getInstance();
      if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
         return;
      }

      Slot focusedSlot = ((HandledScreenAccessor) handledScreen).reinventory$getFocusedSlot();
      if (focusedSlot == null || focusedSlot.inventory == null) {
         return;
      }

      var handler = handledScreen.getScreenHandler();
      if (focusedSlot.id < 0 || focusedSlot.id >= handler.slots.size()) {
         return;
      }

      ClientPlayNetworking.send(new SortInventoryPayload(handler.syncId, focusedSlot.id));
   }
}
