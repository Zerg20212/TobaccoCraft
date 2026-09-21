package com.example.tobaccocraft.listeners;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.catalog.CigaretteRecipe;
import com.example.tobaccocraft.catalog.IngredientMatcher;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class CraftListener implements Listener {
    private static final int LEAF_SLOT = 11, PAPER_SLOT = 15, RESULT_SLOT = 13, CRAFT_SLOT = 22;
    private static final int[] EXTRA_SLOTS = {28, 29, 30, 31, 32, 33};
    private static final int[] INPUT_SLOTS = {LEAF_SLOT, PAPER_SLOT, 28, 29, 30, 31, 32, 33};
    private static boolean inputSlot(int slot) { return java.util.Arrays.stream(INPUT_SLOTS).anyMatch(value -> value == slot); }
    private final TobaccoCraft plugin;
    private final NamespacedKey machineKey;
    private final Map<UUID, MachineInventory> sessions = new HashMap<>();

    /** Holder проверяется по типу, а не по названию окна. */
    private static final class MachineInventory implements InventoryHolder {
        private final Inventory inventory;
        private final UUID owner;
        private final Location machine;
        private boolean returned;
        private String recipeId = "default";
        private MachineInventory(Player player, Location machine) {
            owner = player.getUniqueId(); this.machine = machine.clone();
            inventory = Bukkit.createInventory(this, 45, "§6Табачный станок");
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    public CraftListener(TobaccoCraft plugin) {
        this.plugin = plugin;
        machineKey = new NamespacedKey(plugin, "tobacco_machine");
    }

    public boolean isMachine(Block block) {
        return block.getType() == Material.SMOKER && block.getState() instanceof TileState state
                && state.getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.items().is(event.getItemInHand(), TobaccoItems.MACHINE)) return;
        if (!plugin.messages().require(event.getPlayer(), "tobaccocraft.farmer")
                || plugin.minigames().isPlaying(event.getPlayer())) { event.setCancelled(true); return; }
        if (event.getBlockPlaced().getState() instanceof TileState state) {
            state.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) 1);
            state.update(true, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !isMachine(event.getClickedBlock())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || plugin.minigames().isPlaying(event.getPlayer())
                || !plugin.messages().require(event.getPlayer(), "tobaccocraft.farmer")) return;
        Player player = event.getPlayer();
        player.closeInventory();
        MachineInventory holder = new MachineInventory(player, event.getClickedBlock().getLocation());
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§7 ").build();
        for (int slot = 0; slot < holder.inventory.getSize(); slot++) holder.inventory.setItem(slot, pane);
        for (int slot : INPUT_SLOTS) holder.inventory.setItem(slot, null);
        holder.inventory.setItem(27, new ItemBuilder(Material.HOPPER).name("§eДополнительные ингредиенты")
                .lore("§7Положите добавки в шесть ячеек справа", "§7В нижнем ряду — образцы выбранного рецепта").build());
        holder.inventory.setItem(RESULT_SLOT, plugin.items().create(TobaccoItems.CIGARETTE));
        holder.inventory.setItem(CRAFT_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("§aСкрафтить").lore("§7Нужно: 2 листика табака и 3 бумаги", "§7Результат поступит в ваш инвентарь").build());
        refreshPreview(holder);
        sessions.put(player.getUniqueId(), holder);
        player.openInventory(holder.inventory);
    }

    private boolean accepts(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return slot == LEAF_SLOT ? plugin.items().is(item, TobaccoItems.LEAF)
                : slot == PAPER_SLOT ? TobaccoItems.isPlainPaper(item) : inputSlot(slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MachineInventory holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())
                || holder.returned || plugin.minigames().isPlaying(player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR
                || !plugin.messages().require(player, "tobaccocraft.farmer")) return;
        if (!validMachine(holder, player)) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory()); return;
        }
        refreshPreview(holder);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!holder.returned) refreshPreview(holder);
        });
        int slot = event.getRawSlot();
        if (slot < 0) return;
        if (slot >= holder.inventory.getSize()) {
            if (event.isShiftClick()) {
                ItemStack source = event.getCurrentItem();
                if (source != null && !source.getType().isAir()) {
                    int moved = 0;
                    for (int target : INPUT_SLOTS) if (accepts(target, source))
                        moved += deposit(holder.inventory, target, source, source.getAmount() - moved);
                    if (moved > 0) {
                        ItemStack rest = source.clone(); rest.setAmount(source.getAmount() - moved);
                        event.setCurrentItem(rest.getAmount() == 0 ? null : rest);
                    }
                }
            } else if (event.getAction() != InventoryAction.COLLECT_TO_CURSOR
                    && event.getAction() != InventoryAction.UNKNOWN) event.setCancelled(false);
            return;
        }
        if (slot == RESULT_SLOT || slot == 4) {
            if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
                var choices = recipesFor(holder);
                if (!choices.isEmpty()) {
                    int current = 0;
                    for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(holder.recipeId)) current = i;
                    holder.recipeId = choices.get(Math.floorMod(current + (event.isRightClick() ? -1 : 1), choices.size())).id();
                    refreshPreview(holder);
                }
            }
            return;
        }
        if (slot == CRAFT_SLOT) {
            if (event.isLeftClick() || event.isRightClick()) craft(player, holder);
            return;
        }
        if (!inputSlot(slot)) return;
        if (event.isShiftClick()) {
            ItemStack input = holder.inventory.getItem(slot);
            if (input != null) {
                holder.inventory.setItem(slot, null);
                TobaccoItems.giveOrDrop(player, input);
            }
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        ItemStack cursor = player.getItemOnCursor();
        ItemStack input = holder.inventory.getItem(slot);
        if (cursor.getType().isAir()) {
            if (input == null) return;
            int amount = event.isRightClick() ? (input.getAmount() + 1) / 2 : input.getAmount();
            ItemStack taken = input.clone(); taken.setAmount(amount);
            player.setItemOnCursor(taken);
            take(holder.inventory, slot, amount);
        } else if (accepts(slot, cursor)) {
            int amount = event.isRightClick() ? 1 : cursor.getAmount();
            int moved = deposit(holder.inventory, slot, cursor, amount);
            cursor.setAmount(cursor.getAmount() - moved);
            player.setItemOnCursor(cursor.getAmount() == 0 ? null : cursor);
        } else plugin.messages().send(player, "invalid-recipe");
    }

    private boolean validMachine(MachineInventory holder, Player player) {
        return holder.machine.getWorld() != null && holder.machine.getWorld().equals(player.getWorld())
                && holder.machine.distanceSquared(player.getLocation()) <= 64
                && isMachine(holder.machine.getBlock());
    }

    private static int deposit(Inventory inventory, int slot, ItemStack source, int requested) {
        ItemStack existing = inventory.getItem(slot);
        if (existing != null && !existing.isSimilar(source)) return 0;
        int current = existing == null ? 0 : existing.getAmount();
        int moved = Math.max(0, Math.min(Math.min(requested, source.getAmount()), source.getMaxStackSize() - current));
        if (moved > 0) {
            ItemStack result = source.clone(); result.setAmount(current + moved);
            inventory.setItem(slot, result);
        }
        return moved;
    }

    private static void take(Inventory inventory, int slot, int amount) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getAmount() < amount) throw new IllegalStateException("Недостаточно ингредиентов");
        ItemStack rest = item.clone(); rest.setAmount(item.getAmount() - amount);
        inventory.setItem(slot, rest.getAmount() == 0 ? null : rest);
    }

    private List<CigaretteRecipe> recipesFor(MachineInventory holder) {
        ItemStack leaves = holder.inventory.getItem(LEAF_SLOT);
        String variety = plugin.items().is(leaves, TobaccoItems.LEAF) ? plugin.items().varietyId(leaves) : "default";
        return plugin.catalog().recipesFor(variety);
    }

    private void refreshPreview(MachineInventory holder) {
        if (holder.returned) return;
        for (int slot : new int[]{LEAF_SLOT, PAPER_SLOT}) {
            holder.inventory.setItem(slot, plugin.items().refreshItem(holder.inventory.getItem(slot)));
        }
        var choices = recipesFor(holder);
        if (choices.stream().noneMatch(r -> r.id().equals(holder.recipeId))) holder.recipeId = choices.isEmpty() ? null : choices.getFirst().id();
        holder.inventory.setItem(4, new ItemBuilder(Material.COMPASS).name("§eВыбрать сигарету")
                .lore("§7ЛКМ — следующая, ПКМ — предыдущая", "§7Доступно для этих листьев: " + choices.size()).build());
        for (int i = 0; i < 6; i++) holder.inventory.setItem(37 + i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("§7Нет добавки").build());
        if (holder.recipeId == null) {
            holder.inventory.setItem(RESULT_SLOT, new ItemBuilder(Material.BARRIER).name("§cНет рецепта для этого сорта").build());
            holder.inventory.setItem(CRAFT_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("§cНет рецепта").build());
            return;
        }
        var recipe = plugin.catalog().recipe(holder.recipeId);
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            var sample = recipe.ingredients().get(i).sample(); var meta = sample.getItemMeta();
            var lore = new java.util.ArrayList<String>();
            lore.add("§eОбразец добавки ×" + sample.getAmount());
            lore.add("§7Нужен предмет с такими же свойствами");
            lore.add("§7Положите в любую ячейку добавок выше");
            if (meta.hasLore()) lore.addAll(meta.getLore());
            meta.setLore(lore); sample.setItemMeta(meta); holder.inventory.setItem(37 + i, sample);
        }
        holder.inventory.setItem(RESULT_SLOT, plugin.items().create(TobaccoItems.CIGARETTE, 1, recipe.id()));
        holder.inventory.setItem(CRAFT_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("§aСкрафтить")
                .lore("§7Листья: " + recipe.leavesRequired(), "§7Бумага: " + recipe.paperRequired(), "§7Добавок: " + recipe.ingredients().size() + " (образцы внизу)",
                        "§7Сорт: " + plugin.catalog().variety(recipe.varietyId()).name()).build());
    }

    private void craft(Player player, MachineInventory holder) {
        if (holder.recipeId == null) { player.sendMessage("§cДля этого сорта ещё нет сигарет."); return; }
        var recipe = plugin.catalog().recipe(holder.recipeId);
        var inventory = holder.inventory;
        ItemStack leaves = inventory.getItem(LEAF_SLOT), paper = inventory.getItem(PAPER_SLOT);
        if (!accepts(LEAF_SLOT, leaves) || !plugin.items().varietyId(leaves).equals(recipe.varietyId())
                || leaves.getAmount() < recipe.leavesRequired() || !accepts(PAPER_SLOT, paper) || paper.getAmount() < recipe.paperRequired()) {
            player.sendMessage("§cНужны листья сорта «" + plugin.catalog().variety(recipe.varietyId()).name()
                    + "§c» ×" + recipe.leavesRequired() + " и обычная бумага ×" + recipe.paperRequired() + ".");
            return;
        }
        int[] plan = IngredientMatcher.plan(recipe.ingredients(), java.util.Arrays.stream(EXTRA_SLOTS).mapToObj(inventory::getItem).toList());
        if (plan == null) { player.sendMessage("§cНе хватает добавок. Образцы и количество показаны в нижнем ряду станка."); return; }
        take(inventory, LEAF_SLOT, recipe.leavesRequired()); take(inventory, PAPER_SLOT, recipe.paperRequired());
        for (int i = 0; i < EXTRA_SLOTS.length; i++) if (plan[i] > 0) take(inventory, EXTRA_SLOTS[i], plan[i]);
        for (var ingredient : recipe.ingredients()) if (ingredient.remainder() != Material.AIR)
            TobaccoItems.giveOrDrop(player, new ItemStack(ingredient.remainder(), ingredient.amount()));
        TobaccoItems.giveOrDrop(player, plugin.items().create(TobaccoItems.CIGARETTE, 1, recipe.id()));
        refreshPreview(holder);
    }

    public void refreshViews() {
        for (MachineInventory holder : List.copyOf(sessions.values())) refreshPreview(holder);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MachineInventory
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) event.setCancelled(true);
    }

    private void returnInputs(Player player, MachineInventory holder) {
        if (holder.returned) return;
        holder.returned = true;
        sessions.remove(holder.owner, holder);
        for (int slot : INPUT_SLOTS) {
            ItemStack item = holder.inventory.getItem(slot);
            holder.inventory.setItem(slot, null);
            if (item != null) TobaccoItems.giveOrDrop(player, item);
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MachineInventory holder && event.getPlayer() instanceof Player player)
            returnInputs(player, holder);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        MachineInventory holder = sessions.get(event.getEntity().getUniqueId());
        if (holder == null || holder.returned) return;
        if (event.getKeepInventory()) { returnInputs(event.getEntity(), holder); return; }
        holder.returned = true;
        sessions.remove(holder.owner, holder);
        for (int slot : INPUT_SLOTS) {
            ItemStack item = holder.inventory.getItem(slot);
            if (item != null) event.getDrops().add(item.clone());
            holder.inventory.setItem(slot, null);
        }
    }

    private void closeAt(Location location) {
        for (MachineInventory holder : List.copyOf(sessions.values())) {
            if (holder.machine.equals(location)) {
                Player player = Bukkit.getPlayer(holder.owner);
                if (player != null) { returnInputs(player, holder); player.closeInventory(); }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isMachine(event.getBlock())) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        Location location = event.getBlock().getLocation();
        closeAt(location);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            location.getWorld().dropItemNaturally(location.add(0.5, 0.5, 0.5), plugin.items().create(TobaccoItems.MACHINE));
    }

    // Станок не использует внутренний инвентарь коптильни и не принимает предметы из воронок.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onMove(InventoryMoveItemEvent e) {
        Location source = e.getSource().getLocation(), destination = e.getDestination().getLocation();
        if ((source != null && isMachine(source.getBlock())) || (destination != null && isMachine(destination.getBlock()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onSmelt(FurnaceSmeltEvent e) {
        if (isMachine(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBurn(FurnaceBurnEvent e) {
        if (isMachine(e.getBlock())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isMachine);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isMachine);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onUnload(ChunkUnloadEvent e) {
        for (MachineInventory holder : List.copyOf(sessions.values())) {
            if (holder.machine.getWorld().equals(e.getWorld()) && (holder.machine.getBlockX() >> 4) == e.getChunk().getX()
                    && (holder.machine.getBlockZ() >> 4) == e.getChunk().getZ()) closeAt(holder.machine);
        }
    }
    public void closeAll() {
        for (MachineInventory holder : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(holder.owner);
            if (player != null) { returnInputs(player, holder); player.closeInventory(); }
        }
    }
}
