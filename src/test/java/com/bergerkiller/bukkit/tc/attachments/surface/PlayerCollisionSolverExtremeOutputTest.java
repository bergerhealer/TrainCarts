package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.generated.net.minecraft.world.phys.AABBHandle;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * There are a lot of cases where player-surface collision resulted in extremely large Y translations, which caused players to be teleported very high up in the air.
 * This test contains a number of cases that historically produced such extreme values, and ensures that the solver produces reasonable results for these cases.
 * All use a rotating 8x8x8 "yeetcube" that the player gets near of, to be pushed up/down it.
 */
public class PlayerCollisionSolverExtremeOutputTest {
    // Common box size used in all tests
    private static final Vector SURFACE_SIZE = new Vector(8.0, 0.0, 8.0);
    // Turn on to debug failing tests
    private static final boolean ENABLE_LOGGING = false;
    // Test-wide logger instance used to construct PlayerCollisionSolver instances in tests
    private static final PlayerCollisionLogger TEST_LOGGER = new PlayerCollisionLoggerImpl();
    // Single solver instance used by all tests to avoid repeated construction
    private static final PlayerCollisionSolver TEST_SOLVER = new PlayerCollisionSolver(ENABLE_LOGGING ? TEST_LOGGER :  PlayerCollisionLogger.DISABLED);

    private static OBBSurfaceTransition<String> makeTransition(
            Vector fromCenter, Vector toCenter,
            Vector fromNormal, Vector toNormal
    ) {
        Vector fromForward = Math.abs(fromNormal.getX()) < Math.abs(fromNormal.getZ()) ? new Vector(1,0,0) : new Vector(0,0,1);
        Vector toForward = Math.abs(toNormal.getX()) < Math.abs(toNormal.getZ()) ? new Vector(1,0,0) : new Vector(0,0,1);
        Quaternion fromOrient = Quaternion.fromLookDirection(fromForward, fromNormal);
        Quaternion toOrient = Quaternion.fromLookDirection(toForward, toNormal);
        OrientedBoundingBox fromBox = new OrientedBoundingBox(fromCenter, SURFACE_SIZE, fromOrient);
        OrientedBoundingBox toBox = new OrientedBoundingBox(toCenter, SURFACE_SIZE, toOrient);
        return new OBBSurfaceTransition<>(fromBox, toBox);
    }

    private static AABBHandle makeAABB(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        return AABBHandle.createNew(minX,minY,minZ,maxX,maxY,maxZ);
    }

    private static void runAndAssert(OBBSurfaceTransition<String> transition, AABBHandle playerFrom, AABBHandle playerTo, double expectedMinY, double allowedDelta) {
        runAndAssert(Collections.singletonList(transition), playerFrom, playerTo, expectedMinY, allowedDelta);
    }

    private static void runAndAssert(List<OBBSurfaceTransition<String>> transitions, AABBHandle playerFrom, AABBHandle playerTo, double expectedMinY, double allowedDelta) {
        PlayerBoundsTransition pt = new PlayerBoundsTransition(playerFrom, playerTo);
        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(transitions, pt);
        AABBHandle solved = result.bounds;
        System.out.println("Repro solved bounds: minY=" + solved.getMinY() + " maxY=" + solved.getMaxY());
        assertEquals("Solver produced an extreme minY (" + solved.getMinY() + ")",
                expectedMinY, solved.getMinY(), allowedDelta);
    }

