package com.example.tobaccocraft.integration;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;

import java.lang.reflect.*;
import java.util.*;

/** Игрок для серверных проверок обработчиков: без сети, NMS и изменения настоящих игроков. */
public final class IntegrationPlayer implements InvocationHandler {
    public Player player;
    public final PlayerInventory inventory;
    public final List<String> messages = new ArrayList<>();
    public final List<PotionEffect> effects = new ArrayList<>();
    public double damage;
    public boolean admin = true;
    public InventoryView view;
    public Location location;
    private final Inventory storage = Bukkit.createInventory(null, 36);
    private final PersistentDataContainer pdc;
    private final UUID uuid = UUID.randomUUID();
    private final com.example.tobaccocraft.TobaccoCraft plugin;
    private ItemStack cursor = new ItemStack(Material.AIR);

    public IntegrationPlayer(com.example.tobaccocraft.TobaccoCraft plugin, Location location) {
        this.plugin = plugin; this.location = location;
        pdc = location.getWorld().getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getItemInMainHand", "getItemInHand" -> air(storage.getItem(0));
                case "setItemInMainHand", "setItemInHand" -> { storage.setItem(0, (ItemStack) args[0]); yield null; }
                case "getItemInOffHand" -> new ItemStack(Material.AIR);
                case "getHeldItemSlot" -> 0;
                case "getStorageContents", "getContents" -> storage.getContents();
                case "setStorageContents", "setContents" -> { storage.setContents((ItemStack[]) args[0]); yield null; }
                case "getHolder" -> player;
                default -> {
                    try { yield Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                    catch (NoSuchMethodException ignored) { yield primitive(method.getReturnType()); }
                }
            };
        });
        player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class}, this);
        view = view(Bukkit.createInventory(null, 9));
    }

    private static ItemStack air(ItemStack item) { return item == null ? new ItemStack(Material.AIR) : item; }
    private static Object primitive(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return (char) 0;
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getName" -> "Проверка";
            case "getServer" -> Bukkit.getServer();
            case "isOnline", "isValid" -> true;
            case "isDead" -> false;
            case "hasPermission", "isPermissionSet", "isOp" -> admin;
            case "getGameMode" -> GameMode.SURVIVAL;
            case "getWorld" -> location.getWorld();
            case "getLocation", "getEyeLocation" -> location.clone();
            case "getInventory" -> inventory;
            case "getOpenInventory" -> view;
            case "getPersistentDataContainer" -> pdc;
            case "getItemOnCursor" -> cursor;
            case "setItemOnCursor" -> { cursor = air((ItemStack) args[0]); yield null; }
            case "closeInventory" -> {
                plugin.crafting().onClose(new InventoryCloseEvent(view));
                view = view(Bukkit.createInventory(null, 9)); yield null;
            }
            case "openInventory" -> { view = view((Inventory) args[0]); yield view; }
            case "sendMessage" -> {
                for (Object arg : args) {
                    if (arg instanceof String text) messages.add(text);
                    if (arg instanceof String[] lines) messages.addAll(Arrays.asList(lines));
                }
                yield null;
            }
            case "addPotionEffect" -> { effects.add((PotionEffect) args[0]); yield true; }
            case "damage" -> { damage += (double) args[0]; yield null; }
            case "spigot" -> new Player.Spigot() {
                @Override public void sendMessage(ChatMessageType position, BaseComponent... components) {}
            };
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Проверочный игрок";
            default -> primitive(method.getReturnType());
        };
    }

    private static int localSlot(int raw, int size) {
        if (raw < size) return raw;
        int lower = raw - size;
        return lower < 27 ? lower + 9 : lower - 27;
    }

    private InventoryView view(Inventory top) {
        return (InventoryView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getTopInventory" -> top;
            case "getBottomInventory" -> storage;
            case "getPlayer" -> player;
            case "getType" -> top.getType();
            case "getTitle", "getOriginalTitle" -> "Проверка";
            case "getCursor" -> cursor;
            case "setCursor" -> { cursor = air((ItemStack) args[0]); yield null; }
            case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top : storage;
            case "convertSlot" -> localSlot((int) args[0], top.getSize());
            case "getItem" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top.getItem((int) args[0]) : storage.getItem(localSlot((int) args[0], top.getSize()));
            case "setItem" -> {
                int raw = (int) args[0];
                if (raw >= 0 && raw < top.getSize()) top.setItem(raw, (ItemStack) args[1]);
                else if (raw >= top.getSize()) storage.setItem(localSlot(raw, top.getSize()), (ItemStack) args[1]);
                yield null;
            }
            case "countSlots" -> top.getSize() + storage.getSize();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> primitive(method.getReturnType());
        });
    }
}
