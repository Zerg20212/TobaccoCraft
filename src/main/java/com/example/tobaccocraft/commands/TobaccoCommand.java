package com.example.tobaccocraft.commands;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.items.TobaccoItems;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class TobaccoCommand implements TabExecutor {
    private final TobaccoCraft plugin;
    private static final List<String> ITEMS = List.of("seeds", "leaf", "shears", "cigarette", "machine");
    private static final Map<Material, Integer> RECIPE = new LinkedHashMap<>();
    private static final Map<Material, String> NAMES = Map.of(Material.REDSTONE, "Редстоун", Material.IRON_BLOCK, "Железный блок",
            Material.LEATHER, "Кожа", Material.OAK_PLANKS, "Дубовые доски", Material.CAULDRON, "Котёл");
    static {
        RECIPE.put(Material.REDSTONE, 32); RECIPE.put(Material.IRON_BLOCK, 2); RECIPE.put(Material.LEATHER, 8);
        RECIPE.put(Material.OAK_PLANKS, 4); RECIPE.put(Material.CAULDRON, 1);
    }

    public TobaccoCommand(TobaccoCraft plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!plugin.messages().require(sender, "tobaccocraft.use")) return true;
        if (args.length == 0) { help(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String permission = switch (sub) {
            case "craft" -> "tobaccocraft.farmer";
            case "give", "reload", "clear", "grow", "editor" -> "tobaccocraft.admin";
            default -> "tobaccocraft.use";
        };
        if (!plugin.messages().require(sender, permission)) return true;
        switch (sub) {
            case "info" -> {
                sender.sendMessage("§6TobaccoCraft §f" + plugin.getDescription().getVersion() + " §7— Paper 1.21.1 / Java 21");
                sender.sendMessage("§7Посадите семена на грядку, соберите зрелый куст ножницами и изготовьте сигарету в станке.");
                sender.sendMessage("§7Рост, увядание и награда задаются отдельно для каждого сорта в /tobacco editor.");
                sender.sendMessage("§7Кустов: §f" + plugin.plants().all().size());
            }
            case "reload" -> {
                try { plugin.reloadData(); plugin.messages().send(sender, "reloaded"); }
                catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "§cНе удалось перезагрузить TobaccoCraft", e);
                    plugin.messages().send(sender, "reload-failed");
                }
            }
            case "craft", "give", "clear", "grow", "editor" -> {
                if (!(sender instanceof Player player)) { plugin.messages().send(sender, "players-only"); return true; }
                if (plugin.minigames().isPlaying(player)) { plugin.messages().send(player, "already-playing"); return true; }
                if (sub.equals("craft")) craftMachine(player);
                else if (sub.equals("clear")) plugin.messages().send(player, "cleared", "count", plugin.plants().clear(player.getLocation(), 50));
                else if (sub.equals("grow")) plugin.messages().send(player, "grown", "count", plugin.plants().growNearby(player.getLocation(), 5));
                else if (sub.equals("editor")) plugin.editor().open(player);
                else give(player, args);
            }
            default -> plugin.messages().send(sender, "unknown-command");
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6TobaccoCraft — команды:");
        sender.sendMessage("§e/tobacco info §7— информация");
        if (sender.hasPermission("tobaccocraft.farmer")) sender.sendMessage("§e/tobacco craft §7— создать табачный станок");
        if (sender.hasPermission("tobaccocraft.admin")) {
            sender.sendMessage("§e/tobacco give <seeds|leaf|shears|cigarette|machine> [кол-во] §7— выдать себе предмет");
            sender.sendMessage("§e/tobacco reload §7— перезагрузить config.yml и data.yml");
            sender.sendMessage("§e/tobacco clear §7— удалить кусты в радиусе 50 блоков");
            sender.sendMessage("§e/tobacco grow §7— вырастить кусты в радиусе 5 блоков");
            sender.sendMessage("§e/tobacco editor §7— редактор сортов, сигарет и перекуривания");
        }
    }

    private void give(Player player, String[] args) {
        if (args.length < 2 || args.length > 3 || !ITEMS.contains(args[1].toLowerCase(Locale.ROOT))) {
            plugin.messages().send(player, "unknown-item"); return;
        }
        int amount;
        try {
            amount = args.length == 3 ? Integer.parseInt(args[2]) : 1;
            if (amount < 1 || amount > 2304) throw new NumberFormatException();
        } catch (NumberFormatException e) { plugin.messages().send(player, "invalid-amount"); return; }
        String id = args[1].toLowerCase(Locale.ROOT);
        ItemStack template = plugin.items().create(id);
        for (int left = amount; left > 0; left -= template.getMaxStackSize()) {
            TobaccoItems.giveOrDrop(player, plugin.items().create(id, Math.min(left, template.getMaxStackSize())));
        }
        plugin.messages().send(player, "given", "item", template.getItemMeta().getDisplayName(), "amount", amount);
    }

    /** Сначала проверяется весь рецепт; списание выполняется только при наличии всех материалов. */
    private void craftMachine(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<String> missing = new ArrayList<>();
        for (var ingredient : RECIPE.entrySet()) {
            int found = 0;
            for (ItemStack item : contents) {
                if (item != null && item.getType() == ingredient.getKey() && !plugin.items().isCustom(item)) found += item.getAmount();
            }
            if (found < ingredient.getValue()) missing.add(NAMES.get(ingredient.getKey()) + " ×" + (ingredient.getValue() - found));
        }
        if (!missing.isEmpty()) { plugin.messages().send(player, "missing-resources", "resources", String.join(", ", missing)); return; }
        for (var ingredient : RECIPE.entrySet()) {
            int remaining = ingredient.getValue();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                ItemStack item = contents[slot];
                if (item == null || item.getType() != ingredient.getKey() || plugin.items().isCustom(item)) continue;
                int taken = Math.min(remaining, item.getAmount());
                ItemStack rest = item.clone(); rest.setAmount(item.getAmount() - taken);
                contents[slot] = rest.getAmount() == 0 ? null : rest;
                remaining -= taken;
            }
        }
        player.getInventory().setStorageContents(contents);
        TobaccoItems.giveOrDrop(player, plugin.items().create(TobaccoItems.MACHINE));
        plugin.messages().send(player, "machine-crafted");
    }

    @Override public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                        @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("tobaccocraft.use")) options.add("info");
            if (sender.hasPermission("tobaccocraft.farmer")) options.add("craft");
            if (sender.hasPermission("tobaccocraft.admin")) options.addAll(List.of("give", "reload", "clear", "grow", "editor"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("tobaccocraft.admin")) options.addAll(ITEMS);
        else if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("tobaccocraft.admin")) options.addAll(List.of("1", "16", "64"));
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
