package com.example.tobaccocraft.listeners;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.catalog.*;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.utils.ItemBuilder;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Административные окна содержат только образцы. Любое перемещение предметов отменяется. */
@SuppressWarnings("deprecation")
public final class CatalogEditor implements Listener {
    @FunctionalInterface private interface Input { void accept(String value) throws Exception; }
    @FunctionalInterface private interface SampleAction { void run(ItemStack sample) throws Exception; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private record Pending(long expiresAt, Input input, Runnable back, long generation) {}
    private static final class Menu implements InventoryHolder {
        private SampleAction sampleAction;
        private final UUID owner;
        private final Inventory inventory;
        private final Map<Integer, Action> actions = new HashMap<>();
        private Menu(Player player, String title) {
            owner = player.getUniqueId(); inventory = Bukkit.createInventory(this, 54, title);
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
        private void button(int slot, Material material, String name, Action action, String... lore) {
            inventory.setItem(slot, new ItemBuilder(material).name(name).lore(lore).build());
            actions.put(slot, action);
        }
        private void item(int slot, ItemStack item, Action action) { inventory.setItem(slot, item); actions.put(slot, action); }
    }
    private final TobaccoCraft plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final BukkitTask timeoutTask;
    private long generation;

    public CatalogEditor(TobaccoCraft plugin) {
        this.plugin = plugin;
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            pending.forEach((id, value) -> {
                if (value.expiresAt() <= now && pending.remove(id, value)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendMessage("§eВремя ввода истекло. Откройте /tobacco editor снова.");
                }
            });
        }, 20, 20);
    }

    public void open(Player p) {
        if (!allowed(p)) return;
        var menu = new Menu(p, "§6Редактор табака");
        menu.button(20, Material.WHEAT_SEEDS, "§aСорта табака", () -> varieties(p, 0, null), "§7Название куста, семян и листьев", "§7Описание и награда за сбор");
        menu.button(24, Material.PAPER, "§eСигареты", () -> recipes(p, 0), "§7Рецепт, название и описание", "§7Эффекты и последствия перекуривания");
        show(p, menu);
    }

    private void varieties(Player p, int requestedPage, String recipeToBind) {
        var values = plugin.catalog().varieties();
        int page = page(requestedPage, values.size());
        var menu = new Menu(p, recipeToBind == null ? "§6Сорта табака" : "§6Выберите сорт для сигареты");
        for (int i = page * 45; i < Math.min(values.size(), (page + 1) * 45); i++) {
            var v = values.get(i);
            menu.button(i % 45, Material.WHEAT_SEEDS, v.name(), () -> {
                if (recipeToBind == null) variety(p, v.id());
                else { updateRecipe(recipeToBind, d -> d.variety = v.id()); recipe(p, recipeToBind); }
            }, "§7Нажмите для выбора");
        }
        navigation(menu, page, values.size(), () -> varieties(p, page - 1, recipeToBind), () -> varieties(p, page + 1, recipeToBind),
                () -> { if (recipeToBind == null) open(p); else recipe(p, recipeToBind); });
        if (recipeToBind == null) menu.button(49, Material.EMERALD, "§aСоздать сорт", () -> prompt(p, "Название нового сорта", value -> {
            String name = name(value); var base = plugin.catalog().variety(CatalogManager.DEFAULT);
            String id = CatalogManager.newId();
            plugin.catalog().put(new TobaccoVariety(id, name, "§aСемена: " + name, List.of(), "§aЛистья: " + name,
                    List.of(), base.seedsMin(), base.seedsMax(), base.leafMin(), base.leafMax(), base.leafChance(), base.growthMillis(), base.wiltMillis(), base.seedsChance()));
        }, () -> varieties(p, page, null)), "§7Название вводится в чат");
        show(p, menu);
    }

