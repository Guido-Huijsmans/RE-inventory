package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.slotlock.SlotLockManager;
import uberpookie.reinventory.slotlock.SlotLockPersistence;

public record SlotLockClearPayload(int syncId) implements CustomPayload {
    public static final CustomPayload.Id<SlotLockClearPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "slot_lock_clear"));

    public static final PacketCodec<PacketByteBuf, SlotLockClearPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, SlotLockClearPayload::syncId, SlotLockClearPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, SlotLockClearPayload::handle);
    }

    private static void handle(SlotLockClearPayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != payload.syncId()) {
            return;
        }

        for (Slot slot : handler.slots) {
            if (slot == null) continue;
            if (!SlotLockManager.isLocked(handler, slot.id)) continue;
            SlotLockManager.setLocked(handler, slot.id, false);
            SlotLockPersistence.recordLockChange(handler, player, slot, false);
        }

        SlotLockSyncPayload.sendToPlayer(handler, player);
    }
}
