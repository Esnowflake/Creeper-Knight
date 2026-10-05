package dev.creeperknight.forge;

import dev.creeperknight.test.KnightGameChecks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder("creeperknight")
@PrefixGameTestTemplate(false)
public final class KnightForgeGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void behavior(GameTestHelper helper) { KnightGameChecks.run(helper); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void movement(GameTestHelper helper) { KnightGameChecks.movement(helper); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fuseMovement(GameTestHelper helper) { KnightGameChecks.fuseMovement(helper); }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void scepter(GameTestHelper helper) { dev.creeperknight.test.ScepterGameChecks.run(helper); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scepterMovement(GameTestHelper helper) { dev.creeperknight.test.ScepterGameChecks.movement(helper); }
    @GameTest(template = "empty", timeoutTicks = 850)
    public static void highSpeed(GameTestHelper helper) { dev.creeperknight.test.HighSpeedGameChecks.run(helper); }
}
