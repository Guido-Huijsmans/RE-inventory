package uberpookie.reinventory.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import uberpookie.reinventory.REinventory;

@Environment(EnvType.CLIENT)
public class REinventoryClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(REinventory.MOD_ID, "category.reinventory"));
    private KeyBinding sortKey;
    private boolean sortKeyHeld;

    @Override
    public void onInitializeClient() {
        sortKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.reinventory.sort_inventory",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_3,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!(client.currentScreen instanceof HandledScreen<?>)) {
                sortKeyHeld = false;
                return;
            }

            var boundKey = KeyBindingHelper.getBoundKeyOf(sortKey);
            var window = client.getWindow();
            boolean pressed;
            if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
                pressed = GLFW.glfwGetMouseButton(window.getHandle(), boundKey.getCode()) == GLFW.GLFW_PRESS;
            } else {
                pressed = InputUtil.isKeyPressed(window, boundKey.getCode());
            }

            if (pressed && !sortKeyHeld) {
                InventoryScreenHandler.sortInventory();
            }
            sortKeyHeld = pressed;
        });
    }
}
