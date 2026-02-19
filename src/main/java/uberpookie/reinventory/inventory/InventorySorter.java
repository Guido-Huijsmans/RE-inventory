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
        consolidateStacks(inventory, startSlot, endSlot);

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

    /**
     * Merge compatible stacks so sorting doesn't leave partial stacks scattered.
     */
    public static void consolidateStacks(ItemStack[] inventory, int startSlot, int endSlot) {
        ItemStack[] consolidated = new ItemStack[endSlot - startSlot];
        int consolidatedIndex = 0;

        for (int i = startSlot; i < endSlot; i++) {
            ItemStack current = inventory[i];
            if (current.isEmpty()) {
                continue;
            }

            ItemStack remaining = current.copy();
            for (int j = 0; j < consolidatedIndex && !remaining.isEmpty(); j++) {
                ItemStack target = consolidated[j];
                if (ItemStack.areItemsAndComponentsEqual(target, remaining) && target.getCount() < target.getMaxCount()) {
                    int transferAmount = Math.min(target.getMaxCount() - target.getCount(), remaining.getCount());
                    target.increment(transferAmount);
                    remaining.decrement(transferAmount);
                }
            }

            if (!remaining.isEmpty()) {
                consolidated[consolidatedIndex++] = remaining;
            }
        }

        for (int i = 0; i < endSlot - startSlot; i++) {
            inventory[startSlot + i] = i < consolidatedIndex ? consolidated[i] : ItemStack.EMPTY;
        }
    }
}
