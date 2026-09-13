package dev.zeli.cleanbill;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public final class CleanBillData extends SavedData {
    public static final int POND_SIZE = 135;
    public static final int FILTERED_SIZE = 45;
    // Keep the original SavedData key so 0.1.1 upgrades retain timers and Item Pond contents.
    private static final String NAME = "quackyclean";

    public long nextCleanup = -1;
    public long remainingWhenPaused = -1;
    public long lastCleanup = -1;
    public long nextWash = -1;
    public boolean paused;
    public final List<ItemStack> pond = new ArrayList<>(POND_SIZE);
    public final List<ItemStack> filtered = new ArrayList<>(FILTERED_SIZE);

    public CleanBillData() {
        for (int i = 0; i < POND_SIZE; i++) pond.add(ItemStack.EMPTY);
        for (int i = 0; i < FILTERED_SIZE; i++) filtered.add(ItemStack.EMPTY);
    }

    public static CleanBillData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CleanBillData::new, CleanBillData::load), NAME);
    }

    private static CleanBillData load(CompoundTag tag, HolderLookup.Provider registries) {
        CleanBillData data = new CleanBillData();
        data.nextCleanup = tag.getLong("NextCleanup");
        data.remainingWhenPaused = tag.getLong("RemainingPaused");
        data.lastCleanup = tag.getLong("LastCleanup");
        data.nextWash = tag.getLong("NextWash");
        data.paused = tag.getBoolean("Paused");
        ListTag items = tag.getList("Pond", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < POND_SIZE) {
                data.pond.set(slot, ItemStack.parseOptional(registries, entry.getCompound("Stack")));
            }
        }
        ListTag filteredItems = tag.getList("Filtered", 10);
        for (int i = 0; i < filteredItems.size(); i++) {
            CompoundTag entry = filteredItems.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < FILTERED_SIZE) {
                data.filtered.set(slot, ItemStack.parseOptional(registries, entry.getCompound("Stack")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextCleanup", nextCleanup);
        tag.putLong("RemainingPaused", remainingWhenPaused);
        tag.putLong("LastCleanup", lastCleanup);
        tag.putLong("NextWash", nextWash);
        tag.putBoolean("Paused", paused);
        ListTag items = new ListTag();
        for (int slot = 0; slot < pond.size(); slot++) {
            ItemStack stack = pond.get(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", slot);
                entry.put("Stack", stack.saveOptional(registries));
                items.add(entry);
            }
        }
        tag.put("Pond", items);
        ListTag filteredItems = new ListTag();
        saveInventory(filteredItems, filtered, registries);
        tag.put("Filtered", filteredItems);
        return tag;
    }

    private static void saveInventory(ListTag target, List<ItemStack> inventory, HolderLookup.Provider registries) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", slot);
                entry.put("Stack", stack.saveOptional(registries));
                target.add(entry);
            }
        }
    }

    public int pondStackCount() {
        return (int) pond.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public int filteredStackCount() {
        return (int) filtered.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public boolean moveToFiltered(int pondSlot) {
        if (pondSlot < 0 || pondSlot >= pond.size() || pond.get(pondSlot).isEmpty()) return false;
        ItemStack original = pond.get(pondSlot);
        ItemStack moving = original.copy();
        if (!canFit(filtered, moving)) return false;
        addWithoutOverflow(filtered, moving);
        // Empty the shared stack object before replacing the slot so other open viewers cannot see a stale copy.
        original.setCount(0);
        pond.set(pondSlot, ItemStack.EMPTY);
        setDirty();
        return true;
    }

    private static boolean canFit(List<ItemStack> inventory, ItemStack offered) {
        int room = 0;
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) room += offered.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, offered)) room += stack.getMaxStackSize() - stack.getCount();
            if (room >= offered.getCount()) return true;
        }
        return false;
    }

    private static void addWithoutOverflow(List<ItemStack> inventory, ItemStack offered) {
        ItemStack moving = offered.copy();
        for (ItemStack stack : inventory) {
            if (!moving.isEmpty() && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, moving)) {
                int count = Math.min(moving.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(count);
                moving.shrink(count);
            }
        }
        for (int i = 0; i < inventory.size() && !moving.isEmpty(); i++) {
            if (inventory.get(i).isEmpty()) {
                int count = Math.min(moving.getCount(), moving.getMaxStackSize());
                inventory.set(i, moving.copyWithCount(count));
                moving.shrink(count);
            }
        }
    }

    public int clearPond() {
        int count = 0;
        for (int i = 0; i < pond.size(); i++) {
            ItemStack stack = pond.get(i);
            if (!stack.isEmpty()) count += stack.getCount();
            stack.setCount(0);
            pond.set(i, ItemStack.EMPTY);
        }
        setDirty();
        return count;
    }

    /** Adds a stack, merging first. If full, the oldest occupied slot is discarded and slots shift left. */
    public int addToPond(ItemStack offered) {
        ItemStack stack = offered.copy();
        for (int i = 0; i < pond.size() && !stack.isEmpty(); i++) {
            ItemStack existing = pond.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    stack.shrink(move);
                }
            }
        }
        int discarded = 0;
        while (!stack.isEmpty()) {
            int empty = -1;
            for (int i = 0; i < pond.size(); i++) if (pond.get(i).isEmpty()) { empty = i; break; }
            if (empty < 0) {
                ItemStack oldest = pond.getFirst();
                discarded += oldest.getCount();
                oldest.setCount(0);
                pond.removeFirst();
                pond.add(ItemStack.EMPTY);
                empty = pond.size() - 1;
            }
            int amount = Math.min(stack.getCount(), stack.getMaxStackSize());
            pond.set(empty, stack.copyWithCount(amount));
            stack.shrink(amount);
        }
        setDirty();
        return discarded;
    }
}
