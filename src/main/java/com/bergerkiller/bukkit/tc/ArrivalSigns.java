package com.bergerkiller.bukkit.tc;

import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;

public class ArrivalSigns {
	private static HashMap<String, TimeSign> timerSigns = new HashMap<String, TimeSign>();
	private static BlockMap<TimeCalculation> timeCalculations = new BlockMap<TimeCalculation>();
	private static Task updateTask;

	private static class TimeCalculation {
		public long startTime;
		public Block signblock;
		public MinecartMember<?> member = null;
		public void setTime() {
			long duration = System.currentTimeMillis() - startTime;
			if (MaterialUtil.ISSIGN.get(this.signblock)) {
				Sign sign = BlockUtil.getSign(this.signblock);
				String dur = Util.getTimeString(duration);
				sign.setLine(3, dur);
				sign.update(true);
				//Message
				for (Player player : sign.getWorld().getPlayers()) {
					if (player.hasPermission("train.build.trigger")) {
						player.sendMessage(ChatColor.YELLOW + "[Train Carts] Trigger time of '" + sign.getLine(2) + "' set to " + dur);
					}
				}
			}
		}
	}

	public static class TimeSign {
		private String name;
		public long startTime = -1;
		public long duration;

		public void trigger() {
			this.startTime = System.currentTimeMillis();
		}
		
		public String getName() {
			return this.name;
		}
				
		public String getDuration() {
			long elapsed = System.currentTimeMillis() - this.startTime;
			long remaining = duration - elapsed;
			if (remaining < 0) remaining = 0;
			return Util.getTimeString(remaining);
		}
		
		public boolean update() {
			if (!TrainCarts.SignLinkEnabled) return false;
			//Calculate the time to display
			String dur = getDuration();
			Variables.get(this.name).set(dur);
			Variables.get(this.name + 'T').set(dur);
			if (dur.equals("00:00:00")) {
				timerSigns.remove(this.name);
				return false;
			} else {
				return true;
			}
		}
	}

	public static TimeSign getTimer(String name) {
		TimeSign t = timerSigns.get(name);
		if (t == null) {
			t = new TimeSign();
			t.name = name;
			timerSigns.put(name, t);
		}
		return t;
	}

	public static boolean isTrigger(Sign sign) {
		if (sign != null) {
			if (sign.getLine(0).equalsIgnoreCase("[train]")) {
				if (sign.getLine(1).equalsIgnoreCase("trigger")) {
					return true;
				}
			}
		}
		return false;
	}

	public static void trigger(Sign sign, MinecartMember<?> mm) {
		if (!TrainCarts.SignLinkEnabled) return;
		String name = sign.getLine(2);
		String duration = sign.getLine(3);
		if (name == null || name.equals("")) return;
		if (mm != null) {
			Variables.get(name + 'N').set(mm.getGroup().getProperties().getDisplayName());
			if (mm.getProperties().hasDestination()) {
				Variables.get(name + 'D').set(mm.getProperties().getDestination());
			} else {
				Variables.get(name + 'D').set("Unknown");
			}
			Variables.get(name + 'V').set(Double.toString(MathUtil.round(mm.getForce(), 2)));
		}
		TimeSign t = getTimer(name);
		t.duration = ParseUtil.parseTime(duration);
		if (t.duration == 0) {
			timeCalcStart(sign.getBlock(), mm);
		} else {
			t.trigger();
			t.update();
		}
	}

	public static void updateAll() {
		for (TimeSign t : timerSigns.values()) {
			if (!t.update()) {
				return;
			}
		}
	}

	public static void init(String filename) {
		FileConfiguration config = new FileConfiguration(filename);
		config.load();
		for (String key : config.getKeys()) {
			String dur = config.get(key, String.class, null);
			if (dur != null) {
				TimeSign t = getTimer(key);
				t.duration = ParseUtil.parseTime(dur);
				t.startTime = System.currentTimeMillis();
			}
		}
	}

	public static void save(String filename) {
		FileConfiguration config = new FileConfiguration(filename);
		for (TimeSign sign : timerSigns.values()) {
			config.set(sign.name, sign.getDuration());
		}
		config.save();
	}
	public static void deinit() {
		timerSigns.clear();
		timerSigns = null;
		timeCalculations.clear();
		timeCalculations = null;
		if (updateTask != null && updateTask.isRunning()) {
			updateTask.stop();
		}
		updateTask = null;
	}

	public static void timeCalcStart(Block signblock, MinecartMember<?> member) {
		TimeCalculation calc = new TimeCalculation();
		calc.startTime = System.currentTimeMillis();
		calc.signblock = signblock;
		calc.member = member;
		for (Player player : calc.signblock.getWorld().getPlayers()) {
			if (player.hasPermission("train.build.trigger")) {
				if (member == null) {
					player.sendMessage(ChatColor.YELLOW + "[Train Carts] Remove the power source to stop recording");
				} else {
					player.sendMessage(ChatColor.YELLOW + "[Train Carts] Stop or destroy the minecart to stop recording");
				}
			}
		}
		timeCalculations.put(calc.signblock, calc);
		if (updateTask == null) {
			updateTask = new Task(TrainCarts.plugin) {
				public void run() {
					if (timeCalculations.isEmpty()) {
						this.stop();
						updateTask = null;
					}
					for (TimeCalculation calc : timeCalculations.values()) {
						if (calc.member != null) {
							if (calc.member.getEntity().isDead() || !calc.member.getEntity().isMoving()) {
								calc.setTime();
								timeCalculations.remove(calc.signblock);
								return;
							}
						}
					}
				}
			}.start(0, 1);
		}
	}

	public static void timeCalcStop(Block signblock) {
		TimeCalculation calc = timeCalculations.get(signblock);
		if (calc != null && calc.member == null) {
			calc.setTime();
			timeCalculations.remove(signblock);
		}
	}
}
