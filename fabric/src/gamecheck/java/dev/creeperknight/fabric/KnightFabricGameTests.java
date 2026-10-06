package dev.creeperknight.fabric;

import dev.creeperknight.test.KnightGameChecks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class KnightFabricGameTests implements FabricGameTest {
    @GameTest(template = "creeperknight:empty", timeoutTicks = 1000)
    public void playerRiding(GameTestHelper helper) { dev.creeperknight.test.PlayerRidingGameChecks.run(helper); }
    @GameTest(template = "creeperknight:empty", timeoutTicks = 200)
    public void behavior(GameTestHelper helper) { KnightGameChecks.run(helper); }
    @GameTest(template = "creeperknight:empty", timeoutTicks = 100)
    public void movement(GameTestHelper helper) { KnightGameChecks.movement(helper); }
    @GameTest(template = "creeperknight:empty", timeoutTicks = 100)
    public void fuseMovement(GameTestHelper helper) { KnightGameChecks.fuseMovement(helper); }
    @GameTest(template = "creeperknight:empty", timeoutTicks = 200)
    public void scepter(GameTestHelper helper) { dev.creeperknight.test.ScepterGameChecks.run(helper); }
    @GameTest(template = "creeperknight:empty", timeoutTicks = 100)
    public void scepterMovement(GameTestHelper helper) { dev.creeperknight.test.ScepterGameChecks.movement(helper); }
    @GameTest(template = "creeperknight:empty", timeoutTicks = 850)
    public void highSpeed(GameTestHelper helper) { dev.creeperknight.test.HighSpeedGameChecks.run(helper); }
}
