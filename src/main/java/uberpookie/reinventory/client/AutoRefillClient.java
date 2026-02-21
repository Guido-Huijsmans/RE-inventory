package uberpookie.reinventory.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import uberpookie.reinventory.network.AutoRefillPayload;

@Environment(EnvType.CLIENT)
public final class AutoRefillClient {
    private int lastSelectedSlot = -1;
    private ItemStack lastSelectedStack = ItemStack.EMPTY;
    private boolean requestedForCurrentEmptySlot;

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.currentScreen != null) {
            resetState();
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        ItemStack current = inventory.getStack(selectedSlot);

        if (shouldRequestRefill(selectedSlot, current)) {
            ClientPlayNetworking.send(new AutoRefillPayload(selectedSlot, lastSelectedStack.copy()));
            requestedForCurrentEmptySlot = true;
        }

        if (selectedSlot != lastSelectedSlot || !current.isEmpty()) {
            requestedForCurrentEmptySlot = false;
        }

        lastSelectedSlot = selectedSlot;
        lastSelectedStack = current.copy();
    }

    private boolean shouldRequestRefill(int selectedSlot, ItemStack currentStack) {
        if (requestedForCurrentEmptySlot) {
            return false;
        }
        if (selectedSlot != lastSelectedSlot) {
            return false;
        }
        if (lastSelectedStack.isEmpty()) {
            return false;
        }
        return currentStack.isEmpty();
    }

    private void resetState() {
        lastSelectedSlot = -1;
        lastSelectedStack = ItemStack.EMPTY;
        requestedForCurrentEmptySlot = false;
    }
}
