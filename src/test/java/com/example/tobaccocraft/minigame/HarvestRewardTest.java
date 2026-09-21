package com.example.tobaccocraft.minigame;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.catalog.CatalogManager;
import com.example.tobaccocraft.catalog.TobaccoVariety;
import java.util.List;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.plants.TobaccoPlant;
import com.example.tobaccocraft.utils.ConfigManager;
import com.example.tobaccocraft.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.random.RandomGenerator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HarvestRewardTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void seedsAlwaysDropIncludingWhenLeavesDrop(boolean leavesRoll) {
        TobaccoCraft plugin = mock(TobaccoCraft.class);
        CatalogManager catalog = mock(CatalogManager.class);
        TobaccoItems items = mock(TobaccoItems.class);
        MessageUtils messages = mock(MessageUtils.class);
        RandomGenerator random = mock(RandomGenerator.class);
        Player player = mock(Player.class);
        TobaccoPlant plant = mock(TobaccoPlant.class);
        World world = mock(World.class);
        ItemStack seeds = mock(ItemStack.class), leaves = mock(ItemStack.class);
        when(plugin.catalog()).thenReturn(catalog);
        when(plant.varietyId()).thenReturn("virginia");
        when(catalog.variety("virginia")).thenReturn(new TobaccoVariety("virginia", "Вирджиния", "Семена", List.of(), "Листья", List.of(), 1, 3, 2, 4, 50));
        when(plugin.items()).thenReturn(items);
        when(plugin.messages()).thenReturn(messages);
        when(random.nextInt(1, 4)).thenReturn(3);
        when(random.nextInt(2, 5)).thenReturn(4);
        when(random.nextDouble(100)).thenReturn(leavesRoll ? 0.0 : 99.0);
        when(items.create(TobaccoItems.SEEDS, 3, "virginia")).thenReturn(seeds);
        when(items.create(TobaccoItems.LEAF, 4, "virginia")).thenReturn(leaves);
        when(plant.world()).thenReturn(world);
        when(plant.location()).thenAnswer(invocation -> new Location(world, 10, 64, 10));

        new MinigameManager(plugin, random).dropRewards(player, plant);

        verify(world).dropItemNaturally(any(Location.class), same(seeds));
        verify(world, times(leavesRoll ? 1 : 0)).dropItemNaturally(any(Location.class), same(leaves));
        verify(world, times(leavesRoll ? 2 : 1)).dropItemNaturally(any(Location.class), any(ItemStack.class));
        verify(messages).send(player, "harvest-reward", "seeds", 3, "leaves", leavesRoll ? 4 : 0);
    }
}
