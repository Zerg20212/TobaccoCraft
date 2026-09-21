package com.example.tobaccocraft.utils;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/** Небольшая фабрика предметов; строковые цвета намеренно используют §. */
@SuppressWarnings("deprecation")
public final class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        item = new ItemStack(material);
        meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) { meta.setDisplayName(name); return this; }
    public ItemBuilder lore(String... lore) { meta.setLore(Arrays.asList(lore)); return this; }
    public ItemBuilder model(int model) { meta.setCustomModelData(model); return this; }
    public ItemBuilder tag(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }
    public ItemStack build() { item.setItemMeta(meta); return item.clone(); }
}
