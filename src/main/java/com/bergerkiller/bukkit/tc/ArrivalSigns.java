package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.TimeDurationFormat;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.logging.Level;

public class ArrivalSigns {
    private static HashMap<String, TimeSign> timerSigns = new HashMap<>();
    private static BlockMap<TimeCalculation> timeCalculations = new BlockMap<>();
    private static TimeDurationFormat timeFormat = new TimeDurationFormat("HH:mm:ss");
    private static Task updateTask;

    public static TimeSign getTimer(String name) {
        return timerSigns.computeIfAbsent(name, TimeSign::new);
    }

    public static boolean isTrigger(Sign sign) {
        SignActionHeader header = SignActionHeader.parseFromSign(sign);
        return header.isValid() && Util.getCleanLine(sign, 1).equalsIgnoreCase("trigger");
    }

    public static void trigger(Sign sign, MinecartMember<?> mm) {
        if (!TCConfig.SignLinkEnabled) return;
        final String name = Util.getCleanLine(sign, 2);
        if (name.isEmpty()) return;
        if (mm != null) {
            Variables.get(name + 'N').set(mm.getGroup().getProperties().getDisplayName());
            if (mm.getProperties().hasDestination()) Variables.get(name + 'D').set(mm.getProperties().getDestination());
            else Variables.get(name + 'D').set("Unknown");

            double speed = MathUtil.round(mm.getRealSpeed(), 2);
            speed = Math.min(speed, mm.getGroup().getProperties().getSpeedLimit());
            Variables.get(name + 'V').set(Double.toString(speed));
        }
        final TimeSign t = getTimer(name);
        t.duration = ParseUtil.parseTime(Util.getCleanLine(sign, 3));
        if (t.duration == 0) {
            timeCalcStart(sign.getBlock(), mm);
        } else {
            t.trigger();
            t.update();
        }
    }

    public static void setTimeDurationFormat(String format) {
        try {
            timeFormat = new TimeDurationFormat(format);
        } catch (IllegalArgumentException ex) {
            TrainCarts.plugin.log(Level.WARNING, "Time duration format is invalid: " + format);
        }
    }

    public static void updateAll() {
        for (TimeSign t : timerSigns.values()) if (!t.update()) return;
    }

    public static void init(String filename) {
        final FileConfiguration config = new FileConfiguration(filename);
        config.load();
        for (String key : config.getKeys()) {
            final String dur = config.get(key, String.class, null);
            if (dur != null) {
                TimeSign t = getTimer(key);
                t.duration = ParseUtil.parseTime(dur);
                t.startTime = System.currentTimeMillis();
            }
        }
    }

    public static void save(String filename) {
        final FileConfiguration config = new FileConfiguration(filename);
        for (TimeSign sign : timerSigns.values()) config.set(sign.name, sign.getDuration());
        config.save();
    }

    public static void deinit() {
        timerSigns.clear();
        timerSigns = null;
        timeCalculations.clear();
        timeCalculations = null;
        if (updateTask != null && updateTask.isRunning()) updateTask.stop();
        updateTask = null;
    }

    public static void timeCalcStart(Block signblock, MinecartMember<?> member) {
        TimeCalculation calc = new TimeCalculation();
        calc.startTime = System.currentTimeMillis();
        calc.signblock = signblock;
        calc.member = member;
        for (Player player : calc.signblock.getWorld().getPlayers()) {
            if (player.hasPermission("train.build.trigger"))
                player.sendMessage(member == null
                        ? ChatColor.YELLOW + "[Train Carts] Remove the power source to stop recording"
                        : ChatColor.YELLOW + "[Train Carts] Stop or destroy the minecart to stop recording");
        }
        timeCalculations.put(calc.signblock, calc);
        if (updateTask == null) {
            updateTask = new Task(TrainCarts.plugin) {
                public void run() {
                    if (timeCalculations.isEmpty()) {
                        this.stop();
                        updateTask = null;
                    }
                    for (TimeCalculation calc : timeCalculations.values())
                        if (calc.member != null)
                            if (calc.member.isUnloaded() || calc.member.getEntity().getEntity().isDead() || !calc.member.getEntity().isMoving()) {
                                calc.setTime();
                                timeCalculations.remove(calc.signblock);
                                return;
                            }
                }
            }.start(0, 1);
        }
    }

    public static void timeCalcStop(Block signBlock) {
        TimeCalculation calc = timeCalculations.get(signBlock);
        if (calc != null && calc.member == null) {
            calc.setTime();
            timeCalculations.remove(signBlock);
        }
    }

    private static class TimeCalculation {
        public long startTime;
        public Block signblock;
        public MinecartMember<?> member = null;

        public void setTime() {
            long duration = System.currentTimeMillis() - startTime;
            if (MaterialUtil.ISSIGN.get(this.signblock)) {
                Sign sign = (Sign) this.signblock.getState(); //BlockUtil.getSign(this.signblock);
                String dur = timeFormat.format(duration);
                sign.setLine(3, dur);
                sign.update(true);
                //Message
                for (Player player : sign.getWorld().getPlayers())
                    if (player.hasPermission("train.build.trigger"))
                        player.sendMessage(ChatColor.YELLOW + "[Train Carts] Trigger time of '"
                                + sign.getLine(2) + "' set to " + dur);
            }
        }
    }

    public static class TimeSign {
        public long startTime = -1;
        public long duration;
        private String name;

        public TimeSign(String name) {
            this.name = name;
        }

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
            return timeFormat.format(remaining);
        }

        public boolean update() {
            if (!TCConfig.SignLinkEnabled) return false;
            // Calculate the time to display
            final String dur = getDuration();
            Variables.get(this.name).set(dur);
            Variables.get(this.name + 'T').set(dur);
            if (dur.equals("00:00:00")) {
                timerSigns.remove(this.name);
                return false;
            }
            return true;
        }
    }
}
