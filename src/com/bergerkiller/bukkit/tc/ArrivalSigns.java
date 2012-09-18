package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class ArrivalSigns {
	private static HashMap<String, TimeSign> timerSigns = new HashMap<String, TimeSign>();
	
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
	
	public static void trigger(Sign sign, MinecartMember mm) {
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
		}
		TimeSign t = getTimer(name);
		t.duration = getTime(duration);
		if (t.duration == 0) {
			timeCalcStart(sign.getBlock().getLocation(), mm);
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
	
	public static String getTimeString(long time) {
		if (time == 0) return "00:00:00";
		time /= 1000; // msec -> sec
		String seconds = Integer.toString((int)(time % 60));
		String minutes = Integer.toString((int)((time % 3600) / 60));
		String hours = Integer.toString((int)(time / 3600)); 
		if (seconds.length() == 1) seconds = "0" + seconds;
		if (minutes.length() == 1) minutes = "0" + minutes;
		if (hours.length() == 1) hours = "0" + hours;
		return hours + ":" + minutes + ":" + seconds;
	}
	public static long getTime(String timestring) {
		long rval = 0;
		try {
			if (timestring != null && !timestring.equals("")) {
				String[] parts = timestring.split(":");
				if (parts.length == 1) {
					//Seconds display only
					rval = Long.parseLong(parts[0]) * 1000;
				} else if (parts.length == 2) {
					//Min:Sec
					rval = Long.parseLong(parts[0]) * 60000;
					rval += Long.parseLong(parts[1]) * 1000;
				} else if (parts.length == 3) {
					//Hour:Min:Sec (ow come on -,-)
					rval = Long.parseLong(parts[0]) * 3600000;
					rval += Long.parseLong(parts[1]) * 60000;
					rval += Long.parseLong(parts[2]) * 1000;
				} else {
					return 0;
				}
			} else {
				return 0;
			}
		} catch (Exception ex) {
			//D'aw, failed. Start a timer for auto-update here...
			return 0;
		}
		return rval;
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
			return getTimeString(remaining);
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

	public static String getFile(World w) {
		return TrainCarts.plugin.getDataFolder() + File.separator + "ArrivalSigns" + File.separator + w.getName() + ".txt";
	}
	
	public static void init(String filename) {
		FileConfiguration config = new FileConfiguration(filename);
		config.load();
		for (String key : config.getKeys()) {
			String dur = config.get(key, String.class, null);
			if (dur != null) {
				TimeSign t = getTimer(key);
				t.duration = getTime(dur);
				t.startTime = System.currentTimeMillis();
			}
		}
	}
	public static void deinit(String filename) {
		FileConfiguration config = new FileConfiguration(filename);
		for (TimeSign sign : timerSigns.values()) {
			config.set(sign.name, sign.getDuration());
		}
		config.save();
		timerSigns.clear();
		timerSigns = null;
		timeCalcStart.clear();
		timeCalcStart = null;
		if (updateThread != null && updateThread.isRunning()) {
			updateThread.stop();
		}
		updateThread = null;
	}
	
	private static HashMap<Location, TimeCalculation> timeCalcStart = new HashMap<Location, TimeCalculation>();
	private static Task updateThread;
	public static void timeCalcStart(Location signblock, MinecartMember member) {
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
		timeCalcStart(calc);
	}
	private static void timeCalcStart(TimeCalculation calc) {
		timeCalcStart.put(calc.signblock, calc);
		if (updateThread == null) {
			updateThread = new Task(TrainCarts.plugin) {
				public void run() {
					if (timeCalcStart.size() == 0) {
						this.stop();
					}
					for (TimeCalculation calc : timeCalcStart.values()) {
						if (calc.member != null) {
							if (calc.member.dead || !calc.member.isMoving()) {
								calc.setTime();
								timeCalcStart.remove(calc.signblock);
								return;
							}
						}
					}
				}
			};
		}
		if (!updateThread.isRunning()) {
			updateThread.start(0, 1);
		}
	}
	
	private static class TimeCalculation {
		public long startTime;
		public Location signblock;
		public MinecartMember member = null;
		public void setTime() {
			long duration = System.currentTimeMillis() - startTime;
			Block block = signblock.getBlock();
			if (BlockUtil.isSign(block)) {
				Sign sign = BlockUtil.getSign(block);
				String dur = getTimeString(duration);
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
	
	
	public static void timeCalcStop(Location signblock) {
		TimeCalculation calc = timeCalcStart.get(signblock);
		if (calc != null && calc.member == null) {
			calc.setTime();
			timeCalcStart.remove(signblock);
		}
	}
}
