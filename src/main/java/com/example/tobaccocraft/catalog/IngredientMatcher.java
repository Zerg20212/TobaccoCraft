package com.example.tobaccocraft.catalog;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public final class IngredientMatcher {
    private IngredientMatcher() {}

    /** План списания строится целиком до изменения инвентаря; один предмет не учитывается дважды. */
    public static int[] plan(List<RecipeIngredient> requirements, List<ItemStack> inputs) {
        int[] used = new int[inputs.size()];
        for (RecipeIngredient requirement : requirements) {
            int remaining = requirement.amount();
            for (int i = 0; i < inputs.size() && remaining > 0; i++) {
                ItemStack input = inputs.get(i);
                if (!requirement.matches(input)) continue;
                int take = Math.min(remaining, input.getAmount() - used[i]);
                used[i] += take;
                remaining -= take;
            }
            if (remaining > 0) return null;
        }
        return used;
    }
}
