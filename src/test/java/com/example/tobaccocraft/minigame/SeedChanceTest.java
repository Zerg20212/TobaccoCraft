package com.example.tobaccocraft.minigame;

import com.example.tobaccocraft.TobaccoCraft;
import com.example.tobaccocraft.catalog.*;
import com.example.tobaccocraft.items.TobaccoItems;
import com.example.tobaccocraft.plants.TobaccoPlant;
import com.example.tobaccocraft.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.random.RandomGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeedChanceTest {
    private TobaccoVariety variety(double chance) {
        return new TobaccoVariety("default", "Табак", "Семена", List.of(), "Листья", List.of(), 1, 3, 2, 4, 50, 120000, 0, chance);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,true", "100,99.99,3,false", "25.5,25.49,3,true", "25.5,25.5,0,true", "25.5,90,0,false"})
    void seedRollIsIndependentFromLeaves(double chance, double roll, int seedAmount, boolean leafDrop) {
        var plugin = mock(TobaccoCraft.class); var catalog = mock(CatalogManager.class);
        var items = mock(TobaccoItems.class); var messages = mock(MessageUtils.class);
        var random = mock(RandomGenerator.class); var player = mock(Player.class);
        var plant = mock(TobaccoPlant.class); var world = mock(World.class);
        var seeds = mock(ItemStack.class); var leaves = mock(ItemStack.class);
        when(plugin.catalog()).thenReturn(catalog); when(catalog.variety("default")).thenReturn(variety(chance));
        when(plant.varietyId()).thenReturn("default"); when(plant.world()).thenReturn(world);
        when(plant.location()).thenAnswer(i -> new Location(world, 1, 64, 1));
        when(plugin.items()).thenReturn(items); when(plugin.messages()).thenReturn(messages);
        when(random.nextDouble(100)).thenReturn(roll, leafDrop ? 0.0 : 99.0);
        when(random.nextInt(1, 4)).thenReturn(3); when(random.nextInt(2, 5)).thenReturn(4);
        when(items.create(TobaccoItems.SEEDS, 3, "default")).thenReturn(seeds);
        when(items.create(TobaccoItems.LEAF, 4, "default")).thenReturn(leaves);
        new MinigameManager(plugin, random).dropRewards(player, plant);
        verify(world, times(seedAmount > 0 ? 1 : 0)).dropItemNaturally(any(Location.class), same(seeds));
        verify(world, times(leafDrop ? 1 : 0)).dropItemNaturally(any(Location.class), same(leaves));
        verify(items, never()).create(TobaccoItems.SEEDS, 0, "default");
        verify(messages).send(player, "harvest-reward", "seeds", seedAmount, "leaves", leafDrop ? 4 : 0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, 100.1, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidChancesAreRejected(double chance) {
        assertThrows(IllegalArgumentException.class, () -> variety(chance));
    }
}
