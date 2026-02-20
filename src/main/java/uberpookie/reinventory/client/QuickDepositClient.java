package uberpookie.reinventory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import uberpookie.reinventory.mixin.HandledScreenAccessor;
import uberpookie.reinventory.network.QuickDepositPayload;

@Environment(EnvType.CLIENT)
public final class QuickDepositClient {
    private static KeyBinding modifierKey;

    private QuickDepositClient() {}

    public static void initKeyBinding(KeyBinding.Category category) {
        modifierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.reinventory.quick_deposit_modifier",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_CONTROL,
                category
        ));
    }

    public static boolean onMouseClicked(HandledScreen<?> screen, Click click) {
        if (!isModifierDown()) {
            return false;
        }

        Slot hovered = ((HandledScreenAccessor) screen).reinventory$getFocusedSlot();
        if (hovered == null || hovered.inventory == null || !hovered.hasStack()) {
            return false;
        }

        ScreenHandler handler = screen.getScreenHandler();
        if (handler instanceof PlayerScreenHandler) {
            return false; // nothing to deposit into on the plain inventory screen
        }
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return false;
        }
        Inventory playerInventory = player.getInventory();
        if (hovered.id < 0 || hovered.id >= handler.slots.size()) {
            return false;
        }

        Inventory target = findTargetInventory(handler, hovered.inventory, playerInventory);
        if (target == null) {
            return false;
        }

        ClientPlayNetworking.send(new QuickDepositPayload(handler.syncId, hovered.id));
        return true;
    }

    private static boolean isModifierDown() {
        if (modifierKey == null) {
            return false;
        }
        var client = MinecraftClient.getInstance();
        var window = client.getWindow();
        var boundKey = KeyBindingHelper.getBoundKeyOf(modifierKey);
        if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window.getHandle(), boundKey.getCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(window, boundKey.getCode());
    }

    private static Inventory findTargetInventory(ScreenHandler handler, Inventory source, Inventory playerInv) {
        if (source == playerInv) {
            return findFirstNonPlayerInventory(handler, playerInv);
        }
        return playerInv;
    }

    private static Inventory findFirstNonPlayerInventory(ScreenHandler handler, Inventory playerInv) {
        for (Slot slot : handler.slots) {
            if (slot.inventory != null && slot.inventory != playerInv) {
                return slot.inventory;
            }
        }
        return null;
    }
}
