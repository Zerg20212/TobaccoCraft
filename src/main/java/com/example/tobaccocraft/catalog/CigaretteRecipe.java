package com.example.tobaccocraft.catalog;

import java.util.List;

public record CigaretteRecipe(String id, String varietyId, String name, List<String> lore,
                              int leavesRequired, int paperRequired, List<EffectSpec> effects,
                              OverdoseSettings overdose, List<RecipeIngredient> ingredients) {
    public CigaretteRecipe(String id, String varietyId, String name, List<String> lore,
                           int leavesRequired, int paperRequired, List<EffectSpec> effects, OverdoseSettings overdose) {
        this(id, varietyId, name, lore, leavesRequired, paperRequired, effects, overdose, List.of());
    }
    public CigaretteRecipe {
        CatalogManager.checkId(id); CatalogManager.checkId(varietyId);
        CatalogManager.checkText(name, 80);
        lore = CatalogManager.checkLore(lore);
        if (leavesRequired < 1 || leavesRequired > 64 || paperRequired < 1 || paperRequired > 64)
            throw new IllegalArgumentException("Ингредиенты — от 1 до 64 штук.");
        ingredients = List.copyOf(ingredients);
        if (ingredients.size() > 6) throw new IllegalArgumentException("Максимум 6 дополнительных ингредиентов.");
        effects = List.copyOf(effects);
        CatalogManager.checkEffects(effects);
        if (overdose == null) throw new IllegalArgumentException("Не заданы последствия перекуривания.");
    }
}
