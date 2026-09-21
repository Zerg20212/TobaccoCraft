package com.example.tobaccocraft.catalog;

import java.util.List;

public record TobaccoVariety(String id, String name, String seedsName, List<String> seedsLore,
                             String leafName, List<String> leafLore,
                             int seedsMin, int seedsMax, int leafMin, int leafMax, double leafChance,
                             long growthMillis, long wiltMillis, double seedsChance) {
    public TobaccoVariety(String id, String name, String seedsName, List<String> seedsLore,
                          String leafName, List<String> leafLore, int seedsMin, int seedsMax,
                          int leafMin, int leafMax, double leafChance, long growthMillis, long wiltMillis) {
        this(id, name, seedsName, seedsLore, leafName, leafLore, seedsMin, seedsMax, leafMin, leafMax, leafChance, growthMillis, wiltMillis, 100);
    }
    public TobaccoVariety(String id, String name, String seedsName, List<String> seedsLore,
                          String leafName, List<String> leafLore, int seedsMin, int seedsMax,
                          int leafMin, int leafMax, double leafChance) {
        this(id, name, seedsName, seedsLore, leafName, leafLore, seedsMin, seedsMax, leafMin, leafMax, leafChance, 12000000L, 0);
    }
    public TobaccoVariety {
        if (growthMillis < 1 || growthMillis > 2_000_000_000_000_000L || wiltMillis < 0 || wiltMillis > 2_000_000_000_000_000L)
            throw new IllegalArgumentException("Время роста должно быть положительным; увядание — 0 (отключено) или положительное время.");
        CatalogManager.checkId(id);
        CatalogManager.checkText(name, 80);
        CatalogManager.checkText(seedsName, 80);
        CatalogManager.checkText(leafName, 80);
        seedsLore = CatalogManager.checkLore(seedsLore);
        leafLore = CatalogManager.checkLore(leafLore);
        if (seedsMin < 1 || seedsMax < seedsMin || seedsMax > 64 || leafMin < 1 || leafMax < leafMin || leafMax > 64
                || !Double.isFinite(seedsChance) || seedsChance < 0 || seedsChance > 100
                || !Double.isFinite(leafChance) || leafChance < 0 || leafChance > 100)
            throw new IllegalArgumentException("Количество — от 1 до 64, минимум не больше максимума; шанс — от 0 до 100%.");
    }
}
