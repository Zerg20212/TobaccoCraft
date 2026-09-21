package com.example.tobaccocraft.catalog;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Точный образец ингредиента: материал, метаданные и PDC, без доступа к изменяемому оригиналу. */
public final class RecipeIngredient {
    private final ItemStack sample;

    public RecipeIngredient(ItemStack sample) {
        if (sample == null || sample.getType().isAir() || !sample.getType().isItem()
                || sample.getAmount() < 1 || sample.getAmount() > sample.getMaxStackSize())
            throw new IllegalArgumentException("Ингредиент: непустой предмет, количество от 1 до размера стака.");
        this.sample = sample.clone();
    }

    public ItemStack sample() { return sample.clone(); }
    public int amount() { return sample.getAmount(); }
    public boolean matches(ItemStack item) { return item != null && item.getAmount() > 0 && sample.isSimilar(item); }

    public Material remainder() {
        return switch (sample.getType()) {
            case MILK_BUCKET, WATER_BUCKET, LAVA_BUCKET -> Material.BUCKET;
            case HONEY_BOTTLE, POTION -> Material.GLASS_BOTTLE;
            default -> Material.AIR;
        };
    }
}
