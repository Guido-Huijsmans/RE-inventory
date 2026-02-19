package uberpookie.reinventory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import uberpookie.reinventory.mixin.HandledScreenAccessor;
import uberpookie.reinventory.network.MouseTweaksActionPayload;

@Environment(EnvType.CLIENT)
public final class MouseTweaksController {
    private static final MouseTweaksConfig CONFIG = new MouseTweaksConfig();

    private static boolean rmbDragActive;
    private static boolean lmbWithItemActive;
    private static boolean lmbWithItemShift;
    private static boolean lmbShiftNoItemActive;

    private static Slot lastRmbSlot;

    private MouseTweaksController() {}

    public static MouseTweaksConfig config() {
        return CONFIG;
    }

    public static boolean onMouseClicked(HandledScreen<?> screen, Click click, boolean doubleClick) {
        var handler = screen.getScreenHandler();
        Slot hovered = ((HandledScreenAccessor) screen).reinventory$getFocusedSlot();
        if (hovered == null) {
            clearStates();
            return false;
        }

        var cursor = handler.getCursorStack();
        boolean shift = isShiftDown();
        int button = click.button();

        if (button == 1 && CONFIG.rmbTweak && !cursor.isEmpty()) { // right button
            rmbDragActive = true;
            lastRmbSlot = null;
            send(hovered, MouseTweaksActionPayload.Action.RMB_PLACE_ONE, screen);
            return true;
        }

        if (button == 0 && CONFIG.lmbTweakWithoutItem && cursor.isEmpty() && shift) {
            lmbShiftNoItemActive = true;
            send(hovered, MouseTweaksActionPayload.Action.LMB_SHIFT_NO_ITEM, screen);
            return true;
        }

        if (button == 0 && CONFIG.lmbTweakWithItem && !cursor.isEmpty()) {
            lmbWithItemActive = true;
            lmbWithItemShift = shift;
            send(hovered, lmbWithItemShift
                    ? MouseTweaksActionPayload.Action.LMB_SHIFT_WITH_ITEM
                    : MouseTweaksActionPayload.Action.LMB_PICKUP_MATCH, screen);
            return true;
        }

        return false;
    }

    private static boolean isShiftDown() {
        var window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, 340) || InputUtil.isKeyPressed(window, 344);
    }

    public static boolean onMouseReleased(HandledScreen<?> screen, Click click) {
        clearStates();
        return false;
    }

    public static boolean onMouseDragged(HandledScreen<?> screen, Click click, double deltaX, double deltaY) {
        Slot hovered = ((HandledScreenAccessor) screen).reinventory$getFocusedSlot();
        if (hovered == null) return false;

        int button = click.button();

        if (rmbDragActive && button == 1) {
            if (hovered != lastRmbSlot) {
                send(hovered, MouseTweaksActionPayload.Action.RMB_PLACE_ONE, screen);
                lastRmbSlot = hovered;
            } else {
                send(hovered, MouseTweaksActionPayload.Action.RMB_PLACE_ONE, screen); // allow repeat on same slot
            }
            return true;
        }

        if (lmbWithItemActive && button == 0) {
            send(hovered, lmbWithItemShift
                    ? MouseTweaksActionPayload.Action.LMB_SHIFT_WITH_ITEM
                    : MouseTweaksActionPayload.Action.LMB_PICKUP_MATCH, screen);
            return true;
        }

        if (lmbShiftNoItemActive && button == 0) {
            send(hovered, MouseTweaksActionPayload.Action.LMB_SHIFT_NO_ITEM, screen);
            return true;
        }

        return false;
    }

    public static boolean onMouseScrolled(HandledScreen<?> screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!CONFIG.wheelTweak) return false;
        if (verticalAmount == 0) return false;

        Slot hovered = ((HandledScreenAccessor) screen).reinventory$getFocusedSlot();
        if (hovered == null) return false;

        boolean push; // move out of hovered
        int directionMode = CONFIG.wheelScrollDirectionMode;
        if (directionMode == 1) { // inverted
            push = verticalAmount > 0;
        } else if (directionMode == 2) { // position aware
            push = computePositionAwarePush(screen, hovered, verticalAmount);
        } else { // default
            push = verticalAmount < 0;
        }

        MouseTweaksActionPayload.Action action = push
                ? MouseTweaksActionPayload.Action.WHEEL_PUSH
                : MouseTweaksActionPayload.Action.WHEEL_PULL;

        send(hovered, action, screen);
        return true;
    }

    private static boolean computePositionAwarePush(HandledScreen<?> screen, Slot hovered, double verticalAmount) {
        int hoveredY = hovered.y;
        Integer otherY = null;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory != hovered.inventory) {
                otherY = (otherY == null) ? slot.y : Math.min(otherY, slot.y);
            }
        }
        if (otherY == null) {
            return verticalAmount < 0; // fallback to default
        }

        boolean otherAbove = otherY < hoveredY;
        if (verticalAmount > 0) { // scroll up
            return otherAbove;
        } else { // scroll down
            return !otherAbove;
        }
    }

    private static void send(Slot slot, MouseTweaksActionPayload.Action action, HandledScreen<?> screen) {
        var handler = screen.getScreenHandler();
        if (handler == null || slot == null) return;

        MouseTweaksActionPayload payload = new MouseTweaksActionPayload(handler.syncId, slot.id, action);
        ClientPlayNetworking.send(payload);
    }

    private static void clearStates() {
        rmbDragActive = false;
        lmbWithItemActive = false;
        lmbWithItemShift = false;
        lmbShiftNoItemActive = false;
        lastRmbSlot = null;
    }

    public static void loadConfig() {
        CONFIG.loadFromDisk();
    }

    public static void saveConfig() {
        CONFIG.saveToDisk();
    }
}
