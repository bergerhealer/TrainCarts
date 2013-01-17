package com.bergerkiller.bukkit.tc.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Keeps track of the current sound to
 */
public class PoweredCartSoundLoop extends SoundLoop {
	private static Map<Double, Integer> nodes = new LinkedHashMap<Double, Integer>();
	private int swooshSoundCounter = 0;

	static {
		nodes.put(0.0, Integer.MAX_VALUE);
		nodes.put(0.001, 50);
		nodes.put(0.005, 23);
		nodes.put(0.01, 18);
		nodes.put(0.05, 16);
		nodes.put(0.1, 14);
		nodes.put(0.2, 8);
		nodes.put(0.4, 5);
	}

	public PoweredCartSoundLoop(MinecartMember member) {
		super(member);
	}

	public int getInterval() {
		double speed = member.getMovedDistance();
		// Get the new view distance
		double minSpeed = 0.0, maxSpeed = Double.MAX_VALUE;
		int minInterval = 0, maxInterval = Integer.MAX_VALUE;

		// Get min and max chunks and view around current chunks value
		for (Map.Entry<Double, Integer> entry : nodes.entrySet()) {
			if (entry.getKey() <= speed) {
				if (entry.getKey() >= minSpeed) {
					minSpeed = entry.getKey();
					minInterval = entry.getValue();
				}
			}
			if (entry.getKey() >= speed) {
				if (entry.getKey() <= maxSpeed) {
					maxSpeed = entry.getKey();
					maxInterval = entry.getValue();
				}
			}
		}
		if (minInterval == 0) {
			minInterval = maxInterval;
		}
		if (maxInterval == Integer.MAX_VALUE) {
			maxInterval = minInterval;
		}
		if (minSpeed == maxSpeed) {
			return minInterval;
		} else {
			double value = (double) (speed - minSpeed) / (double) (maxSpeed - minSpeed);
			return (int) (value * (double) maxInterval + (1.00 - value) * (double) minInterval);
		}
	}
	
	@Override
	public void onTick() {
		if (!member.hasFuel()) {
			return;
		}
		this.swooshSoundCounter++;
		int interval = getInterval();
		if (this.swooshSoundCounter >= interval) {
			this.swooshSoundCounter = 0;
			play("step.cloth", 0.4f + 0.2f * random.nextFloat(), 0.8f);
			play("random.fizz", 1.5f + 0.3f * random.nextFloat(), 0.05f + 0.1f * random.nextFloat());
		}
	}
}
