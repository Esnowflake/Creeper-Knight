package dev.creeperknight.config;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonObject;

/** Run by Gradle check on both platforms. No game or third-party test framework required. */
public final class ConfigChecks {
    private static int passed;
    public static void main(String[] args) throws Exception {
        KnightConfig defaults = new KnightConfig();
        check(defaults.wandLifetimeSeconds == 30 && defaults.wandSpeedMultiplier == 1 && !defaults.wandBlockDamage, "scepter defaults");
        check(KnightConfig.fromJson("{}").wandLifetimeSeconds == 30, "old config gains scepter defaults");
        check(defaults.fullAiTakeover, "full takeover defaults on");
        check(defaults.chaseDuringFuse && !defaults.instantExplosion && defaults.instantExplosionDistance == 2,
            "new explosion defaults");
        KnightConfig legacy = KnightConfig.fromJson("{\"speedMultiplier\":1.75,\"fullAiTakeover\":false}");
        check(legacy.chaseDuringFuse && !legacy.instantExplosion && legacy.instantExplosionDistance == 2
            && legacy.speedMultiplier == 1.75 && !legacy.fullAiTakeover, "old world config keeps its values and gains new defaults");
        check(KnightConfig.fromJson("{}").zombifiedPiglin, "missing fields retain defaults");
        check(defaults.copy().toJson().equals(defaults.toJson()), "serialization round trip");
        KnightConfig changed = defaults.copy(); changed.fullAiTakeover = false;
        check(defaults.fullAiTakeover, "client drafts do not mutate server config");
        for (KnightConfig.Option option : KnightConfig.OPTIONS) {
            if (option.isBoolean()) { option.set(changed, false); check(!(Boolean)option.get(changed), option.key()); }
            else {
                option.set(changed, option.min()); check(((Number)option.get(changed)).doubleValue() == option.min(), option.key() + " minimum");
                option.set(changed, option.max()); check(((Number)option.get(changed)).doubleValue() == option.max(), option.key() + " maximum");
                reject(() -> option.set(changed, option.min() - 1));
                reject(() -> option.set(changed, option.max() + 1));
                reject(() -> option.set(changed, Double.NaN));
                reject(() -> option.set(changed, Double.POSITIVE_INFINITY));
            }
        }
        reject(() -> KnightConfig.fromJson("null"));
        reject(() -> KnightConfig.fromJson("{\"seekIntervalTicks\":0}"));
        reject(() -> KnightConfig.fromJson("{\"seekRange\":2,\"mountDistance\":3}"));
        reject(() -> KnightConfig.fromJson(" ".repeat(8193)));
        reject(() -> KnightConfig.OPTIONS.stream().filter(o -> o.key().equals("seekIntervalTicks")).findFirst().orElseThrow().set(changed, 20.5));
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            JsonObject text = KnightConfig.GSON.fromJson(Files.readString(Path.of(args[0], "assets/creeperknight/lang/" + locale + ".json")), JsonObject.class);
            for (KnightConfig.Option option : KnightConfig.OPTIONS) {
                check(text.has("creeperknight.option." + option.key()), locale + " label " + option.key());
                check(text.has("creeperknight.option." + option.key() + ".tooltip"), locale + " tooltip " + option.key());
                if (option.isChoice()) for (int choice = (int)option.min(); choice <= option.max(); choice++)
                    check(text.has("creeperknight.choice." + option.key() + "." + choice), locale + " choice " + option.key());
            }
            check(text.has("creeperknight.wand.selfwarning") && text.has("item.creeperknight.friendship_scepter"), locale + " item and short timer warning");
        }
        System.out.println("Creeper Knight config checks passed: " + passed);
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException e) { passed++; return; }
        throw new AssertionError("Invalid configuration accepted");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); passed++; }
}
