package uberpookie.reinventory.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import uberpookie.reinventory.mixin.HandledScreenAccessor;
import uberpookie.reinventory.network.SlotLockUpdatePayload;
import uberpookie.reinventory.slotlock.SlotLockManager;

@Environment(EnvType.CLIENT)
public final class SlotLockClient {
    private static KeyBinding modifierKey;
    private static boolean dragActive;
    private static int dragButton;
    private static final IntSet dragTouchedSlots = new IntOpenHashSet();

    private SlotLockClient() {}

    public static void initKeyBinding(KeyBinding.Category category) {
        modifierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.reinventory.slot_lock_modifier",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                category
        ));
    }

    public static boolean onMouseClicked(HandledScreen<?> screen, Click click) {
        if (!isModifierDown()) {
            return false;
        }

        Slot hovered = ((HandledScreenAccessor) screen).reinventory$getFocusedSlot();
        if (hovered == null || hovered.inventory == null) {
            return false;
        }

        ScreenHandler handler = screen.getScreenHandler();
        if (hovered.id < 0 || hovered.id >= handler.slots.size()) {
            return false;
        }

        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !SlotLockManager.isLockable(hovered, handler, player)) {
            return false;
        }

        dragActive = true;
        dragButton = click.button();
        dragTouchedSlots.clear();
        toggleLock(handler, hovered);
        dragTouchedSlots.add(hovered.id);
        return true; // consume click so the slot isn't interacted with
    }

    public static boolean onMouseDragged(HandledScreen<?> screen, Click click, double deltaX, double deltaY) {
        if (!dragActive || click.button() != dragButton || !isModifierDown()) {
            return false;
        }
        Slot hovered = ((HandledScreenAccessor) screen).reinventory$getFocusedSlot();
        if (hovered == null) return false;

        ScreenHandler handler = screen.getScreenHandler();
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !SlotLockManager.isLockable(hovered, handler, player)) {
            return false;
        }

        // Toggle once per slot while dragging to support sweeping lock/unlock
        if (dragTouchedSlots.add(hovered.id)) {
            toggleLock(handler, hovered);
        }
        return true;
    }

    public static boolean onMouseReleased(HandledScreen<?> screen, Click click) {
        dragActive = false;
        dragTouchedSlots.clear();
        return false;
    }

    public static void toggleLock(ScreenHandler handler, Slot slot) {
        boolean newState = !SlotLockManager.isLocked(handler, slot.id);
        setLockState(handler, slot, newState);
    }

    private static void setLockState(ScreenHandler handler, Slot slot, boolean state) {
        SlotLockManager.setLocked(handler, slot.id, state); // optimistic client-side update
        ClientPlayNetworking.send(new SlotLockUpdatePayload(handler.syncId, slot.id, state));
    }

    public static boolean isLocked(ScreenHandler handler, Slot slot) {
        return SlotLockManager.isLocked(handler, slot);
    }

    public static void drawLockOverlay(DrawContext context, ScreenHandler handler, Slot slot) {
        var player = MinecraftClient.getInstance().player;
        if (!isLocked(handler, slot) || player == null || !SlotLockManager.isLockable(slot, handler, player)) {
            return;
        }

        // Subtle red background (aligned to slot interior)
        int x = slot.x;
        int y = slot.y;
        int width = 16;
        int height = 16;
        int color = 0x30FF4A4A; // subtle semi-transparent red
        context.fill(x, y, x + width, y + height, color);
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

    public static void clearAll(ScreenHandler handler) {
        // optimistic clear client-side
        for (Slot slot : handler.slots) {
            SlotLockManager.setLocked(handler, slot.id, false);
        }
        ClientPlayNetworking.send(new uberpookie.reinventory.network.SlotLockClearPayload(handler.syncId));
    }
}