    private void variety(Player p, String id) {
        var v = plugin.catalog().variety(id);
        var menu = new Menu(p, "§6Настройка сорта");
        menu.button(10, Material.NAME_TAG, "§eНазвание над кустом", () -> prompt(p, "Название сорта", s -> updateVariety(id, d -> d.name = name(s)), () -> variety(p, id)), v.name());
        menu.button(12, Material.WHEAT_SEEDS, "§eНазвание семян", () -> prompt(p, "Название семян", s -> updateVariety(id, d -> d.seedsName = name(s)), () -> variety(p, id)), v.seedsName());
        menu.button(13, Material.WRITABLE_BOOK, "§eОписание семян", () -> prompt(p, "Описание семян: строки через |; минус — очистить", s -> updateVariety(id, d -> d.seedsLore = lore(s)), () -> variety(p, id)), v.seedsLore().toArray(String[]::new));
        menu.button(14, Material.GREEN_DYE, "§eНазвание листьев", () -> prompt(p, "Название листьев", s -> updateVariety(id, d -> d.leafName = name(s)), () -> variety(p, id)), v.leafName());
        menu.button(15, Material.WRITABLE_BOOK, "§eОписание листьев", () -> prompt(p, "Описание листьев: строки через |; минус — очистить", s -> updateVariety(id, d -> d.leafLore = lore(s)), () -> variety(p, id)), v.leafLore().toArray(String[]::new));
        menu.button(28, Material.WHEAT_SEEDS, "§eКоличество семян: " + v.seedsMin() + "–" + v.seedsMax(), () -> prompt(p, "Минимум и максимум семян, например: 1 3", s -> {
            int[] range = range(s); updateVariety(id, d -> { d.seedsMin = range[0]; d.seedsMax = range[1]; });
        }, () -> variety(p, id)), "§7Количество при успешном выпадении семян");
        menu.button(30, Material.GREEN_DYE, "§eКоличество листьев: " + v.leafMin() + "–" + v.leafMax(), () -> prompt(p, "Минимум и максимум листьев, например: 2 4", s -> {
            int[] range = range(s); updateVariety(id, d -> { d.leafMin = range[0]; d.leafMax = range[1]; });
        }, () -> variety(p, id)));
        menu.button(32, Material.SUNFLOWER, "§eШанс листьев: " + v.leafChance() + "%", () -> prompt(p, "Шанс выпадения листьев от 0 до 100", s -> updateVariety(id, d -> d.chance = number(s)), () -> variety(p, id)));
        menu.button(34, Material.WHEAT_SEEDS, "§eШанс семян: " + v.seedsChance() + "%", () -> prompt(p, "Шанс выпадения семян от 0 до 100; можно дробное число", s -> updateVariety(id, d -> d.seedsChance = number(s)), () -> variety(p, id)), "§7Отдельно от шанса листьев", "§70 — не выпадают, 100 — всегда");
        menu.button(37, Material.CLOCK, "§eВремя роста", () -> prompt(p, "Минуты от посадки до созревания; можно дробное число", s -> updateVariety(id, d -> d.growth = duration(s, false)), () -> variety(p, id)), "§7Сейчас: " + minutes(v.growthMillis()) + " мин.");
        menu.button(39, Material.DEAD_BUSH, "§eУвядание после созревания", () -> prompt(p, "Через сколько минут зрелый куст завянет; 0 — никогда", s -> updateVariety(id, d -> d.wilt = duration(s, true)), () -> variety(p, id)), v.wiltMillis() == 0 ? "§7Отключено" : "§7Через " + minutes(v.wiltMillis()) + " мин.", "§7Завядший куст исчезает без урожая");
        menu.button(46, Material.CHEST, "§aПолучить 16 семян", () -> TobaccoItems.giveOrDrop(p, plugin.items().create(TobaccoItems.SEEDS, 16, id)));
        menu.button(47, Material.CHEST, "§aПолучить 16 листьев", () -> TobaccoItems.giveOrDrop(p, plugin.items().create(TobaccoItems.LEAF, 16, id)));
        menu.button(49, Material.ARROW, "§eНазад", () -> varieties(p, 0, null));
        show(p, menu);
    }

    private void recipes(Player p, int requestedPage) {
        var values = plugin.catalog().recipes(); int page = page(requestedPage, values.size());
        var menu = new Menu(p, "§6Виды сигарет");
        for (int i = page * 45; i < Math.min(values.size(), (page + 1) * 45); i++) {
            var r = values.get(i);
            menu.item(i % 45, plugin.items().create(TobaccoItems.CIGARETTE, 1, r.id()), () -> recipe(p, r.id()));
        }
        navigation(menu, page, values.size(), () -> recipes(p, page - 1), () -> recipes(p, page + 1), () -> open(p));
        menu.button(49, Material.EMERALD, "§aСоздать сигарету", () -> prompt(p, "Название новой сигареты", value -> {
            var base = plugin.catalog().recipe(CatalogManager.DEFAULT);
            plugin.catalog().put(new CigaretteRecipe(CatalogManager.newId(), CatalogManager.DEFAULT, name(value), List.of(),
                    2, 3, List.of(), base.overdose()));
        }, () -> recipes(p, page)), "§7Новое описание и эффекты сначала пусты");
        show(p, menu);
    }

