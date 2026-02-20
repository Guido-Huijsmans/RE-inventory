package uberpookie.reinventory;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uberpookie.reinventory.network.MouseTweaksActionPayload;
import uberpookie.reinventory.network.QuickDepositPayload;
import uberpookie.reinventory.network.SortInventoryPayload;
import uberpookie.reinventory.network.SlotLockClearPayload;
import uberpookie.reinventory.network.SlotLockUpdatePayload;
import uberpookie.reinventory.network.SlotLockSyncRequestPayload;

public class REinventory implements ModInitializer {
   public static final String MOD_ID = "reinventory";
   public static final Logger LOGGER = LoggerFactory.getLogger("reinventory");

   public void onInitialize() {
      LOGGER.info("RE-inventory initialized!");
      SortInventoryPayload.register();
      MouseTweaksActionPayload.register();
      QuickDepositPayload.register();
      SlotLockUpdatePayload.registerC2S();
      SlotLockSyncRequestPayload.register();
      SlotLockClearPayload.register();
   }
}
