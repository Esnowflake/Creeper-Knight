package dev.creeperknight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;

/** All values are authoritative on the server. Probabilities are fractions, not percentages. */
public final class KnightConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean zombie = true;
    public boolean husk = true;
    public boolean drowned = true;
    public boolean zombieVillager = true;
    public boolean zombifiedPiglin = true;
    public boolean naturalSpawning = true;
    public double naturalChance = 0.01;
    public boolean seekMounts = true;
    public double seekChance = 0.01;
    public int seekIntervalTicks = 200;
    public double seekRange = 8.0;
    public double mountDistance = 2.0;
    public double speedMultiplier = 1.5;
    public boolean fullAiTakeover = true;
    public boolean chaseDuringFuse = true;
    public boolean instantExplosion = false;
    public double instantExplosionDistance = 2.0;
    public boolean attackPlayers = true;
    public boolean attackVillagers = true;
    public boolean attackIronGolems = true;
    public boolean attackOtherTargets = true;
    public boolean wandEnabled = true;
    public boolean wandCraftable = true;
    public int wandRiderType = 0;
    public int wandLifetimeSeconds = 30;
    public double wandSpeedMultiplier = 1;
    public boolean wandBlockDamage = false;
    public int wandNotifications = 0;
    public int wandGlowSeconds = 10;
    public int wandCooldownSeconds = 10;
    public double wandLockRange = 32;
    public boolean wandConsumeGunpowder = true;

    public enum Group { RIDERS, SPAWNING, SEEKING, MOVEMENT, TARGETS, SCEPTER }
    public record Option(String key, Group group, double min, double max) {
        public Object get(KnightConfig config) {
            try { return KnightConfig.class.getField(key).get(config); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        public boolean isBoolean() { return get(new KnightConfig()) instanceof Boolean; }
        public boolean isChoice() { return key.equals("wandRiderType") || key.equals("wandNotifications"); }
        public void set(KnightConfig config, Object value) {
            try {
                var field = KnightConfig.class.getField(key);
                if (field.getType() == boolean.class) field.setBoolean(config, (Boolean)value);
                else {
                    double number = ((Number)value).doubleValue();
                    if (!Double.isFinite(number) || number < min || number > max)
                        throw new IllegalArgumentException(key + " must be in [" + min + ", " + max + "]");
                    if (field.getType() == int.class) {
                        if (number != Math.rint(number)) throw new IllegalArgumentException(key + " must be an integer");
                        field.setInt(config, (int)number);
                    } else field.setDouble(config, number);
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }
    public static final List<Option> OPTIONS = List.of(
        new Option("zombie", Group.RIDERS, 0, 1),
        new Option("husk", Group.RIDERS, 0, 1),
        new Option("drowned", Group.RIDERS, 0, 1),
        new Option("zombieVillager", Group.RIDERS, 0, 1),
        new Option("zombifiedPiglin", Group.RIDERS, 0, 1),
        new Option("naturalSpawning", Group.SPAWNING, 0, 1),
        new Option("naturalChance", Group.SPAWNING, 0, 1),
        new Option("seekMounts", Group.SEEKING, 0, 1),
        new Option("seekChance", Group.SEEKING, 0, 1),
        new Option("seekIntervalTicks", Group.SEEKING, 20, 12000),
        new Option("seekRange", Group.SEEKING, 2, 32),
        new Option("mountDistance", Group.SEEKING, 0.5, 3),
        new Option("speedMultiplier", Group.MOVEMENT, 0.1, 4),
        new Option("fullAiTakeover", Group.MOVEMENT, 0, 1),
        new Option("chaseDuringFuse", Group.MOVEMENT, 0, 1),
        new Option("instantExplosion", Group.MOVEMENT, 0, 1),
        new Option("instantExplosionDistance", Group.MOVEMENT, 0.5, 16),
        new Option("attackPlayers", Group.TARGETS, 0, 1),
        new Option("attackVillagers", Group.TARGETS, 0, 1),
        new Option("attackIronGolems", Group.TARGETS, 0, 1),
        new Option("attackOtherTargets", Group.TARGETS, 0, 1),
        new Option("wandEnabled", Group.SCEPTER, 0, 1),
        new Option("wandCraftable", Group.SCEPTER, 0, 1),
        new Option("wandRiderType", Group.SCEPTER, 0, 4),
        new Option("wandLifetimeSeconds", Group.SCEPTER, 0, 120),
        new Option("wandSpeedMultiplier", Group.SCEPTER, 1, 10),
        new Option("wandBlockDamage", Group.SCEPTER, 0, 1),
        new Option("wandNotifications", Group.SCEPTER, 0, 2),
        new Option("wandGlowSeconds", Group.SCEPTER, 0, 120),
        new Option("wandCooldownSeconds", Group.SCEPTER, 0, 120),
        new Option("wandLockRange", Group.SCEPTER, 2, 128),
        new Option("wandConsumeGunpowder", Group.SCEPTER, 0, 1)
    );
    public void validate() {
        for (Option option : OPTIONS) option.set(this, option.get(this));
        if (mountDistance > seekRange) throw new IllegalArgumentException("mountDistance must not exceed seekRange");
    }
    public static KnightConfig fromJson(String json) {
        if (json.length() > 8192) throw new IllegalArgumentException("Config too large");
        KnightConfig config = GSON.fromJson(json, KnightConfig.class);
        if (config == null) throw new IllegalArgumentException("Missing config");
        config.validate();
        return config;
    }
    public String toJson() { return GSON.toJson(this); }
    public KnightConfig copy() { return fromJson(toJson()); }
}
