package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.material.Directional;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

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
	
	public static void trigger(Sign sign, BlockFace trainDirection) {
		if (isTrigger(sign)) {
			if (trainDirection == BlockFace.SELF) {
				trigger(sign.getLine(2), sign.getLine(3));
			} else {
				BlockFace d = ((Directional) sign.getData()).getFacing();
				if (trainDirection != d) {
					//Allowed
					trigger(sign.getLine(2), sign.getLine(3));
				}
			}
		}
	}
	public static void trigger(Sign sign) {
		trigger(sign, BlockFace.SELF);
	}
	public static void trigger(String name, String duration) {
		if (name == null || name.equals("")) return;
		TimeSign t = getTimer(name);
		if (duration != null && !duration.equals("")) {
			String[] parts = duration.split(":");
			try {
				if (parts.length == 1) {
					//Seconds display only
					t.duration = Long.parseLong(parts[0]) * 1000;
				} else if (parts.length == 2) {
					//Min:Sec
					t.duration = Long.parseLong(parts[0]) * 60000;
					t.duration += Long.parseLong(parts[1]) * 1000;
				} else if (parts.length == 3) {
					//Hour:Min:Sec (ow come on -,-)
					t.duration = Long.parseLong(parts[0]) * 3600000;
					t.duration += Long.parseLong(parts[1]) * 60000;
					t.duration += Long.parseLong(parts[2]) * 1000;
				}
			} catch (Exception ex) {
				//D'aw, failed. Need anything? Neh...
			}
		}
		t.trigger();
		t.update();
	}
		
	public static void updateAll() {
		for (TimeSign t : timerSigns.values()) {
			t.update();
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
				
		public void update() {
			if (!TrainCarts.SignLinkEnabled) return;
			//Calculate the time to display
			long elapsed = System.currentTimeMillis() - this.startTime;
			long remaining = (duration - elapsed) / 1000;
			if (remaining < 0) remaining = 0;
			if (remaining == 0) {
				com.bergerkiller.bukkit.sl.API.Variables.set(this.name, ChatColor.WHITE + "00:00:00").update();
			} else {
				//Convert to a String
				String seconds = Integer.toString((int)(remaining % 60));  
				String minutes = Integer.toString((int)((remaining % 3600) / 60));  
				String hours = Integer.toString((int)(remaining / 3600)); 
				if (seconds.length() == 1) seconds = "0" + seconds;
				if (minutes.length() == 1) minutes = "0" + minutes;
				if (hours.length() == 1) hours = "0" + hours;
				String finalTime = ChatColor.WHITE + hours + ":" + minutes + ":" + seconds;
				com.bergerkiller.bukkit.sl.API.Variables.set(this.name, finalTime).update();
			}
		}
	}

	public static String getFile(World w) {
		return TrainCarts.plugin.getDataFolder() + File.separator + "ArrivalSigns" + File.separator + w.getName() + ".txt";
	}

}
