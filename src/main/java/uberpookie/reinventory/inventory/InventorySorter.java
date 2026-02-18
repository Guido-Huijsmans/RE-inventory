package uberpookie.reinventory.inventory;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Utility class for sorting inventory items in Creative mode order.
 * Items are ordered based on their registry order in Minecraft.
 */
public class InventorySorter {

    /**
     * Comparator for sorting items in Creative mode order.
     */
    public static int compareByCreativeOrder(ItemStack a, ItemStack b) {
        if (a.getItem() == b.getItem()) {
            return 0; // Same item type, no swap needed
        }

        int registryIndexA = getItemRegistryIndex(a);
        int registryIndexB = getItemRegistryIndex(b);
        return Integer.compare(registryIndexA, registryIndexB);
    }

    private static int getItemRegistryIndex(ItemStack stack) {
        if (stack.isEmpty()) {
            return Integer.MAX_VALUE; // Empty stacks go at the end
        }

        var item = stack.getItem();
        var index = Registries.ITEM.getRawId(item);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    /**
     * Sorts a segment of the inventory using bubble sort.
     */
    public static void sortInventoryArray(ItemStack[] inventory, int startSlot, int endSlot) {
        for (int i = startSlot; i < endSlot - 1; i++) {
            for (int j = startSlot; j < endSlot - 1 - (i - startSlot); j++) {
                if (compareByCreativeOrder(inventory[j], inventory[j + 1]) > 0) {
                    ItemStack temp = inventory[j].copy();
                    inventory[j] = inventory[j + 1].copy();
                    inventory[j + 1] = temp;
                }
            }
        }
    }

    /**
     * Determines if a sorting pass would change the inventory order.
     */
    public static boolean needsSorting(ItemStack[] inventory, int startSlot, int endSlot) {
        for (int i = startSlot; i < endSlot - 1; i++) {
            if (!inventory[i].isEmpty() && !inventory[i + 1].isEmpty()) {
                if (compareByCreativeOrder(inventory[i], inventory[i + 1]) > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
