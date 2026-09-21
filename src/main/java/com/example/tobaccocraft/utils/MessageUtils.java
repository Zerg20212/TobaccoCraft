package com.example.tobaccocraft.utils;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("deprecation")
public final class MessageUtils {
    private final ConfigManager config;

    public MessageUtils(ConfigManager config) { this.config = config; }

    public String text(String key, Object... replacements) {
        String text = config.message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return text;
    }

    public void send(CommandSender sender, String key, Object... replacements) {
        String message = text(key, replacements);
        if (!message.isBlank()) sender.sendMessage(message);
    }

    public boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        send(sender, "no-permission");
        return false;
    }

    public void action(Player player, String key) { actionBar(player, text(key)); }

    public static void actionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }
}
