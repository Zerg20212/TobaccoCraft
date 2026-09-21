package com.example.tobaccocraft.catalog;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;

public record EffectSpec(String key, int level, int seconds) {
    public EffectSpec {
        if (key == null || !key.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Некорректный эффект.");
        if (level < 1 || level > 10 || seconds < 1 || seconds > 86400)
            throw new IllegalArgumentException("Уровень эффекта — от 1 до 10; длительность — от 1 до 86400 секунд.");
    }

    public PotionEffect toPotionEffect() {
        var type = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (type == null) throw new IllegalArgumentException("Неизвестный эффект: " + key);
        return new PotionEffect(type, seconds * 20, level - 1);
    }

    public String displayName() { return displayName(key); }
    public static String displayName(String key) {
        return switch (key) {
            case "speed" -> "Скорость"; case "slowness" -> "Замедление";
            case "haste" -> "Спешка"; case "mining_fatigue" -> "Усталость";
            case "strength" -> "Сила"; case "instant_health" -> "Мгновенное лечение";
            case "instant_damage" -> "Мгновенный урон"; case "jump_boost" -> "Прыгучесть";
            case "nausea" -> "Тошнота"; case "regeneration" -> "Регенерация";
            case "resistance" -> "Сопротивление"; case "fire_resistance" -> "Огнестойкость";
            case "water_breathing" -> "Подводное дыхание"; case "invisibility" -> "Невидимость";
            case "blindness" -> "Слепота"; case "night_vision" -> "Ночное зрение";
            case "hunger" -> "Голод"; case "weakness" -> "Слабость"; case "poison" -> "Отравление";
            case "wither" -> "Иссушение"; case "health_boost" -> "Прилив здоровья";
            case "absorption" -> "Поглощение"; case "saturation" -> "Насыщение";
            case "glowing" -> "Свечение"; case "levitation" -> "Левитация";
            case "luck" -> "Удача"; case "unluck" -> "Невезение";
            case "slow_falling" -> "Плавное падение"; case "conduit_power" -> "Сила источника";
            case "dolphins_grace" -> "Грация дельфина"; case "bad_omen" -> "Дурное знамение";
            case "hero_of_the_village" -> "Герой деревни"; case "darkness" -> "Тьма";
            case "trial_omen" -> "Зловещее испытание"; case "raid_omen" -> "Дурное предчувствие";
            case "wind_charged" -> "Заряд ветра"; case "weaving" -> "Плетение";
            case "oozing" -> "Слизистость"; case "infested" -> "Заражение";
            default -> "Эффект " + key;
        };
    }
}
