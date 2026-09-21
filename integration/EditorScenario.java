package com.example.tobaccocraft.integration;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.catalog.CigaretteRecipe;
import com.example.tobaccocraft.catalog.RecipeIngredient;
import com.example.tobaccocraft.catalog.TobaccoVariety;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.listeners.SmokeListener;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.Consumer;

/** Прогон редактора через настоящие события кликов/чата с проверочным игроком. */
public final class EditorScenario {
    private final JavaPlugin probe;
    private final TobaccoCraft plugin;
    private final Runnable success;
    private final Consumer<Throwable> failure;
    private final Queue<Runnable> steps = new ArrayDeque<>();
    private IntegrationPlayer user;
    private String varietyId, recipeId;
    private final String suffix = Long.toString(System.nanoTime());
    private final String varietyName = "§aСорт " + suffix, recipeName = "§6Сигарета " + suffix;

    public EditorScenario(JavaPlugin probe, TobaccoCraft plugin, Runnable success, Consumer<Throwable> failure) {
        this.probe = probe; this.plugin = plugin; this.success = success; this.failure = failure;
    }
    public void start() {
        user = new IntegrationPlayer(plugin, new Location(Bukkit.getWorlds().getFirst(), 48, 100, 48));
        steps.add(() -> { plugin.getCommand("tobacco").execute(user.player, "tobacco", new String[]{"editor"}); check(top().getSize() == 54, "Команда открыла GUI"); click(20); });
        steps.add(() -> click(49));
        steps.add(() -> chat("&aСорт " + suffix));
        steps.add(() -> {
            varietyId = plugin.catalog().varieties().stream().filter(v -> v.name().equals(varietyName)).findFirst().orElseThrow().id();
            check(plugin.catalog().variety(varietyId) != null, "Сорт создан через GUI и чат");
            click(plugin.catalog().varieties().indexOf(plugin.catalog().variety(varietyId)));
        });
        steps.add(() -> click(37));
        steps.add(() -> chat("0.1"));
        steps.add(() -> click(39));
        steps.add(() -> chat("0.05"));
        steps.add(() -> click(34));
        steps.add(() -> chat("25.5"));
        steps.add(() -> {
            check(plugin.catalog().variety(varietyId).growthMillis() == 6000 && plugin.catalog().variety(varietyId).wiltMillis() == 3000, "Сроки роста и увядания настроены через GUI");
            check(plugin.catalog().variety(varietyId).seedsChance() == 25.5, "Шанс семян настроен через GUI и чат");
            plugin.editor().open(user.player); click(24);
        });
        steps.add(() -> click(49));
        steps.add(() -> chat("&6Сигарета " + suffix));
        steps.add(() -> {
            var recipe = plugin.catalog().recipes().stream().filter(r -> r.name().equals(recipeName)).findFirst().orElseThrow();
            recipeId = recipe.id(); click(plugin.catalog().recipes().indexOf(recipe));
        });
        steps.add(() -> click(12));
        steps.add(() -> chat("&7Моё описание | &bВторая строка"));
        steps.add(() -> click(14));
        steps.add(() -> click(plugin.catalog().varieties().indexOf(plugin.catalog().variety(varietyId))));
        steps.add(() -> click(31));
        steps.add(() -> {
            user.inventory.setItem(9, new ItemStack(Material.LAPIS_LAZULI, 2));
            click(54); // Верхнее окно 54, первый слот нижней части соответствует слоту 9 игрока.
        });
        steps.add(() -> { user.inventory.setItemInMainHand(new ItemStack(Material.GLOW_BERRIES, 3)); click(22); });
        steps.add(() -> { check(plugin.catalog().recipe(recipeId).ingredients().size() == 2, "Две добавки скопированы из инвентаря и руки"); click(10); });
        steps.add(() -> click(22));
        steps.add(() -> chat("4"));
        steps.add(() -> {
            check(plugin.catalog().recipe(recipeId).ingredients().getFirst().amount() == 4, "Количество добавки изменено через чат");
            check(user.inventory.getItem(9).getAmount() == 2, "Образец добавки не расходуется"); click(49);
        });
        steps.add(() -> click(29));
        steps.add(() -> click(find("Скорость")));
        steps.add(() -> click(20));
        steps.add(() -> click(22));
        steps.add(() -> chat("2"));
        steps.add(() -> click(24));
        steps.add(() -> chat("90"));
        steps.add(() -> {
            var r = plugin.catalog().recipe(recipeId);
            check(r.varietyId().equals(varietyId) && r.lore().equals(List.of("§7Моё описание", "§bВторая строка")), "Сорт и своё описание сохранены");
            check(r.effects().size() == 1 && r.effects().getFirst().key().equals("speed") && r.effects().getFirst().level() == 2
                    && r.effects().getFirst().seconds() == 90, "Уровень и длительность эффекта настроены в GUI");
            click(49);
        });
        steps.add(() -> click(48));
        steps.add(() -> click(33));
        steps.add(() -> click(10));
        steps.add(() -> chat("2"));
        steps.add(() -> click(14));
        steps.add(() -> chat("4"));
        steps.add(() -> click(16));
        steps.add(() -> click(find("Замедление")));
        steps.add(() -> click(20));
        steps.add(() -> click(49));
        steps.add(() -> click(48));
        steps.add(() -> click(33));
        steps.add(() -> { user.inventory.setItemInMainHand(new ItemStack(Material.COAL, 2)); click(10); });
        steps.add(() -> click(14));
        steps.add(() -> chat("&8Мой пепел"));
        steps.add(() -> click(16));
        steps.add(() -> chat("&7Своё описание пепла"));
        steps.add(() -> {
            var o = plugin.catalog().recipe(recipeId).overdose();
            check(o.threshold() == 2 && o.damage() == 4 && o.effects().size() == 1, "Порог, урон и эффекты перекуривания настроены");
            check(o.dropMaterial() == Material.COAL && o.dropAmount() == 2 && o.dropName().equals("§8Мой пепел")
                    && o.dropLore().equals(List.of("§7Своё описание пепла")), "Предмет перекуривания настроен через GUI");
            check(user.inventory.getItemInMainHand().getAmount() == 2, "Образец из руки не расходуется");
            user.player.closeInventory(); smoke(); craft(); extraCraft(); legacy(); lifecycle();
            try { plugin.reloadData(); } catch (Exception e) { throw new RuntimeException(e); }
            check(plugin.catalog().recipe(recipeId).overdose().dropName().equals("§8Мой пепел"), "Каталог восстановлен из файла");
            check(plugin.catalog().recipe(recipeId).ingredients().size() == 2 && plugin.catalog().recipe(recipeId).ingredients().getFirst().amount() == 4, "Добавки восстановлены из YAML");
            check(plugin.catalog().variety(varietyId).growthMillis() == 6000 && plugin.catalog().variety(varietyId).wiltMillis() == 3000, "Сроки роста восстановлены из YAML");
            check(plugin.catalog().variety(varietyId).seedsChance() == 25.5, "Шанс семян восстановлен из YAML");
            plugin.editor().open(user.player); click(20);
        });
        steps.add(() -> click(49));
        steps.add(() -> chat("отмена"));
        steps.add(() -> {
            check(plugin.catalog().varieties().stream().filter(v -> v.name().equals(varietyName)).count() == 1, "Отмена ввода не создаёт сорт");
            var click = new InventoryClickEvent(user.view, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
            plugin.editor().onClick(click); check(click.isCancelled(), "Горячие клавиши в редакторе не выдают образцы");
            user.player.closeInventory(); user.admin = false; plugin.editor().open(user.player);
            check(top().getSize() != 54, "Редактор недоступен без права администратора");
            check(user.messages.stream().noneMatch(m -> m.contains("[TobaccoCraft]")), "В сообщениях нет префикса");
        });
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    if (steps.isEmpty()) { cancel(); success.run(); }
                    else steps.remove().run();
                } catch (Throwable error) { cancel(); failure.accept(error); }
            }
        }.runTaskTimer(probe, 1, 3);
    }

    private Inventory top() { return user.view.getTopInventory(); }
    private int find(String name) {
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = top().getItem(slot);
            if (item != null && item.hasItemMeta() && name.equals(ChatColor.stripColor(item.getItemMeta().getDisplayName()))) return slot;
        }
        throw new AssertionError("Кнопка не найдена: " + name);
    }
    private void click(int slot) {
        var event = new InventoryClickEvent(user.view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        plugin.editor().onClick(event); check(event.isCancelled(), "Клик редактора отменяет перенос предметов");
    }
    private void chat(String value) {
        Component text = Component.text(value);
        var event = new AsyncChatEvent(false, user.player, new HashSet<>(), ChatRenderer.defaultRenderer(), text, text, SignedMessage.system(value, text));
        plugin.editor().onChat(event); check(event.isCancelled(), "Ввод редактора не попадает в общий чат");
    }
    private void smoke() {
        user.inventory.clear(); user.inventory.setItemInMainHand(plugin.items().create(TobaccoItems.CIGARETTE, 2, recipeId));
        var listener = new SmokeListener(plugin);
        try {
            for (int i = 0; i < 2; i++) listener.onSmoke(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_AIR,
                    user.inventory.getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND));
        } finally { listener.shutdown(); }
        check(user.damage == 4, "Настроенный урон применён на второй сигарете");
        check(user.effects.stream().anyMatch(e -> e.getType().equals(PotionEffectType.SPEED) && e.getAmplifier() == 1 && e.getDuration() == 1800), "Обычный эффект сигареты применён");
        check(user.effects.stream().anyMatch(e -> e.getType().equals(PotionEffectType.SLOWNESS)), "Эффект перекуривания применён");
        check(user.location.getWorld().getNearbyEntities(user.location, 3, 3, 3).stream().filter(e -> e instanceof Item)
                .map(e -> ((Item) e).getItemStack()).anyMatch(i -> i.getType() == Material.COAL && i.getAmount() == 2
                        && i.getItemMeta().getDisplayName().equals("§8Мой пепел")), "Выпал предмет со своим названием");
        check(user.player.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "tobacco_smoke_count"), PersistentDataType.INTEGER, -1) == 0, "Счётчик сброшен после перекуривания");
    }
    private void craft() {
        user.inventory.clear();
        var machine = user.location.getBlock(); machine.setType(Material.SMOKER, false);
        var state = (TileState) machine.getState();
        state.getPersistentDataContainer().set(new NamespacedKey(plugin, "tobacco_machine"), PersistentDataType.BYTE, (byte) 1); state.update();
        var base = plugin.catalog().recipe(recipeId);
        var alt = new CigaretteRecipe("alternate_" + suffix, varietyId, "§eДругой рецепт", List.of("§7Другое описание"), 2, 3, List.of(), base.overdose());
        try { plugin.catalog().put(alt); } catch (Exception e) { throw new RuntimeException(e); }
        plugin.crafting().onUse(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_BLOCK, null, machine, BlockFace.UP, EquipmentSlot.HAND));
        top().setItem(11, plugin.items().create(TobaccoItems.LEAF, 2, varietyId)); top().setItem(15, new ItemStack(Material.PAPER, 3));
        plugin.crafting().onClick(new InventoryClickEvent(user.view, InventoryType.SlotType.CONTAINER, 13, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        check(plugin.items().recipeId(top().getItem(13)).equals(alt.id()), "В станке можно переключить сигарету того же сорта");
        plugin.crafting().onClick(new InventoryClickEvent(user.view, InventoryType.SlotType.CONTAINER, 22, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        check(Arrays.stream(user.inventory.getContents()).filter(Objects::nonNull).anyMatch(i -> plugin.items().is(i, TobaccoItems.CIGARETTE)
                && plugin.items().recipeId(i).equals(alt.id())), "Станок выдал выбранный вид сигареты");
        check(top().getItem(11) == null && top().getItem(15) == null, "Ресурсы выбранного рецепта списаны");
        user.player.closeInventory(); machine.setType(Material.AIR, false);
    }
    private void craftClick(int slot) {
        plugin.crafting().onClick(new InventoryClickEvent(user.view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }
    private void extraCraft() {
        user.inventory.clear();
        var machine = user.location.getBlock(); machine.setType(Material.SMOKER, false);
        var state = (TileState) machine.getState();
        state.getPersistentDataContainer().set(new NamespacedKey(plugin, "tobacco_machine"), PersistentDataType.BYTE, (byte) 1); state.update();
        plugin.crafting().onUse(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_BLOCK, null, machine, BlockFace.UP, EquipmentSlot.HAND));
        top().setItem(11, plugin.items().create(TobaccoItems.LEAF, 4, varietyId)); top().setItem(15, new ItemStack(Material.PAPER, 6));
        // Первый выбранный рецепт этого сорта содержит 4 лазурита + 3 ягоды.
        top().setItem(28, new ItemStack(Material.LAPIS_LAZULI, 2));
        top().setItem(30, new ItemStack(Material.LAPIS_LAZULI, 2));
        craftClick(22);
        check(top().getItem(11).getAmount() == 4 && top().getItem(28).getAmount() == 2 && user.inventory.isEmpty(), "Неполный рецепт не расходует компоненты и не выдаёт сигарету");
        top().setItem(29, new ItemStack(Material.GLOW_BERRIES, 3));
        top().setItem(33, new ItemStack(Material.DIAMOND));
        craftClick(22);
        check(top().getItem(28) == null && top().getItem(29) == null && top().getItem(30) == null && top().getItem(33).getType() == Material.DIAMOND, "Несколько добавок списываются вместе, посторонний предмет остаётся");
        check(Arrays.stream(user.inventory.getContents()).filter(Objects::nonNull).anyMatch(i -> plugin.items().is(i, TobaccoItems.CIGARETTE) && plugin.items().recipeId(i).equals(recipeId)), "Создана сигарета с дополнительными ингредиентами");
        var drag = new InventoryDragEvent(user.view, new ItemStack(Material.DIAMOND), new ItemStack(Material.DIAMOND, 2), false, Map.of(37, new ItemStack(Material.DIAMOND)));
        plugin.crafting().onDrag(drag); check(drag.isCancelled(), "Нижний ряд образцов защищён от перетаскивания");
        user.player.closeInventory();
        check(user.inventory.contains(Material.DIAMOND), "Неиспользованная добавка возвращается при закрытии");

        var sample = new ItemStack(Material.MILK_BUCKET);
        var meta = sample.getItemMeta(); meta.setDisplayName("§fОсобое молоко"); meta.setLore(List.of("§7Образец"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "test_ingredient"), PersistentDataType.STRING, "milk"); sample.setItemMeta(meta);
        var ingredient = new RecipeIngredient(sample); sample.setAmount(0);
        check(ingredient.amount() == 1 && !ingredient.matches(new ItemStack(Material.MILK_BUCKET)), "Образец независим от оригинала и проверяет метаданные");
        var base = plugin.catalog().recipe(recipeId);
        var milk = new CigaretteRecipe("milk_" + suffix, varietyId, "§fМолочная", List.of(), 2, 3, List.of(), base.overdose(), List.of(ingredient));
        try { plugin.catalog().put(milk); plugin.reloadData(); } catch (Exception e) { throw new RuntimeException(e); }
        check(plugin.catalog().recipe(milk.id()).ingredients().getFirst().matches(ingredient.sample()), "PDC и описание ингредиента переживают сохранение");
        user.inventory.clear();
        plugin.crafting().onUse(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_BLOCK, null, machine, BlockFace.UP, EquipmentSlot.HAND));
        top().setItem(11, plugin.items().create(TobaccoItems.LEAF, 2, varietyId)); top().setItem(15, new ItemStack(Material.PAPER, 3));
        craftClick(13); craftClick(13); // После основного и альтернативного — молочный рецепт.
        check(plugin.items().recipeId(top().getItem(13)).equals(milk.id()), "Выбран молочный рецепт");
        top().setItem(28, new ItemStack(Material.MILK_BUCKET)); craftClick(22);
        check(top().getItem(11).getAmount() == 2, "Обычное молоко не заменяет именной ингредиент");
        top().setItem(28, ingredient.sample()); craftClick(22);
        check(top().getItem(28) == null && user.inventory.contains(Material.BUCKET), "При крафте молоко расходуется, пустое ведро возвращается");
        user.player.closeInventory(); machine.setType(Material.AIR, false);
    }

    private void lifecycle() {
        var world = user.location.getWorld();
        var bottom = world.getBlockAt(56, 100, 48);
        bottom.getRelative(BlockFace.DOWN).setType(Material.FARMLAND, false);
        var planted = plugin.plants().plant(bottom, varietyId);
        plugin.plants().growNearby(bottom.getLocation(), 0);
        long forcedAt = planted.forcedMaturedAt();
        try { plugin.reloadData(); } catch (Exception e) { throw new RuntimeException(e); }
        check(plugin.plants().get(bottom).forcedMaturedAt() == forcedAt, "Время принудительной зрелости переживает reload");
        var expired = new com.example.tobaccocraft.plants.TobaccoPlant(planted.position(), planted.worldName(), System.currentTimeMillis() - 10000, false, varietyId);
        plugin.plants().apply(List.of(expired));
        check(plugin.plants().get(bottom) == null && bottom.getType() == Material.AIR && bottom.getRelative(BlockFace.UP).getType() == Material.AIR,
                "Просроченный естественно выросший куст удаляется при загрузке");
        check(bottom.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND, "Увядание сохраняет грядку");
        check(world.getNearbyEntities(bottom.getLocation(), 2, 2, 2).stream().noneMatch(e -> e instanceof Item), "Увядание не выдаёт урожай");
        // Проверка секундного таймера: готовый куст завянет без взаимодействия с игроком.
        var v = plugin.catalog().variety(varietyId);
        try { plugin.catalog().put(new TobaccoVariety("short_" + suffix, "Быстрый", v.seedsName(), v.seedsLore(), v.leafName(), v.leafLore(), 1, 1, 1, 1, 50, 1, 1)); }
        catch (Exception e) { throw new RuntimeException(e); }
        plugin.plants().plant(bottom, "short_" + suffix);
        for (int i = 0; i < 10; i++) steps.add(() -> {});
        steps.add(() -> check(plugin.plants().get(bottom) == null && bottom.getType() == Material.AIR, "Таймер удалил увядший куст без кликов"));
    }

    private void legacy() {
        ItemStack old = plugin.items().create(TobaccoItems.CIGARETTE);
        var meta = old.getItemMeta(); meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "recipe_id"));
        meta.setLore(List.of("§7ПКМ — закурить", "§7Действует 5 минут")); old.setItemMeta(meta);
        var refreshed = plugin.items().refreshItem(old);
        check(plugin.items().is(refreshed, TobaccoItems.CIGARETTE) && !refreshed.getItemMeta().hasLore(), "У старой сигареты удалён стандартный лор");
    }
    private void check(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
        probe.getLogger().info("Проверено: " + text);
    }
}
