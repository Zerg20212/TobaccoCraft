package com.example.tobaccocraft.catalog;

import com.example.tobaccocraft.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public record OverdoseSettings(int threshold, int windowSeconds, double damage, List<EffectSpec> effects,
                               String message, Material dropMaterial, int dropAmount, String dropName,
                               List<String> dropLore, int dropModel) {
    public OverdoseSettings {
        if (threshold < 1 || threshold > 1000 || windowSeconds < 1 || windowSeconds > 86400
                || !Double.isFinite(damage) || damage < 0 || damage > 2048)
            throw new IllegalArgumentException("Порог — 1–1000 сигарет; окно — 1–86400 секунд; урон — 0–2048.");
        effects = List.copyOf(effects);
        CatalogManager.checkEffects(effects);
        if (message == null || message.length() > 250) throw new IllegalArgumentException("Сообщение — до 250 символов.");
        if (dropMaterial == null || (dropMaterial != Material.AIR && !dropMaterial.isItem()))
            throw new IllegalArgumentException("Выберите материал предмета.");
        if (dropAmount < 1 || dropAmount > (dropMaterial == Material.AIR ? 64 : dropMaterial.getMaxStackSize()) || dropModel < 0)
            throw new IllegalArgumentException("Количество превышает размер стака или указан отрицательный номер модели.");
        CatalogManager.checkText(dropName, 80);
        dropLore = CatalogManager.checkLore(dropLore);
    }
    public ItemStack createDrop() {
        if (dropMaterial == Material.AIR) return null;
        var builder = new ItemBuilder(dropMaterial).name(dropName).lore(dropLore.toArray(String[]::new));
        if (dropModel > 0) builder.model(dropModel);
        var item = builder.build(); item.setAmount(dropAmount);
        return item;
    }
}
