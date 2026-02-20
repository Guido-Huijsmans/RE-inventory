package uberpookie.reinventory.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.slotlock.SlotLockPersistence;

public record SlotLockSyncRequestPayload(int syncId) implements CustomPayload {
    public static final CustomPayload.Id<SlotLockSyncRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "slot_lock_sync_request"));

    public static final PacketCodec<PacketByteBuf, SlotLockSyncRequestPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, SlotLockSyncRequestPayload::syncId, SlotLockSyncRequestPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, SlotLockSyncRequestPayload::handle);
    }

    private static void handle(SlotLockSyncRequestPayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != payload.syncId()) {
            return;
        }
        SlotLockPersistence.applyPersistedLocks(handler, player);
        SlotLockSyncPayload.sendToPlayer(handler, player);
    }
}