    private void recipe(Player p, String id) {
        var r = plugin.catalog().recipe(id); var menu = new Menu(p, "§6Настройка сигареты");
        menu.item(4, plugin.items().create(TobaccoItems.CIGARETTE, 1, id), () -> {});
        menu.button(10, Material.NAME_TAG, "§eНазвание", () -> prompt(p, "Название сигареты", s -> updateRecipe(id, d -> d.name = name(s)), () -> recipe(p, id)), r.name());
        menu.button(12, Material.WRITABLE_BOOK, "§eСвое описание", () -> prompt(p, "Описание: строки через |; минус — очистить", s -> updateRecipe(id, d -> d.lore = lore(s)), () -> recipe(p, id)), r.lore().toArray(String[]::new));
        menu.button(14, Material.WHEAT_SEEDS, "§eСорт табака", () -> varieties(p, 0, id), plugin.catalog().variety(r.varietyId()).name());
        menu.button(16, Material.CRAFTING_TABLE, "§eИнгредиенты", () -> prompt(p, "Количество листьев и бумаги, например: 2 3", s -> {
            int[] amounts = pair(s); updateRecipe(id, d -> { d.leaves = amounts[0]; d.paper = amounts[1]; });
        }, () -> recipe(p, id)), "§7Листья: " + r.leavesRequired(), "§7Бумага: " + r.paperRequired());
        menu.button(31, Material.HOPPER, "§eДополнительные ингредиенты", () -> ingredients(p, id), "§7Добавок: " + r.ingredients().size() + "/6", "§7Можно требовать несколько предметов сразу");
        menu.button(29, Material.POTION, "§aЭффекты при курении", () -> effects(p, id, false, 0), "§7Выбрано: " + r.effects().size());
        menu.button(33, Material.REDSTONE, "§cПерекуривание", () -> overdose(p, id), "§7Порог: " + r.overdose().threshold(), "§7Окно: " + r.overdose().windowSeconds() + " сек.");
        menu.button(46, Material.CHEST, "§aПолучить сигарету", () -> TobaccoItems.giveOrDrop(p, plugin.items().create(TobaccoItems.CIGARETTE, 1, id)));
        menu.button(49, Material.ARROW, "§eНазад", () -> recipes(p, 0));
        show(p, menu);
    }

    private void ingredients(Player p, String id) {
        var values = plugin.catalog().recipe(id).ingredients();
        var menu = new Menu(p, "§6Дополнительные ингредиенты");
        for (int i = 0; i < values.size(); i++) {
            int index = i;
            menu.item(10 + i, values.get(i).sample(), () -> ingredient(p, id, index));
        }
        menu.sampleAction = sample -> {
            updateRecipe(id, d -> { var next = new ArrayList<>(d.ingredients); next.add(new RecipeIngredient(sample)); d.ingredients = next; });
            ingredients(p, id);
        };
        menu.button(22, Material.CHEST, "§aДобавить предмет из основной руки", () -> menu.sampleAction.run(p.getInventory().getItemInMainHand().clone()),
                "§7Или нажмите на предмет в своём инвентаре", "§7Образец копируется и не расходуется", "§7Учитываются название, описание и другие свойства");
        menu.button(31, Material.BOOK, "§eНастройка добавок", () -> {}, "§7Нажмите на образец сверху для настройки", "§7Эффекты задаются в меню сигареты", "§7Все добавки рецепта требуются одновременно");
        menu.button(49, Material.ARROW, "§eНазад", () -> recipe(p, id));
        show(p, menu);
    }

