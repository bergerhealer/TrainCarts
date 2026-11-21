package com.bergerkiller.bukkit.tc.attachments.surface;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimulatedPlayerFallDistanceTest {
    @Test
    public void accumulatesOnlyAirborneDescentAndResetsOnSupportOrWater() {
        SimulatedPlayer player = new SimulatedPlayer(null, null, new Vector(), new Vector(),
                new Vector(), CollisionSurface.DISABLED, true);
        player.updateFallDistance(10.0, 9.0, false);
        player.updateFallDistance(9.0, 9.5, false);
        player.updateFallDistance(9.5, 7.5, false);
        assertEquals(3.0f, player.fallDistance, 0.0001f);

        player.updateFallDistance(7.5, 7.0, true);
        assertEquals(0.0f, player.fallDistance, 0.0001f);
        player.updateFallDistance(7.0, 5.0, false);
        player.flying = false;
        player.updateFallDistance(5.0, 4.5, false);
        assertEquals(0.0f, player.fallDistance, 0.0001f);
    }
}
