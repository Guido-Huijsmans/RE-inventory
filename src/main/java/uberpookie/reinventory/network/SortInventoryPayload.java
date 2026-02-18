package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.inventory.InventorySorter;

public record SortInventoryPayload() implements CustomPayload {
    public static final CustomPayload.Id<SortInventoryPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "sort_inventory"));

    public static final PacketCodec<PacketByteBuf, SortInventoryPayload> CODEC =
            PacketCodec.unit(new SortInventoryPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, SortInventoryPayload::handleSortRequest);
    }

    private static void handleSortRequest(SortInventoryPayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        var inventory = player.getInventory();

        ItemStack[] tempInventory = new ItemStack[27];
        for (int i = 0; i < tempInventory.length; i++) {
            tempInventory[i] = inventory.getStack(9 + i).copy();
        }

        InventorySorter.sortInventoryArray(tempInventory, 0, tempInventory.length);

        for (int i = 0; i < tempInventory.length; i++) {
            inventory.setStack(9 + i, tempInventory[i]);
        }
    }
}