    private void ingredient(Player p, String id, int index) {
        var values = plugin.catalog().recipe(id).ingredients();
        if (index >= values.size()) { ingredients(p, id); return; }
        var selected = values.get(index);
        var menu = new Menu(p, "§6Настройка ингредиента");
        menu.item(4, selected.sample(), () -> {});
        menu.button(22, Material.HOPPER, "§eКоличество: " + selected.amount(), () -> prompt(p, "Количество от 1 до " + selected.sample().getMaxStackSize(), s -> updateRecipe(id, d -> {
            if (index >= d.ingredients.size() || !d.ingredients.get(index).sample().equals(selected.sample()))
                throw new IllegalArgumentException("Рецепт изменён другим администратором. Откройте его снова.");
            var next = new ArrayList<>(d.ingredients); var sample = selected.sample(); sample.setAmount(integer(s));
            next.set(index, new RecipeIngredient(sample)); d.ingredients = next;
        }), () -> ingredients(p, id)));
        menu.button(24, Material.BARRIER, "§cУдалить ингредиент", () -> {
            updateRecipe(id, d -> {
                if (index >= d.ingredients.size() || !d.ingredients.get(index).sample().equals(selected.sample()))
                    throw new IllegalArgumentException("Рецепт уже изменён. Откройте его снова.");
                var next = new ArrayList<>(d.ingredients); next.remove(index); d.ingredients = next;
            }); ingredients(p, id);
        });
        menu.button(49, Material.ARROW, "§eНазад", () -> ingredients(p, id));
        show(p, menu);
    }

    private void overdose(Player p, String id) {
        var o = plugin.catalog().recipe(id).overdose(); var menu = new Menu(p, "§6Настройка перекуривания");
        menu.button(10, Material.REDSTONE, "§eПорог: " + o.threshold() + " сигарет", () -> prompt(p, "Количество сигарет до перекуривания: 1–1000", s -> updateRecipe(id, d -> d.overdose.threshold = integer(s)), () -> overdose(p, id)));
        menu.button(12, Material.CLOCK, "§eОкно: " + o.windowSeconds() + " сек.", () -> prompt(p, "За сколько секунд считать сигареты: 1–86400", s -> updateRecipe(id, d -> d.overdose.window = integer(s)), () -> overdose(p, id)));
        menu.button(14, Material.IRON_SWORD, "§eУрон: " + o.damage(), () -> prompt(p, "Урон: 0–2048; 2 единицы = 1 сердце", s -> updateRecipe(id, d -> d.overdose.damage = number(s)), () -> overdose(p, id)));
        menu.button(16, Material.POTION, "§eЭффекты перекуривания", () -> effects(p, id, true, 0), "§7Выбрано: " + o.effects().size());
        menu.button(29, Material.WRITABLE_BOOK, "§eСообщение при перекуривании", () -> prompt(p, "Сообщение; минус — отключить", s -> updateRecipe(id, d -> d.overdose.message = empty(s) ? "" : colors(s)), () -> overdose(p, id)), o.message());
        menu.button(33, Material.CHEST, "§eВыпадающий предмет", () -> drop(p, id), o.dropMaterial().isAir() ? "§7Выпадение отключено" : o.dropName());
        menu.button(49, Material.ARROW, "§eНазад", () -> recipe(p, id));
        show(p, menu);
    }

    private void drop(Player p, String id) {
        var o = plugin.catalog().recipe(id).overdose(); var menu = new Menu(p, "§6Предмет при перекуривании");
        if (!o.dropMaterial().isAir()) menu.item(4, o.createDrop(), () -> {});
        menu.button(10, Material.CHEST, "§eВзять образец из основной руки", () -> {
            ItemStack sample = p.getInventory().getItemInMainHand().clone();
            if (sample.getType().isAir()) throw new IllegalArgumentException("Возьмите нужный предмет в основную руку перед открытием редактора.");
            if (plugin.items().isCustom(sample)) throw new IllegalArgumentException("Выберите обычный предмет Minecraft.");
            var meta = sample.getItemMeta();
            updateRecipe(id, d -> {
                d.overdose.material = sample.getType(); d.overdose.amount = sample.getAmount();
                d.overdose.dropName = meta.hasDisplayName() ? meta.getDisplayName() : "§7Предмет";
                d.overdose.dropLore = meta.hasLore() ? meta.getLore() : List.of();
                d.overdose.model = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
            }); drop(p, id);
        }, "§7Копируются материал, название, описание и модель", "§7Предмет в руке не расходуется");
        menu.button(12, Material.FLINT, "§eВыбрать материал", () -> materials(p, id, 0));
        menu.button(14, Material.NAME_TAG, "§eНазвание предмета", () -> prompt(p, "Название выпадающего предмета", s -> updateRecipe(id, d -> d.overdose.dropName = name(s)), () -> drop(p, id)), o.dropName());
        menu.button(16, Material.WRITABLE_BOOK, "§eОписание предмета", () -> prompt(p, "Описание: строки через |; минус — очистить", s -> updateRecipe(id, d -> d.overdose.dropLore = lore(s)), () -> drop(p, id)), o.dropLore().toArray(String[]::new));
        menu.button(29, Material.HOPPER, "§eКоличество: " + o.dropAmount(), () -> prompt(p, "Количество предметов, не больше стака", s -> updateRecipe(id, d -> d.overdose.amount = integer(s)), () -> drop(p, id)));
        menu.button(33, Material.BARRIER, "§cОтключить выпадение", () -> { updateRecipe(id, d -> { d.overdose.material = Material.AIR; d.overdose.amount = 1; }); drop(p, id); });
        menu.button(49, Material.ARROW, "§eНазад", () -> overdose(p, id));
        show(p, menu);
    }

