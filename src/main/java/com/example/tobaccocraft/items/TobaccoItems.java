package com.example.tobaccocraft.items;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.catalog.CatalogManager;
import com.example.tobaccocraft.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

@SuppressWarnings("deprecation")
public final class TobaccoItems implements Listener {
    public static final String SEEDS = "seeds", LEAF = "leaf", SHEARS = "shears", CIGARETTE = "cigarette", MACHINE = "machine";
    private static final Map<String, Material> TYPES = Map.of(SEEDS, Material.WHEAT_SEEDS, LEAF, Material.GREEN_DYE,
            SHEARS, Material.SHEARS, CIGARETTE, Material.PAPER, MACHINE, Material.SMOKER);
    private static final Map<String, Integer> MODELS = Map.of(SEEDS, 1002, LEAF, 1003, SHEARS, 1001, CIGARETTE, 2001, MACHINE, 3001);
    private final TobaccoCraft plugin;
    private final NamespacedKey itemKey, varietyKey, recipeKey;

    public TobaccoItems(TobaccoCraft plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "item_type");
        varietyKey = new NamespacedKey(plugin, "variety_id");
        recipeKey = new NamespacedKey(plugin, "recipe_id");
    }

    public ItemStack create(String id) { return create(id, 1); }
    public ItemStack create(String id, int amount) { return create(id, amount, CatalogManager.DEFAULT); }
    public ItemStack create(String id, int amount, String variant) {
        Material material = TYPES.get(id);
        if (material == null) throw new IllegalArgumentException("Неизвестный предмет: " + id);
        if (amount < 1 || amount > material.getMaxStackSize()) throw new IllegalArgumentException("Некорректный размер стака.");
        if (variant == null) variant = CatalogManager.DEFAULT;
        var builder = new ItemBuilder(material).model(MODELS.get(id)).tag(itemKey, id);
        switch (id) {
            case SEEDS, LEAF -> {
                var variety = plugin.catalog().variety(variant);
                if (variety == null) throw new IllegalArgumentException("Сорт табака не найден.");
                builder.name(id.equals(SEEDS) ? variety.seedsName() : variety.leafName())
                        .lore((id.equals(SEEDS) ? variety.seedsLore() : variety.leafLore()).toArray(String[]::new)).tag(varietyKey, variant);
            }
            case CIGARETTE -> {
                var recipe = plugin.catalog().recipe(variant);
                if (recipe == null) throw new IllegalArgumentException("Вид сигареты не найден.");
                builder.name(recipe.name()).lore(recipe.lore().toArray(String[]::new)).tag(recipeKey, variant);
            }
            case SHEARS -> builder.name("§aНожницы для табака").lore("§7ПКМ по взрослому кусту — начать сбор");
            case MACHINE -> builder.name("§6Табачный станок").lore("§7Поставьте и ПКМ для крафта сигарет");
            default -> throw new IllegalArgumentException("Неизвестный предмет.");
        }
        var item = builder.build(); item.setAmount(amount); return item;
    }

    public String varietyId(ItemStack item) { return tag(item, varietyKey); }
    public String recipeId(ItemStack item) { return tag(item, recipeKey); }
    private String tag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return CatalogManager.DEFAULT;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.STRING, CatalogManager.DEFAULT);
    }
    public boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta() || item.getType() != TYPES.get(id)) return false;
        var meta = item.getItemMeta();
        if (!id.equals(meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING))
                || !meta.hasCustomModelData() || meta.getCustomModelData() != MODELS.get(id)) return false;
        if ((id.equals(SEEDS) || id.equals(LEAF)) && plugin.catalog().variety(varietyId(item)) == null) return false;
        return !id.equals(CIGARETTE) || plugin.catalog().recipe(recipeId(item)) != null;
    }
    public boolean isCustom(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }
    public static boolean isPlainPaper(ItemStack item) { return item != null && item.getType() == Material.PAPER && !item.hasItemMeta(); }

    /** Обновляем подписи старых предметов, сохраняя прочность, зачарования и сторонние метки. */
    public ItemStack refreshItem(ItemStack item) {
        if (!isCustom(item)) return item;
        String id = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (!is(item, id)) return item;
        String variant = id.equals(CIGARETTE) ? recipeId(item) : varietyId(item);
        var template = create(id, 1, variant).getItemMeta();
        ItemStack updated = item.clone(); var meta = updated.getItemMeta();
        meta.setDisplayName(template.getDisplayName()); meta.setLore(template.getLore());
        if (id.equals(CIGARETTE)) meta.getPersistentDataContainer().set(recipeKey, PersistentDataType.STRING, variant);
        if (id.equals(SEEDS) || id.equals(LEAF)) meta.getPersistentDataContainer().set(varietyKey, PersistentDataType.STRING, variant);
        updated.setItemMeta(meta); return updated;
    }
    public void refreshPlayer(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, refreshItem(inventory.getItem(slot)));
        player.setItemOnCursor(refreshItem(player.getItemOnCursor()));
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { refreshPlayer(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        var inventory = event.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isCustom(item)) inventory.setItem(slot, refreshItem(item));
        }
    }
    public static void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