    @Test
    public void testExtremeSample1() {
        OBBSurfaceTransition<String> transition = makeTransition(
                new Vector(-345.5010390433314, 17.060010828699962, 304.40886783736573),
                new Vector(-345.5022115882019, 17.064111855268017, 304.36701422476295),
                new Vector(-0.9997402391671499, 6.222928250094739E-4, 0.022783040658569675),
                new Vector(-0.9994471029495253, -4.0296381700377637E-4, 0.03324644380926495)
        );

        AABBHandle playerFrom = makeAABB(-345.1142276022316, 12.90760083295261, 306.46874996031676,
                -344.51422757838975, 14.707600785268895, 307.0687499841586);
        AABBHandle playerTo = makeAABB(-345.1378490925351, 12.90760083295261, 306.4583184825722,
                -344.5378490686932, 14.707600785268895, 307.0583185064141);

        // Ensure finite and not ridiculously large (sanity check)
        PlayerBoundsTransition pt = new PlayerBoundsTransition(playerFrom, playerTo);
        PlayerCollisionSolver.Result<String> result = TEST_SOLVER.solveDetailed(java.util.Collections.singletonList(transition), pt);
        AABBHandle solved = result.bounds;
        System.out.println("Solved bounds: minY=" + solved.getMinY() + " maxY=" + solved.getMaxY());
        assertTrue("minY is finite and within reasonable bounds", Double.isFinite(solved.getMinY()) && Math.abs(solved.getMinY()) < 1e6);
        assertTrue("maxY is finite and within reasonable bounds", Double.isFinite(solved.getMaxY()) && Math.abs(solved.getMaxY()) < 1e6);
    }

    @Test
    public void testExtremeSample2() {
        OBBSurfaceTransition<String> transition = makeTransition(
                new Vector(-345.56525537181307, 17.084512838268083, 305.21923580688497),
                new Vector(-345.5729983589632, 17.055782635270067, 305.26066614773197),
                new Vector(-0.9836861570467319, -0.0055032095670204395, -0.17980895172124706),
                new Vector(-0.9817504102592001, 0.0016793411824831406, -0.1901665369329884)
        );

        AABBHandle playerFrom = makeAABB(-340.28658642458964, 13.74360538238074, 308.23556503825984,
                -339.6865864007478, 15.543605334697023, 308.8355650621017);
        AABBHandle playerTo = makeAABB(-340.6698192351082, 13.74360538238074, 308.1044362461995,
                -340.06981921126635, 15.543605334697023, 308.70443627004136);

        runAndAssert(transition, playerFrom, playerTo, 13.7, 1.0);
    }

    @Test
    public void testExtremeSample3() {
        OBBSurfaceTransition<String> transition = makeTransition(
                new Vector(-346.29946803333854,17.050651788593967,306.89926129262005),
                new Vector(-346.27454344103336,16.957580618297445,306.8633497222295),
                new Vector(-0.8001329916653598,0.002962052851507968,-0.5998153231550074),
                new Vector(-0.8063641397416572,0.026229845425638443,-0.5908374305573778)
        );

        AABBHandle playerFrom = makeAABB(-345.1299673347247, 17.102795023375034, 306.6126631246129,
                -344.52996731088285, 18.902794975691318, 307.21266314845474);
        AABBHandle playerTo = makeAABB(-345.1299673347247, 17.102795023375034, 306.6126631246129,
                -344.52996731088285, 18.902794975691318, 307.21266314845474);

        // Sanity check: solver should not produce extremely large Y translations for this case
        runAndAssert(transition, playerFrom, playerTo, 17.1, 1.0);
    }

    @Test
    public void testExtremeSample4() {
        OBBSurfaceTransition<String> transition = makeTransition(
                new Vector(-345.6521672694735,17.135043145852865,305.59037643494287),
                new Vector(-345.64094584101844,17.09091910372645,305.55208999262925),
                new Vector(-0.9619581826316241,-0.01813578646321634,-0.2725941087357228),
                new Vector(-0.9647635397453889,-0.007104775931612295,-0.2630224981573054)
        );

        AABBHandle playerFrom = makeAABB(-344.2673439715234, 17.080339005571616, 306.17542817026595,
                -343.66734394768156, 18.8803389578879, 306.7754281941078);
        AABBHandle playerTo = makeAABB(-344.2673439715234, 17.080339005571616, 306.17542817026595,
                -343.66734394768156, 18.8803389578879, 306.7754281941078);

        // Allow larger threshold because this case historically produced very large values
        runAndAssert(transition, playerFrom, playerTo, 17.08, 1.0);
    }

