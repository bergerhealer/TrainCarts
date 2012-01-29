package com.bergerkiller.bukkit.common;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class Task extends ParameterWrapper implements Runnable {
	
	public Task(final JavaPlugin plugin, Object... arguments) {
		super(arguments);
		this.plugin = plugin;
	}
	public final JavaPlugin getPlugin() {
		return this.plugin;
	}
	
	private final JavaPlugin plugin;
	private int id = -1;
	
	public boolean isRunning() {
		return this.id != -1 && Bukkit.getServer().getScheduler().isCurrentlyRunning(this.id);
	}
    public boolean isQueued() {
        return Bukkit.getServer().getScheduler().isQueued(this.id);
    }

	public static boolean stop(Task task) {
		if (task == null) return false;
		task.stop();
		return true;
	}
	public Task stop() {
		if (this.id != -1) {
			Bukkit.getServer().getScheduler().cancelTask(this.id);
			this.id = -1;
		}
		return this;
	}
	public Task start() {
		this.id = this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, this);
		return this;
	}
	public Task start(long delay) {
		this.id = this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, this, delay);
		return this;
	}
	public Task start(long delay, long interval) {
		this.id = this.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(this.plugin, this, delay, interval);
		return this;
	}
	
}
