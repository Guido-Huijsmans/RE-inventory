package uberpookie.reinventory.network;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import uberpookie.reinventory.REinventory;
import uberpookie.reinventory.slotlock.SlotLockManager;

public record SlotLockSyncPayload(int syncId, int[] lockedSlots) implements CustomPayload {
    public static final CustomPayload.Id<SlotLockSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(REinventory.MOD_ID, "slot_lock_sync"));

    public static final PacketCodec<PacketByteBuf, SlotLockSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public SlotLockSyncPayload decode(PacketByteBuf buf) {
            int syncId = PacketCodecs.VAR_INT.decode(buf);
            int len = PacketCodecs.VAR_INT.decode(buf);
            int[] arr = new int[len];
            for (int i = 0; i < len; i++) {
                arr[i] = PacketCodecs.VAR_INT.decode(buf);
            }
            return new SlotLockSyncPayload(syncId, arr);
        }

        @Override
        public void encode(PacketByteBuf buf, SlotLockSyncPayload payload) {
            PacketCodecs.VAR_INT.encode(buf, payload.syncId());
            int[] arr = payload.lockedSlots();
            PacketCodecs.VAR_INT.encode(buf, arr.length);
            for (int value : arr) {
                PacketCodecs.VAR_INT.encode(buf, value);
            }
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void registerS2C() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ID, SlotLockSyncPayload::handleClient);
    }

    public static void sendToPlayer(ScreenHandler handler, ServerPlayerEntity player) {
        IntList locked = new IntArrayList();
        for (int i = 0; i < handler.slots.size(); i++) {
            if (SlotLockManager.isLocked(handler, i)) {
                locked.add(i);
            }
        }
        ServerPlayNetworking.send(player, new SlotLockSyncPayload(handler.syncId, locked.toIntArray()));
    }

    @Environment(EnvType.CLIENT)
    private static void handleClient(SlotLockSyncPayload payload, ClientPlayNetworking.Context context) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        client.execute(() -> {
            var player = client.player;
            if (player == null) return;
            var handler = player.currentScreenHandler;
            if (handler == null || handler.syncId != payload.syncId()) {
                return;
            }

            // Clear then apply authoritative locks
            for (int i = 0; i < handler.slots.size(); i++) {
                SlotLockManager.setLocked(handler, i, false);
            }
            for (int slotId : payload.lockedSlots()) {
                if (slotId >= 0 && slotId < handler.slots.size()) {
                    SlotLockManager.setLocked(handler, slotId, true);
                }
            }
        });
    }
}