    private void materials(Player p, String id, int requestedPage) {
        var values = Arrays.stream(Material.values()).filter(m -> !m.isLegacy() && m.isItem() && !m.isAir()).toList();
        int page = page(requestedPage, values.size()); var menu = new Menu(p, "§6Выберите выпадающий предмет");
        for (int i = page * 45; i < Math.min(values.size(), (page + 1) * 45); i++) {
            Material material = values.get(i);
            menu.item(i % 45, new ItemStack(material), () -> { updateRecipe(id, d -> { d.overdose.material = material; d.overdose.amount = 1; }); drop(p, id); });
        }
        navigation(menu, page, values.size(), () -> materials(p, id, page - 1), () -> materials(p, id, page + 1), () -> drop(p, id));
        show(p, menu);
    }

    private void effects(Player p, String id, boolean overdose, int requestedPage) {
        var types = Registry.EFFECT.stream().sorted(Comparator.comparing(t -> EffectSpec.displayName(t.getKey().getKey()))).toList();
        int page = page(requestedPage, types.size()); var menu = new Menu(p, overdose ? "§6Эффекты перекуривания" : "§6Эффекты сигареты");
        var selected = effects(id, overdose);
        for (int i = page * 45; i < Math.min(types.size(), (page + 1) * 45); i++) {
            String key = types.get(i).getKey().getKey();
            var current = selected.stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
            menu.button(i % 45, current == null ? Material.GLASS_BOTTLE : Material.POTION,
                    (current == null ? "§7" : "§a") + EffectSpec.displayName(key), () -> effect(p, id, overdose, key),
                    current == null ? "§7Не включён" : "§7Уровень " + current.level() + ", " + current.seconds() + " сек.", "§eНажмите для настройки");
        }
        navigation(menu, page, types.size(), () -> effects(p, id, overdose, page - 1), () -> effects(p, id, overdose, page + 1),
                () -> { if (overdose) overdose(p, id); else recipe(p, id); });
        show(p, menu);
    }

    private List<EffectSpec> effects(String id, boolean overdose) {
        var r = plugin.catalog().recipe(id); return overdose ? r.overdose().effects() : r.effects();
    }
    private void effect(Player p, String id, boolean overdose, String key) {
        var current = effects(id, overdose).stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
        var menu = new Menu(p, "§6Настройка эффекта");
        menu.button(4, Material.POTION, "§e" + EffectSpec.displayName(key), () -> {});
        menu.button(20, current == null ? Material.LIME_DYE : Material.RED_DYE, current == null ? "§aВключить" : "§cУдалить эффект", () -> {
            setEffect(id, overdose, key, current == null ? new EffectSpec(key, 1, 60) : null); effect(p, id, overdose, key);
        });
        if (current != null) {
            menu.button(22, Material.REDSTONE, "§eУровень: " + current.level(), () -> prompt(p, "Уровень эффекта: 1–10", s -> {
                var latest = effects(id, overdose).stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow();
                setEffect(id, overdose, key, new EffectSpec(key, integer(s), latest.seconds()));
            }, () -> effect(p, id, overdose, key)));
            menu.button(24, Material.CLOCK, "§eДлительность: " + current.seconds() + " сек.", () -> prompt(p, "Длительность эффекта: 1–86400 секунд", s -> {
                var latest = effects(id, overdose).stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow();
                setEffect(id, overdose, key, new EffectSpec(key, latest.level(), integer(s)));
            }, () -> effect(p, id, overdose, key)));
        }
        menu.button(49, Material.ARROW, "§eНазад", () -> effects(p, id, overdose, 0));
        show(p, menu);
    }
    private void setEffect(String id, boolean overdose, String key, EffectSpec next) throws IOException {
        updateRecipe(id, d -> {
            var list = new ArrayList<>(overdose ? d.overdose.effects : d.effects);
            list.removeIf(e -> e.key().equals(key)); if (next != null) list.add(next);
            if (overdose) d.overdose.effects = list; else d.effects = list;
        });
    }

