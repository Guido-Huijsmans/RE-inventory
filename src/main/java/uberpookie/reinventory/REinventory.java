package uberpookie.reinventory;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uberpookie.reinventory.network.SortInventoryPayload;

public class REinventory implements ModInitializer {
   public static final String MOD_ID = "reinventory";
   public static final Logger LOGGER = LoggerFactory.getLogger("reinventory");

   public void onInitialize() {
      LOGGER.info("RE-inventory initialized!");
      SortInventoryPayload.register();
   }
}
