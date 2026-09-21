package com.example.tobaccocraft.catalog;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngredientMatcherTest {
    private ItemStack stack(int amount) {
        var item = mock(ItemStack.class); when(item.getAmount()).thenReturn(amount); return item;
    }
    private RecipeIngredient need(int amount, ItemStack... matching) {
        var ingredient = mock(RecipeIngredient.class); when(ingredient.amount()).thenReturn(amount);
        for (var item : matching) when(ingredient.matches(item)).thenReturn(true);
        return ingredient;
    }
    @Test void multipleIngredientsRequiredTogether() {
        var lapis = stack(4); var berries = stack(5);
        assertArrayEquals(new int[]{2, 3}, IngredientMatcher.plan(List.of(need(2, lapis), need(3, berries)), List.of(lapis, berries)));
    }
    @Test void incompleteRecipeDoesNotMutateInputs() {
        var lapis = stack(4);
        assertNull(IngredientMatcher.plan(List.of(need(2, lapis), need(1)), Arrays.asList(lapis, null)));
        verify(lapis, never()).setAmount(anyInt());
    }
    @Test void stacksMayBeSplitAndInAnyOrder() {
        var a = stack(2); var b = stack(3);
        assertArrayEquals(new int[]{2, 0, 2}, IngredientMatcher.plan(List.of(need(4, a, b)), Arrays.asList(a, null, b)));
    }
    @Test void duplicateRequirementsCannotCountSameItemTwice() {
        var item = stack(3);
        assertNull(IngredientMatcher.plan(List.of(need(2, item), need(2, item)), List.of(item)));
        assertArrayEquals(new int[]{3}, IngredientMatcher.plan(List.of(need(1, item), need(2, item)), List.of(item)));
    }
    @Test void unrelatedItemsAreNotConsumed() {
        var gold = stack(2); var unrelated = stack(64);
        assertArrayEquals(new int[]{0, 1}, IngredientMatcher.plan(List.of(need(1, gold)), List.of(unrelated, gold)));
    }
    @Test void oldRecipeRequiresNoExtras() {
        assertArrayEquals(new int[]{0, 0}, IngredientMatcher.plan(List.of(), Arrays.asList(stack(5), null)));
    }
}
