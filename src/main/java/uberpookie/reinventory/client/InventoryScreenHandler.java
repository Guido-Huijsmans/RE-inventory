package uberpookie.reinventory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import uberpookie.reinventory.network.SortInventoryPayload;

@Environment(EnvType.CLIENT)
public class InventoryScreenHandler {
   public static void sortInventory() {
      ClientPlayNetworking.send(new SortInventoryPayload());
   }
}
