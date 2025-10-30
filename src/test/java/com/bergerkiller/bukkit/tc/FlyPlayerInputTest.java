package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.player.pmc.PlayerMovementController;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Tests that the correct motion is predicted based on player input.
 * This has to be exact for correct client state prediction.
 */
public class FlyPlayerInputTest {

    @Test
    public void testFlyInputPrediction() {
        // Note: Bukkit does a X 2.0F internally. Weird!
        float bukkitFlySpeed = 0.5F * 0.1f;

        boolean hasFailures = false;
        for (TestCase c : getTestCases_1_21_10()) {
            Vector motion = c.motion.clone();
            motion.add(getMotion_1_21_10(bukkitFlySpeed, c));
            Vector pos = c.start.clone();
            pos.add(motion);
            if (pos.getX() == c.expected.getX() && pos.getY() == c.expected.getY() && pos.getZ() == c.expected.getZ()) {
                //c.logPass();
            } else {
                c.logFail(pos);
                hasFailures = true;
            }
        }

        if (hasFailures) {
            fail("Movement prediction failures were found in the test cases");
        }
    }

    /**
     * Test different algorithms to calculate the player-input motion vector here
     *
     * @param currSpeed Bukkit fly speed (note: x 0.5F as Bukkit does a X 2.0F internally)
     * @param testCase TestCase
     * @return Motion Vector
     */
    private static Vector getMotion_1_21_10(float currSpeed, TestCase testCase) {
        // Constants for 1.21.10
        double diagonalSpeedFactor = 0.7071067094802855;
        float verticalSpeedFactor = 3.0f;
        AttachmentViewer.Input input = testCase.input;
        PlayerMovementController.ForwardMotion currForward = testCase.fwd;

        /* ================== Implementation =================== */

        double horSpeedDbl = (double) currSpeed;
        double verSpeedDbl = (double) (currSpeed * verticalSpeedFactor);

        if (input.hasDiagonalWalkInput()) {
            horSpeedDbl *= diagonalSpeedFactor;
        } else {
            horSpeedDbl *= 0.98F;
        }

        double left = input.sidewaysSigNum() * horSpeedDbl; // left/right
        double forward = input.forwardsSigNum() * horSpeedDbl; // forward/backward
        double vertical = input.verticalSigNum() * verSpeedDbl; // jump/sneak

        return new Vector(
                forward * currForward.dx + left * currForward.dz,
                vertical,
                forward * currForward.dz - left * currForward.dx);

        /*
        double speed = (double) (flySpeed);
        if (testCase.horizontalInput.diagonal()) {
            speed *= 0.7071067094802855;
        } else {
            speed *= 0.98F;
        }

        double left = testCase.horizontalInput.leftSteerInput() * speed;
        double forward = testCase.horizontalInput.forwardSteerInput() * speed;

        return new Vector(
                forward * testCase.fwd.dx + left * testCase.fwd.dz,
                0.0,
                forward * testCase.fwd.dz - left * testCase.fwd.dx);
         */
    }

