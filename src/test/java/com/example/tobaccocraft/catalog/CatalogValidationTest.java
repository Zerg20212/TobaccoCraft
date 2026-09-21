package com.example.tobaccocraft.catalog;

import com.example.tobaccocraft.listeners.CatalogEditor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CatalogValidationTest {
    private TobaccoVariety variety(String id) {
        return new TobaccoVariety(id, "§aВирджиния", "§aСемена", List.of(), "§aЛистья", List.of(), 1, 3, 2, 4, 50);
    }
    private OverdoseSettings overdose() {
        return new OverdoseSettings(3, 300, 12, List.of(), "", Material.AIR, 1, "§7Пепел", List.of(), 0);
    }
    @Test void colorAndMultilineChatInput() {
        assertEquals("§aСвой сорт", CatalogEditor.name("&aСвой сорт"));
        assertEquals(List.of("§7Первая", "§aВторая"), CatalogEditor.lore("&7Первая | &aВторая"));
        assertTrue(CatalogEditor.lore("-").isEmpty());
        assertTrue(CatalogEditor.lore("очистить").isEmpty());
    }
    @Test void namesCannotBeBlankOrOnlyColorCodes() {
        assertThrows(IllegalArgumentException.class, () -> CatalogEditor.name("&a"));
        assertThrows(IllegalArgumentException.class, () -> CatalogEditor.name(" "));
        assertThrows(IllegalArgumentException.class, () -> CatalogEditor.name("а".repeat(81)));
    }
    @Test void descriptionsAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> CatalogEditor.lore("а".repeat(121)));
        assertThrows(IllegalArgumentException.class, () -> CatalogEditor.lore("строка|".repeat(11)));
    }
    @Test void invalidRewardRangesAndChanceAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TobaccoVariety("a", "a", "a", List.of(), "a", List.of(), 3, 1, 1, 2, 50));
        assertThrows(IllegalArgumentException.class, () -> new TobaccoVariety("a", "a", "a", List.of(), "a", List.of(), 1, 2, 1, 2, Double.NaN));
    }
    @Test void recipeMustReferenceExistingVariety() {
        var recipe = new CigaretteRecipe("default", "missing", "Сигарета", List.of(), 2, 3, List.of(), overdose());
        assertThrows(IllegalArgumentException.class, () -> new CatalogManager.Catalog(Map.of("default", variety("default")), Map.of("default", recipe)));
    }
    @Test void duplicateEffectsAreRejected() {
        var effect = new EffectSpec("speed", 1, 60);
        assertThrows(IllegalArgumentException.class, () -> new CigaretteRecipe("default", "default", "Сигарета", List.of(), 2, 3, List.of(effect, effect), overdose()));
    }
    @Test void levelsAreHumanReadableAndBounded() {
        assertEquals(2, new EffectSpec("speed", 2, 60).level());
        assertThrows(IllegalArgumentException.class, () -> new EffectSpec("speed", 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new EffectSpec("speed", 1, 0));
    }
    @Test void disabledOverdoseDropCreatesNothing() { assertNull(overdose().createDrop()); }
    @Test void catalogAndProfileListsAreImmutable() {
        var v = variety("default");
        var recipe = new CigaretteRecipe("default", "default", "Сигарета", List.of(), 2, 3, List.of(), overdose());
        var catalog = new CatalogManager.Catalog(Map.of("default", v), Map.of("default", recipe));
        assertThrows(UnsupportedOperationException.class, () -> catalog.varieties().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.seedsLore().add("постороннее"));
    }
}
