package uberpookie.reinventory.slotlock;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simple JSON file persistence for slot locks per player.
 * Stores: player main-inventory locks and container locks keyed by dimension+BlockPos.
 */
public final class SlotLockPersistence {
    private static final Gson GSON = new Gson();
    private static final Type SAVE_TYPE = new TypeToken<SaveData>() {}.getType();

    private SlotLockPersistence() {}

    public static void applyPersistedLocks(ScreenHandler handler, ServerPlayerEntity player) {
        SaveData data = load(player.getUuid());
        if (data == null) return;

        for (Slot slot : handler.slots) {
            if (slot == null || slot.inventory == null) continue;
            int idx = slot.getIndex();

            if (slot.inventory == player.getInventory()) {
                if (data.playerInvLocks.contains(idx)) {
                    SlotLockManager.setLocked(handler, slot.id, true);
                }
                continue;
            }

            if (slot.inventory instanceof BlockEntity be) {
                World world = be.getWorld();
                if (world == null) continue;
                Identifier dimId = world.getRegistryKey().getValue();
                String key = containerKey(dimId, be.getPos());
                var locks = data.containerLocks.get(key);
                if (locks != null && locks.contains(idx)) {
                    SlotLockManager.setLocked(handler, slot.id, true);
                }
            }
        }
    }

    public static void recordLockChange(ScreenHandler handler, ServerPlayerEntity player, Slot slot, boolean locked) {
        SaveData data = loadOrNew(player.getUuid());
        int idx = slot.getIndex();

        if (slot.inventory == player.getInventory()) {
            updateList(data.playerInvLocks, idx, locked);
        } else if (slot.inventory instanceof BlockEntity be) {
            World world = be.getWorld();
            if (world == null) return;
            Identifier dimId = world.getRegistryKey().getValue();
            String key = containerKey(dimId, be.getPos());
            var list = data.containerLocks.computeIfAbsent(key, k -> new ArrayList<>());
            updateList(list, idx, locked);
            if (list.isEmpty()) {
                data.containerLocks.remove(key);
            }
        } else {
            // Non-persisted inventory types are ignored
        }

        save(player.getUuid(), data);
    }

    private static void updateList(List<Integer> list, int value, boolean add) {
        list.removeIf(v -> v == value);
        if (add) {
            list.add(value);
        }
    }

    private static String containerKey(Identifier dim, BlockPos pos) {
        return dim.toString() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static SaveData load(UUID playerId) {
        Path path = filePath(playerId);
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, SAVE_TYPE);
        } catch (IOException e) {
            return null;
        }
    }

    private static SaveData loadOrNew(UUID playerId) {
        SaveData data = load(playerId);
        return data != null ? data : new SaveData();
    }

    private static void save(UUID playerId, SaveData data) {
        Path path = filePath(playerId);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, SAVE_TYPE, writer);
            }
        } catch (IOException e) {
            // ignore persistence errors to avoid impacting gameplay
        }
    }

    private static Path filePath(UUID playerId) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("reinventory")
                .resolve("slotlocks")
                .resolve(playerId.toString() + ".json");
    }

    private static class SaveData {
        List<Integer> playerInvLocks = new ArrayList<>();
        Map<String, List<Integer>> containerLocks = new HashMap<>();
    }
}
