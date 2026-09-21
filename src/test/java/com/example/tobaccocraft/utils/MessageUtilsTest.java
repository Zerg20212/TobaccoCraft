package com.example.tobaccocraft.utils;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class MessageUtilsTest {
    @Test void legacyPrefixIsNeverPrepended() {
        var config = mock(ConfigManager.class);
        var sender = mock(CommandSender.class);
        when(config.message("prefix")).thenReturn("§6[TobaccoCraft] ");
        when(config.message("grown")).thenReturn("§aВыращено: {count}");
        new MessageUtils(config).send(sender, "grown", "count", 2);
        verify(sender).sendMessage("§aВыращено: 2");
        verify(config, never()).message("prefix");
    }
    @Test void emptyMessageDoesNotSendBlankChatLine() {
        var config = mock(ConfigManager.class);
        var sender = mock(CommandSender.class);
        when(config.message("planted")).thenReturn("");
        new MessageUtils(config).send(sender, "planted");
        verifyNoInteractions(sender);
    }
}
