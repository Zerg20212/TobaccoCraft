package com.example.tobaccocraft.catalog;

import com.example.tobaccocraft.TobaccoCraft;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class CatalogManager {
    public static final String DEFAULT = "default";
    public record Catalog(Map<String, TobaccoVariety> varieties, Map<String, CigaretteRecipe> recipes) {
        public Catalog {
            varieties = Collections.unmodifiableMap(new LinkedHashMap<>(varieties));
            recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            if (!varieties.containsKey(DEFAULT) || !recipes.containsKey(DEFAULT))
                throw new IllegalArgumentException("В каталоге должны быть сорт и сигарета default.");
            if (varieties.size() > 256 || recipes.size() > 256) throw new IllegalArgumentException("Максимум 256 сортов и 256 сигарет.");
            for (CigaretteRecipe recipe : recipes.values()) if (!varieties.containsKey(recipe.varietyId()))
                throw new IllegalArgumentException("У сигареты отсутствует сорт: " + recipe.varietyId());
        }
    }
    private final TobaccoCraft plugin;
    private final Path path;
    private Catalog catalog;

    public CatalogManager(TobaccoCraft plugin) {
        this.plugin = plugin;
        path = plugin.getDataFolder().toPath().resolve("catalog.yml");
    }

    public Catalog read() throws Exception {
        if (!Files.exists(path)) {
            Catalog defaults = defaults();
            write(defaults);
            return defaults;
        }
        var yaml = new YamlConfiguration(); yaml.load(path.toFile());
        var varieties = new LinkedHashMap<String, TobaccoVariety>();
        var recipes = new LinkedHashMap<String, CigaretteRecipe>();
        var vs = Objects.requireNonNull(yaml.getConfigurationSection("varieties"), "Нет раздела varieties");
        for (String id : vs.getKeys(false)) {
            var s = Objects.requireNonNull(vs.getConfigurationSection(id));
            varieties.put(id, new TobaccoVariety(id, s.getString("name"), s.getString("seeds-name"), s.getStringList("seeds-lore"),
                    s.getString("leaf-name"), s.getStringList("leaf-lore"), s.getInt("seeds-min"), s.getInt("seeds-max"),
                    s.getInt("leaf-min"), s.getInt("leaf-max"), s.getDouble("leaf-chance"), s.getLong("growth-millis", defaultGrowthMillis()), s.getLong("wilt-millis", 0), s.getDouble("seeds-chance", 100)));
        }
        var rs = Objects.requireNonNull(yaml.getConfigurationSection("cigarettes"), "Нет раздела cigarettes");
        for (String id : rs.getKeys(false)) {
            var s = Objects.requireNonNull(rs.getConfigurationSection(id));
            var o = Objects.requireNonNull(s.getConfigurationSection("overdose"));
            var drop = Material.matchMaterial(o.getString("drop.material", "AIR"));
            var overdose = new OverdoseSettings(o.getInt("threshold"), o.getInt("window-seconds"), o.getDouble("damage"),
                    readEffects(o, "effects"), o.getString("message", ""), drop, o.getInt("drop.amount", 1),
                    o.getString("drop.name", "§7Пепел"), o.getStringList("drop.lore"), o.getInt("drop.model", 0));
            recipes.put(id, new CigaretteRecipe(id, s.getString("variety"), s.getString("name"), s.getStringList("lore"),
                    s.getInt("leaves-required"), s.getInt("paper-required"), readEffects(s, "effects"), overdose, readIngredients(s)));
        }
        return new Catalog(varieties, recipes);
    }

    private long defaultGrowthMillis() {
        return plugin.config().minutesToMillis("growth.real-time-per-day-minutes") * plugin.config().integer("growth.days-required");
    }

    private static List<RecipeIngredient> readIngredients(ConfigurationSection section) {
        var result = new ArrayList<RecipeIngredient>();
        for (Object entry : section.getList("ingredients", List.of())) {
            if (!(entry instanceof org.bukkit.inventory.ItemStack item))
                throw new IllegalArgumentException("Некорректный образец ингредиента.");
            result.add(new RecipeIngredient(item));
        }
        return result;
    }

    private static List<EffectSpec> readEffects(ConfigurationSection section, String path) {
        List<EffectSpec> result = new ArrayList<>();
        for (var row : section.getMapList(path)) {
            var effect = new EffectSpec(String.valueOf(row.get("type")), ((Number) row.get("level")).intValue(),
                    ((Number) row.get("seconds")).intValue());
            effect.toPotionEffect(); // Проверка существования эффекта выполняется на сервере.
            result.add(effect);
        }
        return result;
    }

    public void apply(Catalog next) { catalog = next; }
    public TobaccoVariety variety(String id) { return catalog.varieties().get(id); }
    public CigaretteRecipe recipe(String id) { return catalog.recipes().get(id); }
    public List<TobaccoVariety> varieties() { return List.copyOf(catalog.varieties().values()); }
    public List<CigaretteRecipe> recipes() { return List.copyOf(catalog.recipes().values()); }
    public List<CigaretteRecipe> recipesFor(String variety) {
        return recipes().stream().filter(recipe -> recipe.varietyId().equals(variety)).toList();
    }
    public long maxWindowMillis() {
        return recipes().stream().mapToLong(recipe -> recipe.overdose().windowSeconds() * 1000L).max().orElse(300000);
    }

    public void put(TobaccoVariety variety) throws IOException {
        var map = new LinkedHashMap<>(catalog.varieties()); map.put(variety.id(), variety);
        commit(new Catalog(map, catalog.recipes()));
    }
    public void put(CigaretteRecipe recipe) throws IOException {
        var map = new LinkedHashMap<>(catalog.recipes()); map.put(recipe.id(), recipe);
        commit(new Catalog(catalog.varieties(), map));
    }
    private void commit(Catalog next) throws IOException {
        write(next); catalog = next;
        if (plugin.plants() != null) for (var plant : plugin.plants().all()) if (plant.isLoaded()) plugin.plants().refresh(plant);
        if (plugin.items() != null) plugin.getServer().getOnlinePlayers().forEach(plugin.items()::refreshPlayer);
        if (plugin.crafting() != null) plugin.crafting().refreshViews();
    }

    private Catalog defaults() {
        var config = plugin.config();
        var v = new TobaccoVariety(DEFAULT, "§aОбычный табак", "§aСемена табака", List.of("§7Посадите на грядку для выращивания"),
                "§aЛистик табака", List.of(), config.integer("minigame.reward.seeds-min"), config.integer("minigame.reward.seeds-max"),
                config.integer("minigame.reward.leaf-min"), config.integer("minigame.reward.leaf-max"), 50, defaultGrowthMillis(), 0);
        var effects = List.of("nausea", "slowness", "weakness", "hunger").stream()
                .map(key -> new EffectSpec(key, 1, config.integer("smoking.effects-duration-seconds"))).toList();
        var o = new OverdoseSettings(config.integer("smoking.overdose-threshold"), config.integer("smoking.smoke-duration-seconds"),
                config.number("smoking.overdose-damage"), List.of(), "§cВы перекурили! Вам стало плохо!",
                Material.AIR, 1, "§7Пепел", List.of(), 0);
        var r = new CigaretteRecipe(DEFAULT, DEFAULT, "§cСигарета", List.of(), 2, 3, effects, o);
        return new Catalog(Map.of(DEFAULT, v), Map.of(DEFAULT, r));
    }

    private void write(Catalog next) throws IOException {
        var yaml = new YamlConfiguration();
        for (var v : next.varieties().values()) {
            var s = yaml.createSection("varieties." + v.id());
            s.set("name", v.name()); s.set("seeds-name", v.seedsName()); s.set("seeds-lore", v.seedsLore());
            s.set("leaf-name", v.leafName()); s.set("leaf-lore", v.leafLore());
            s.set("seeds-min", v.seedsMin()); s.set("seeds-max", v.seedsMax()); s.set("seeds-chance", v.seedsChance());
            s.set("leaf-min", v.leafMin()); s.set("leaf-max", v.leafMax()); s.set("leaf-chance", v.leafChance());
            s.set("growth-millis", v.growthMillis()); s.set("wilt-millis", v.wiltMillis());
        }
        for (var r : next.recipes().values()) {
            var s = yaml.createSection("cigarettes." + r.id());
            s.set("variety", r.varietyId()); s.set("name", r.name()); s.set("lore", r.lore());
            s.set("leaves-required", r.leavesRequired()); s.set("paper-required", r.paperRequired());
            s.set("effects", effectRows(r.effects()));
            s.set("ingredients", r.ingredients().stream().map(RecipeIngredient::sample).toList());
            var o = r.overdose(); var section = s.createSection("overdose");
            section.set("threshold", o.threshold()); section.set("window-seconds", o.windowSeconds()); section.set("damage", o.damage());
            section.set("effects", effectRows(o.effects())); section.set("message", o.message());
            section.set("drop.material", o.dropMaterial().name()); section.set("drop.amount", o.dropAmount());
            section.set("drop.name", o.dropName()); section.set("drop.lore", o.dropLore()); section.set("drop.model", o.dropModel());
        }
        Files.createDirectories(path.getParent());
        var temp = path.resolveSibling("catalog.yml.tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static List<Map<String, Object>> effectRows(List<EffectSpec> effects) {
        return effects.stream().<Map<String, Object>>map(e -> Map.of("type", e.key(), "level", e.level(), "seconds", e.seconds())).toList();
    }
    public static String newId() { return "custom_" + UUID.randomUUID().toString().replace("-", ""); }
    public static void checkId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Некорректный идентификатор.");
    }
    public static void checkText(String text, int limit) {
        if (text == null || ChatColor.stripColor(text).isBlank() || text.length() > limit)
            throw new IllegalArgumentException("Текст должен содержать от 1 до " + limit + " символов.");
    }
    public static List<String> checkLore(List<String> lore) {
        if (lore == null || lore.size() > 10 || lore.stream().anyMatch(line -> line == null || line.length() > 120))
            throw new IllegalArgumentException("Описание: до 10 строк, до 120 символов в каждой.");
        return List.copyOf(lore);
    }
    public static void checkEffects(List<EffectSpec> effects) {
        if (effects.size() > 64 || effects.stream().map(EffectSpec::key).distinct().count() != effects.size())
            throw new IllegalArgumentException("Эффекты не должны повторяться.");
    }
}