    @Test
    public void testExtremeSample5() {
       OBBSurfaceTransition<String> transition = makeTransition(
                new Vector(-345.50898982375236, 17.18138428343457, 304.7402171938888),
                new Vector(-345.5120121165452, 17.18884695958023, 304.7828234205691),
                new Vector(-0.9977525440619174, -0.02972107085864284, -0.06005429847219368),
                new Vector(-0.996996970863703, -0.031586739895057114, -0.07070585514228517)
        );
        AABBHandle playerFrom = makeAABB(-339.02404610443693, 15.152128887983055, 308.83501315128234, -338.4240460805951, 16.952128840299338, 309.4350131751242);
        AABBHandle playerTo   = makeAABB(-339.4626853546168, 15.152128887983055, 308.74459191829305, -338.8626853307749, 16.952128840299338, 309.3445919421349);

        runAndAssert(transition, playerFrom, playerTo, 15.15, 1.0);
    }

    @Test
    public void testExtremeSample6() {
        List<OBBSurfaceTransition<String>> transitions = Arrays.asList(
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-348.8984839168028, 13.109267286122877, 304.60064547474593),
                                new Quaternion(9.596255044222594E-4, 0.012690191352381149, 0.9970722984586079, -0.07539807563061414),
                                new Vector(8.0, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-348.85714742819994, 13.114880800805402, 304.44483453000026),
                                new Quaternion(-5.632583127584843E-4, -0.0069639290172577775, 0.996720664298377, -0.08061701925349239),
                                new Vector(8.0, 0.0, 8.0)
                        )
                ),
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-349.5, 17.16430313740766, 308.49870430529865),
                                new Quaternion(-0.46670239433797633, -0.5294102786162094, 0.5430600954730127, 0.4549718284900175),
                                new Vector(8.0, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-349.5, 17.00660799870038, 308.49960949146174),
                                new Quaternion(0.45485148717019264, 0.5424324354409429, -0.5349052481109265, -0.4612521578746918),
                                new Vector(8.0, 0.0, 8.0)
                        )
                )
        );
        AABBHandle playerFrom = makeAABB(-346.31995312705936, 11.782268707771879, 307.7687637137779, -345.7199531032175, 13.582268660088163, 308.36876373761976);
        AABBHandle playerTo   = makeAABB(-346.31995312705936, 11.782268707771879, 307.7687637137779, -345.7199531032175, 13.582268660088163, 308.36876373761976);

        runAndAssert(transitions, playerFrom, playerTo, 11.7, 1.0);
    }

    @Test
    public void testExtremeSample7() {
        OBBSurfaceTransition<String> transition = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-345.5783442180496, 17.0350490602156, 305.2873133898265),
                        new Quaternion(-0.4558055337061519, 0.5564746424095952, -0.5374104092495788, -0.4401901178220195),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-345.58680015151464, 17.000995675636325, 305.32648905854745),
                        new Quaternion(-0.46142212481322054, 0.5693814303844466, -0.5285845080849356, -0.4283606276011147),
                        new Vector(8.0, 0.0, 8.0)
                )
        );
        AABBHandle playerFrom = makeAABB(-346.5070732716714, 12.47675566162468, 307.69288332333537, -345.90707324782954, 14.276755613940963, 308.2928833471772);
        AABBHandle playerTo   = makeAABB(-346.5070732716714, 12.47675566162468, 307.69288332333537, -345.90707324782954, 14.276755613940963, 308.2928833471772);

        runAndAssert(transition, playerFrom, playerTo, 12.5, 1.0);
    }

    @Test
    public void testExtremeSample8() {
        OBBSurfaceTransition<String> transition = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.2516447879356, 16.580855835514686, 308.4631223280966),
                        new Quaternion(-0.6627623593964918, 0.020594928417997437, 0.023249395831545224, 0.7481853844363183),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.20989386234305, 16.425466394525635, 308.43827711244273),
                        new Quaternion(-0.6477710172865009, 0.02352126531259406, 0.02763165719453882, 0.7609704007137135),
                        new Vector(8.0, 0.0, 8.0)
                )
        );
        AABBHandle playerFrom = makeAABB(-350.1753025340326, 14.314739731984336, 304.61394231649456, -349.57530251019074, 16.11473968430062, 305.2139423403364);
        AABBHandle playerTo   = makeAABB(-350.1753025340326, 14.4647397379448, 304.61394231649456, -349.57530251019074, 16.264739690261084, 305.2139423403364);

        runAndAssert(transition, playerFrom, playerTo, 14.0, 1.0);
    }

    @Test
    public void testExtremeSample9() {
        List<OBBSurfaceTransition<String>> transitions = Arrays.asList(
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-351.0102981097617, 16.986731658219565, 308.2031417442537),
                                new Quaternion(0.7009596206947818, -0.13741814954798479, 0.13463525867313464, 0.6867643041482672),
                                new Vector(8.0, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-351.04896329654883, 16.841782365634856, 308.18130363211526),
                                new Quaternion(0.7136187559052992, -0.14378012052065242, 0.13541778138714317, 0.6721142556496056),
                                new Vector(8.0, 0.0, 8.0)
                        )
                ),
                new OBBSurfaceTransition<>(
                        new OBBSurfaceState(
                                new Vector(-347.9897018902383, 17.138268341780435, 300.7968582557463),
                                new Quaternion(0.7009596206947818, -0.13741814954798479, 0.13463525867313464, 0.6867643041482672),
                                new Vector(8.0, 0.0, 8.0)
                        ),
                        new OBBSurfaceState(
                                new Vector(-347.95103670345117, 17.283217634365144, 300.81869636788474),
                                new Quaternion(0.7136187559052992, -0.14378012052065242, 0.13541778138714317, 0.6721142556496056),
                                new Vector(8.0, 0.0, 8.0)
                        )
                )
        );
        AABBHandle playerFrom = makeAABB(-355.48461994987053, 17.300936451230044, 306.66089419754155, -354.8846199260287, 19.100936403546328, 307.2608942213834);
        AABBHandle playerTo   = makeAABB(-355.51393358908814, 17.450936457190508, 306.621629573802, -354.9139335652463, 19.250936409506792, 307.22162959764387);

        runAndAssert(transitions, playerFrom, playerTo, 17.8, 1.0);
    }

    @Test
    public void testExtremeSample10() {
        OBBSurfaceTransition<String> transition = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-349.87999387770975, 14.15363481081982, 300.4510361863708),
                        new Quaternion(0.04449824715478048, 0.7115285260692048, -0.6997856503133573, -0.04524495779032666),
                        new Vector(4.0, 0.0, 2.0)
                ),
                new OBBSurfaceState(
                        new Vector(-349.848841924715, 14.311686102618715, 300.33931108907007),
                        new Quaternion(0.04001081414070242, 0.7254158849095786, -0.6858425422094209, -0.04231945141858487),
                        new Vector(4.0, 0.0, 2.0)
                )
        );

        AABBHandle playerFrom = makeAABB(-351.5723884637438, 14.31310116721022, 300.97113917426714, -350.9723884399019, 16.113101119526505, 301.571139198109);
        AABBHandle playerTo   = makeAABB(-351.6096431524634, 14.309811807105113, 301.0537361581235, -351.0096431286215, 16.109811759421397, 301.65373618196537);

        runAndAssert(transition, playerFrom, playerTo, 14.4, 1.0);
    }

    @Test
    public void testExtremeSample11() {
        OBBSurfaceTransition<String> transition = new OBBSurfaceTransition<>(
                new OBBSurfaceState(
                        new Vector(-353.28082620600964, 17.735132020126507, 303.3806611927341),
                        new Quaternion(-0.5050851266062037, 0.708820466254028, -0.4010112372058693, -0.28574910172253504),
                        new Vector(8.0, 0.0, 8.0)
                ),
                new OBBSurfaceState(
                        new Vector(-353.2669579735926, 17.800383752084752, 303.3750310254959),
                        new Quaternion(-0.5068644745416779, 0.7192486694454414, -0.3883979086413763, -0.27370937234876735),
                        new Vector(8.0, 0.0, 8.0)
                )
        );
        AABBHandle playerFrom = makeAABB(-351.0833305211111, 21.732875392327262, 303.49582366757164, -350.48333049726926, 23.532875344643546, 304.0958236914135);
        AABBHandle playerTo   = makeAABB(-351.0833305211111, 21.732875392327262, 303.49582366757164, -350.48333049726926, 23.532875344643546, 304.0958236914135);

        runAndAssert(transition, playerFrom, playerTo, 21.7, 1.0);
    }
}
