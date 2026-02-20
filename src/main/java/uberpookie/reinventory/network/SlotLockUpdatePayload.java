package uberpookie.reinventory.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.slotlock.SlotLockManager;
import uberpookie.reinventory.slotlock.SlotLockPersistence;

/**
 * Syncs a slot lock toggle between client and server.
 */
public record SlotLockUpdatePayload(int syncId, int slotId, boolean locked) implements CustomPayload {
    public static final CustomPayload.Id<SlotLockUpdatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "slot_lock_update"));

    public static final PacketCodec<PacketByteBuf, SlotLockUpdatePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SlotLockUpdatePayload::syncId,
                    PacketCodecs.VAR_INT, SlotLockUpdatePayload::slotId,
                    PacketCodecs.BOOLEAN, SlotLockUpdatePayload::locked,
                    SlotLockUpdatePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void registerC2S() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, SlotLockUpdatePayload::handleServer);
    }

    @Environment(EnvType.CLIENT)
    public static void registerS2C() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ID, SlotLockUpdatePayload::handleClient);
    }

    private static void handleServer(SlotLockUpdatePayload payload, Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != payload.syncId()) {
            return;
        }

        if (payload.slotId() < 0 || payload.slotId() >= handler.slots.size()) {
            return;
        }

        var slot = handler.getSlot(payload.slotId());
        if (!SlotLockManager.isLockable(slot, handler, player)) {
            return;
        }

        SlotLockManager.setLocked(handler, payload.slotId(), payload.locked());
        SlotLockPersistence.recordLockChange(handler, player, slot, payload.locked());
        // Echo back to keep the client in sync with the authoritative state.
        ServerPlayNetworking.send(player, payload);
    }

    @Environment(EnvType.CLIENT)
    private static void handleClient(SlotLockUpdatePayload payload, ClientPlayNetworking.Context context) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        client.execute(() -> {
            var handler = client.player != null ? client.player.currentScreenHandler : null;
            if (handler == null || handler.syncId != payload.syncId()) {
                return;
            }
            SlotLockManager.setLocked(handler, payload.slotId(), payload.locked());
        });
    }
}