    /**
     * Sampled test cases from Minecraft 1.21.10 movement behavior, testing
     * various button inputs and the delta motion that was sent by the client.
     *
     * @return TestCase Array
     */
    private static TestCase[] getTestCases_1_21_10() {
        return new TestCase[] {
                /*
                 * ============================================================================
                 * ===================== Vertical Flight test cases =========================
                 * ============================================================================
                 */

                new TestCase(
                        "NEW TEST CASE FOR NONE + JUMP",
                        PlayerMovementController.HorizontalPlayerInput.NONE,
                        PlayerMovementController.VerticalPlayerInput.JUMP,
                        new Vector(-175.5, 14.1, 354.5),
                        new Vector(-175.5, 14.250000005960464, 354.5),
                        new Vector(0.0, 0.0, 0.0),
                        0.0f
                ),

                new TestCase(
                        "NEW TEST CASE FOR NONE + SNEAK",
                        PlayerMovementController.HorizontalPlayerInput.NONE,
                        PlayerMovementController.VerticalPlayerInput.SNEAK,
                        new Vector(-175.5, 14.1, 354.5),
                        new Vector(-175.5, 13.949999994039535, 354.5),
                        new Vector(0.0, 0.0, 0.0),
                        0.0f
                ),

                /*
                 * ============================================================================
                 * ===================== Horizontal Flight test cases =========================
                 * ============================================================================
                 */

                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.53403707133356, 0.0, 354.53662618546764),
                        new Vector(0.0, 0.0, 0.0),
                        -182.10194f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.45187993885446, 0.0, 354.4864259437448),
                        new Vector(0.0, 0.0, 0.0),
                        -60.749893f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 11.380528937331835, 354.5),
                        new Vector(-175.53535533600086, 10.988142794976211, 354.46464466399914),
                        new Vector(0.0, -0.6539769039260399, 0.0),
                        90.0f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_LEFT,
                        new Vector(-175.5, 12.06674079447702, 354.5),
                        new Vector(-175.46464466399914, 12.751097752629198, 354.53535533600086),
                        new Vector(0.0, 0.6678472340408312, 0.0),
                        90.0f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT AT ANGLE",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 4.720384578001238, 354.5),
                        new Vector(-175.49282948141882, 4.578465132124281, 354.4505168392956),
                        new Vector(0.0, -0.2342310540616559, 0.0),
                        143.25002f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.54900000168382, 0.0, 354.5),
                        new Vector(0.0, 0.0, 0.0),
                        90.0f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS AT ANGLE",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS,
                        new Vector(-175.5, 14.804024003142358, 354.5),
                        new Vector(-175.51514272735895, 15.481377640399607, 354.45339852000075),
                        new Vector(0.0, 0.6875464525175925, 0.0),
                        162.00002f
                ),
                new TestCase(
                        "NEW TEST CASE FOR RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.RIGHT,
                        new Vector(-175.5, 7.776692441569732, 354.5),
                        new Vector(-175.5, 8.319556418426833, 354.45099999831615),
                        new Vector(0.0, 0.5120574580490818, 0.0),
                        90.0f
                ),
                new TestCase(
                        "NEW TEST CASE FOR LEFT",
                        PlayerMovementController.HorizontalPlayerInput.LEFT,
                        new Vector(-175.5, 11.791706189425117, 354.5),
                        new Vector(-175.5, 12.06674079447702, 354.54900000168385),
                        new Vector(0.0, 0.39281262898892705, 0.0),
                        90.0f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 20.448552630614877, 354.5),
                        new Vector(-175.4500556200502, 20.95907093243231, 354.49764237925524),
                        new Vector(0.0, 0.541447812079511, 0.0),
                        222.30006f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.51619601147428, 0.0, 354.54730221426496),
                        new Vector(0.0, 0.0, 0.0),
                        -26.1f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.45000151193076, 0.0, 354.50038828413034),
                        new Vector(0.0, 0.0, 0.0),
                        45.449997f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.54948247371084, 0.0, 354.49282473568405),
                        new Vector(0.0, 0.0, 0.0),
                        -216.75021f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.47135537841527, 0.0, 354.5409805162806),
                        new Vector(0.0, 0.0, 0.0),
                        -79.9502f
                ),
                new TestCase(
                        "NEW TEST CASE FOR RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.5312339227204, 0.0, 354.4622449698587),
                        new Vector(0.0, 0.0, 0.0),
                        50.39982f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.4892227462034, 0.0, 354.4511753099896),
                        new Vector(0.0, 0.0, 0.0),
                        57.449814f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.46437119668224, 0.0, 354.5350796854774),
                        new Vector(0.0, 0.0, 0.0),
                        -0.45019203f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.53116056405463, 0.0, 354.4608973313874),
                        new Vector(0.0, 0.0, 0.0),
                        96.44983f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.45375665145528, 0.0, 354.48098547095896),
                        new Vector(0.0, 0.0, 0.0),
                        202.64983f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.51744184840058, 0.0, 354.45420936510567),
                        new Vector(0.0, 0.0, 0.0),
                        339.1499f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.54573182704, 0.0, 354.4797861550929),
                        new Vector(0.0, 0.0, 0.0),
                        338.8499f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.45925537738262, 0.0, 354.5272190351303),
                        new Vector(0.0, 0.0, 0.0),
                        303.74985f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.46535508787787, 0.0, 354.4653484434499),
                        new Vector(0.0, 0.0, 0.0),
                        224.9999f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.5437764386081, 0.0, 354.47798583008694),
                        new Vector(0.0, 0.0, 0.0),
                        116.699875f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.54648733768332, 0.0, 354.51840996942997),
                        new Vector(0.0, 0.0, 0.0),
                        23.399864f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.48504757965233, 0.0, 354.45229016884696),
                        new Vector(0.0, 0.0, 0.0),
                        -27.600138f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.47741778795634, 0.0, 354.4553916908866),
                        new Vector(0.0, 0.0, 0.0),
                        -18.150135f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.54833528142373, 0.0, 354.50804367298707),
                        new Vector(0.0, 0.0, 0.0),
                        -99.45014f
                ),
                new TestCase(
                        "NEW TEST CASE FOR BACKWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.BACKWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.51644671039057, 0.0, 354.5472176371377),
                        new Vector(0.0, 0.0, 0.0),
                        -205.80011f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.50937339718502, 0.0, 354.4508864692324),
                        new Vector(0.0, 0.0, 0.0),
                        214.19989f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.48037973650455, 0.0, 354.54598961610924),
                        new Vector(0.0, 0.0, 0.0),
                        21.899593f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_RIGHT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_RIGHT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.45191227286017, 0.0, 354.48630437643914),
                        new Vector(0.0, 0.0, 0.0),
                        -150.9004f
                ),
                new TestCase(
                        "NEW TEST CASE FOR FORWARDS_LEFT",
                        PlayerMovementController.HorizontalPlayerInput.FORWARDS_LEFT,
                        new Vector(-175.5, 0.0, 354.5),
                        new Vector(-175.47965908340927, 0.0, 354.5456754482593),
                        new Vector(0.0, 0.0, 0.0),
                        20.999302f
                )
        };
    }

    // Just X axis!
    private static class TestCase {
        public final String desc;
        public final PlayerMovementController.HorizontalPlayerInput horizontalInput;
        public final PlayerMovementController.VerticalPlayerInput verticalInput;
        public final AttachmentViewer.Input input;
        public final Vector start;
        public final Vector expected;
        public final Vector motion;
        public final float yaw;
        public final PlayerMovementController.ForwardMotion fwd;

        public TestCase(String desc, PlayerMovementController.HorizontalPlayerInput horizontalInput, Vector start, Vector expected, Vector motion, float yaw) {
            this(desc, horizontalInput, PlayerMovementController.VerticalPlayerInput.NONE, start, expected, motion, yaw);

            // Bleh
            this.start.setY(20.0);
            this.expected.setY(20.0);
            this.motion.setY(0.0);
        }

        public TestCase(String desc, PlayerMovementController.HorizontalPlayerInput horizontalInput, PlayerMovementController.VerticalPlayerInput verticalInput, Vector start, Vector expected, Vector motion, float yaw) {
            this.desc = desc;
            this.horizontalInput = horizontalInput;
            this.verticalInput = verticalInput;
            this.input = PlayerMovementController.composeInput(horizontalInput, verticalInput);
            this.start = start;
            this.expected = expected;
            this.motion = motion;
            this.yaw = yaw;
            this.fwd = PlayerMovementController.ForwardMotion.get(yaw);
        }

        public void logPass() {
            System.out.println("TestCase{" + desc + "} = PASS");
        }

        public void logFail(Vector calculated) {
            System.out.println("TestCase{" + desc + "} = FAIL");
            System.out.println("            " + withPadding("X") + withPadding("Y") + withPadding("Z"));
            System.out.println("  Expected: " + dblWithPadding(expected.getX()) + dblWithPadding(expected.getY()) + dblWithPadding(expected.getZ()));
            System.out.println("    Actual: " + dblWithPadding(calculated.getX()) + dblWithPadding(calculated.getY()) + dblWithPadding(calculated.getZ()));
            System.out.println("      Diff: " + dblWithPadding(Math.abs(expected.getX() - calculated.getX())) + dblWithPadding(Math.abs(expected.getY() - calculated.getY())) + dblWithPadding(Math.abs(expected.getZ() - calculated.getZ())));
        }

        private String dblWithPadding(double val) {
            return withPadding(Double.toString(val));
        }

        private String withPadding(String text) {
            while (text.length() < 24) {
                text += " ";
            }
            return text;
        }
    }
}
