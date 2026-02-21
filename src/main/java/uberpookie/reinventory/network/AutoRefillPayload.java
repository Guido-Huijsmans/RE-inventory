package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;

import java.util.ArrayList;
import java.util.List;

public record AutoRefillPayload(int selectedSlot, ItemStack depletedStack) implements CustomPayload {
    public static final CustomPayload.Id<AutoRefillPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "auto_refill"));

    public static final PacketCodec<RegistryByteBuf, AutoRefillPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, AutoRefillPayload::selectedSlot,
                    ItemStack.PACKET_CODEC, AutoRefillPayload::depletedStack,
                    AutoRefillPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> process(context.player(), payload));
    }

    private static void process(ServerPlayerEntity player, AutoRefillPayload payload) {
        PlayerInventory inventory = player.getInventory();
        int activeSlot = payload.selectedSlot();
        ItemStack consumedTemplate = payload.depletedStack();
        if (activeSlot < 0 || activeSlot >= PlayerInventory.getHotbarSize()) {
            return;
        }
        if (consumedTemplate.isEmpty()) {
            return;
        }
        if (inventory.getSelectedSlot() != activeSlot) {
            return;
        }
        if (!inventory.getStack(activeSlot).isEmpty()) {
            return;
        }

        Integer hotbarMatch = findMatchingSlot(inventory, 0, PlayerInventory.getHotbarSize(), consumedTemplate, activeSlot);
        if (hotbarMatch != null) {
            selectHotbarSlot(player, hotbarMatch);
            return;
        }

        Integer inventoryMatch = findMatchingSlot(inventory, PlayerInventory.getHotbarSize(), PlayerInventory.MAIN_SIZE, consumedTemplate, -1);
        if (inventoryMatch != null) {
            inventory.setStack(activeSlot, inventory.getStack(inventoryMatch));
            inventory.setStack(inventoryMatch, ItemStack.EMPTY);
            inventory.markDirty();
            player.playerScreenHandler.sendContentUpdates();
            return;
        }

        ItemStack fromShulker = extractFromNestedStorage(inventory, consumedTemplate, true);
        if (!fromShulker.isEmpty()) {
            inventory.setStack(activeSlot, fromShulker);
            inventory.markDirty();
            player.playerScreenHandler.sendContentUpdates();
            return;
        }

        ItemStack fromBundle = extractFromNestedStorage(inventory, consumedTemplate, false);
        if (!fromBundle.isEmpty()) {
            inventory.setStack(activeSlot, fromBundle);
            inventory.markDirty();
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    private static void selectHotbarSlot(ServerPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(slot));
    }

    private static Integer findMatchingSlot(PlayerInventory inventory, int fromInclusive, int toExclusive, ItemStack match, int ignoredSlot) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            if (i == ignoredSlot) {
                continue;
            }
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (ItemStack.areItemsAndComponentsEqual(stack, match)) {
                return i;
            }
        }
        return null;
    }

    private static ItemStack extractFromNestedStorage(PlayerInventory inventory, ItemStack match, boolean shulkersFirstPass) {
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack containerStack = inventory.getStack(slot);
            if (containerStack.isEmpty()) {
                continue;
            }
            if (isShulkerBoxItem(containerStack) != shulkersFirstPass) {
                continue;
            }
            if (shulkersFirstPass) {
                ItemStack extracted = extractFromShulker(containerStack, match);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            } else if (containerStack.getItem() instanceof BundleItem) {
                ItemStack extracted = extractFromBundle(containerStack, match);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isShulkerBoxItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static ItemStack extractFromShulker(ItemStack shulkerStack, ItemStack match) {
        ContainerComponent container = shulkerStack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT);
        List<ItemStack> stacks = container.stream()
                .map(ItemStack::copy)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack nested = stacks.get(i);
            if (nested.isEmpty()) {
                continue;
            }
            if (!ItemStack.areItemsAndComponentsEqual(nested, match)) {
                continue;
            }
            ItemStack extracted = nested.copy();
            stacks.set(i, ItemStack.EMPTY);
            shulkerStack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
            return extracted;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractFromBundle(ItemStack bundleStack, ItemStack match) {
        BundleContentsComponent contents = bundleStack.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
        List<ItemStack> stacks = contents.stream()
                .map(ItemStack::copy)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack nested = stacks.get(i);
            if (!ItemStack.areItemsAndComponentsEqual(nested, match)) {
                continue;
            }
            ItemStack extracted = nested.copy();
            stacks.remove(i);
            bundleStack.set(DataComponentTypes.BUNDLE_CONTENTS, stacks.isEmpty() ? BundleContentsComponent.DEFAULT : new BundleContentsComponent(stacks));
            return extracted;
        }
        return ItemStack.EMPTY;
    }
}