    private static int page(int page, int count) { return Math.max(0, Math.min(page, Math.max(0, (count - 1) / 45))); }
    private static void navigation(Menu menu, int page, int count, Action previous, Action next, Action back) {
        if (page > 0) menu.button(45, Material.ARROW, "§eПредыдущая страница", previous);
        menu.button(48, Material.ARROW, "§eНазад", back);
        menu.button(50, Material.PAPER, "§7Страница " + (page + 1), () -> {});
        if ((page + 1) * 45 < count) menu.button(53, Material.ARROW, "§eСледующая страница", next);
    }
    private void show(Player p, Menu menu) { pending.remove(p.getUniqueId()); p.openInventory(menu.inventory); }
    private boolean allowed(Player p) {
        if (!plugin.messages().require(p, "tobaccocraft.admin")) return false;
        if (plugin.minigames().isPlaying(p)) { plugin.messages().send(p, "already-playing"); return false; }
        return p.isOnline() && !p.isDead();
    }
    private void prompt(Player p, String title, Input input, Runnable back) {
        p.closeInventory();
        pending.put(p.getUniqueId(), new Pending(System.currentTimeMillis() + 120000, input, back, generation));
        p.sendMessage("§e" + title + ". §7Цвета: &a, &c… Введите «отмена» для возврата.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !menu.owner.equals(p.getUniqueId())) return;
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) return;
        Action selected = menu.actions.get(e.getRawSlot());
        if (e.getRawSlot() >= menu.inventory.getSize() && menu.sampleAction != null && e.getCurrentItem() != null) {
            var sample = e.getCurrentItem().clone();
            selected = () -> menu.sampleAction.run(sample);
        }
        if (selected == null) return;
        final Action action = selected;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.getOpenInventory().getTopInventory() != menu.inventory || !allowed(p)) return;
            try { action.run(); } catch (Exception error) { error(p, error); }
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId(); Pending input = pending.remove(id);
        if (input == null) return;
        e.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            if (input.generation() != generation || !allowed(p)) return;
            if (input.expiresAt() < System.currentTimeMillis()) { p.sendMessage("§eВремя ввода истекло."); return; }
            if (value.equalsIgnoreCase("отмена") || value.equalsIgnoreCase("cancel")) { input.back().run(); return; }
            try { input.input().accept(value); input.back().run(); }
            catch (Exception error) {
                error(p, error);
                pending.put(id, new Pending(System.currentTimeMillis() + 120000, input.input(), input.back(), generation));
                p.sendMessage("§eВведите другое значение или «отмена».");
            }
        });
    }
    private void error(Player p, Exception error) {
        if (error instanceof IOException) {
            plugin.getLogger().log(Level.SEVERE, "§cНе удалось сохранить каталог", error);
            p.sendMessage("§cНе удалось сохранить изменения. Подробнее в консоли.");
        } else p.sendMessage("§c" + (error.getMessage() == null ? "Некорректное значение." : error.getMessage()));
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { pending.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { pending.remove(e.getEntity().getUniqueId()); }
    public void closeAll() {
        generation++;
        pending.clear();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
    }
    public void shutdown() { closeAll(); timeoutTask.cancel(); }

    public static String colors(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    public static String name(String input) { String text = colors(input); CatalogManager.checkText(text, 80); return text; }
    private static boolean empty(String text) { return text.equals("-") || text.equalsIgnoreCase("очистить"); }
    public static List<String> lore(String input) {
        return empty(input) ? List.of() : CatalogManager.checkLore(Arrays.stream(input.split("\\|", -1)).map(String::trim).map(CatalogEditor::colors).toList());
    }
    private static String minutes(long millis) { return java.math.BigDecimal.valueOf(millis).divide(java.math.BigDecimal.valueOf(60000), 4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static long duration(String text, boolean allowZero) {
        double value = number(text) * 60000;
        if (value < 0 || value > 2_000_000_000_000_000L || (value > 0 && value < 1) || (!allowZero && value < 1))
            throw new IllegalArgumentException("Введите положительное время в минутах" + (allowZero ? " или 0 для отключения." : "."));
        return Math.round(value);
    }
    private static int integer(String text) {
        try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Введите целое число."); }
    }
    private static double number(String text) {
        try { double value = Double.parseDouble(text.trim().replace(',', '.')); if (!Double.isFinite(value)) throw new NumberFormatException(); return value; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Введите обычное число."); }
    }
    private static int[] pair(String text) {
        String[] words = text.trim().split("\\s+");
        if (words.length != 2) throw new IllegalArgumentException("Введите два целых числа через пробел.");
        return new int[]{integer(words[0]), integer(words[1])};
    }
    private static int[] range(String text) {
        int[] range = pair(text);
        if (range[0] < 1 || range[1] < range[0] || range[1] > 64) throw new IllegalArgumentException("Диапазон: 1–64, минимум не больше максимума.");
        return range;
    }
    private void updateVariety(String id, Consumer<VarietyDraft> edit) throws IOException {
        var d = new VarietyDraft(plugin.catalog().variety(id)); edit.accept(d); plugin.catalog().put(d.build());
    }
    private void updateRecipe(String id, Consumer<RecipeDraft> edit) throws IOException {
        var d = new RecipeDraft(plugin.catalog().recipe(id)); edit.accept(d); plugin.catalog().put(d.build());
    }
    private static final class VarietyDraft {
        String id, name, seedsName, leafName; List<String> seedsLore, leafLore;
        int seedsMin, seedsMax, leafMin, leafMax; double chance, seedsChance; long growth, wilt;
        VarietyDraft(TobaccoVariety v) {
            id = v.id(); name = v.name(); seedsName = v.seedsName(); leafName = v.leafName(); seedsLore = v.seedsLore(); leafLore = v.leafLore();
            seedsMin = v.seedsMin(); seedsMax = v.seedsMax(); leafMin = v.leafMin(); leafMax = v.leafMax(); chance = v.leafChance(); seedsChance = v.seedsChance(); growth = v.growthMillis(); wilt = v.wiltMillis();
        }
        TobaccoVariety build() { return new TobaccoVariety(id, name, seedsName, seedsLore, leafName, leafLore, seedsMin, seedsMax, leafMin, leafMax, chance, growth, wilt, seedsChance); }
    }
    private static final class RecipeDraft {
        String id, variety, name; List<String> lore; int leaves, paper; List<EffectSpec> effects; OverdoseDraft overdose; List<RecipeIngredient> ingredients;
        RecipeDraft(CigaretteRecipe r) {
            id = r.id(); variety = r.varietyId(); name = r.name(); lore = r.lore(); leaves = r.leavesRequired(); paper = r.paperRequired();
            effects = r.effects(); overdose = new OverdoseDraft(r.overdose()); ingredients = r.ingredients();
        }
        CigaretteRecipe build() { return new CigaretteRecipe(id, variety, name, lore, leaves, paper, effects, overdose.build(), ingredients); }
    }
    private static final class OverdoseDraft {
        int threshold, window, amount, model; double damage; String message, dropName; Material material;
        List<EffectSpec> effects; List<String> dropLore;
        OverdoseDraft(OverdoseSettings o) {
            threshold = o.threshold(); window = o.windowSeconds(); damage = o.damage(); effects = o.effects(); message = o.message();
            material = o.dropMaterial(); amount = o.dropAmount(); dropName = o.dropName(); dropLore = o.dropLore(); model = o.dropModel();
        }
        OverdoseSettings build() { return new OverdoseSettings(threshold, window, damage, effects, message, material, amount, dropName, dropLore, model); }
    }
}
