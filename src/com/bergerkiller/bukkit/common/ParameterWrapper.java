package com.bergerkiller.bukkit.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.server.MinecraftServer;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

@SuppressWarnings("unchecked")
public class ParameterWrapper {
	
	private Object[] arguments;
	public ParameterWrapper(Object... arguments) {
		this.arguments = arguments;
	}
	
	public Object set(int index, Object value) {
		if (index > this.arguments.length - 1) {
			//resize
			Object[] newargs = new Object[index + 1];
			System.arraycopy(this.arguments, 0, newargs, 0, this.arguments.length);
			this.arguments = newargs;
		}
		Object old = arguments[index];
		arguments[index] = value;
		return old;
	}
	public Object arg(int index) {
		return this.arguments[index];
	}
	public <T> T arg(int index, Class<T> type) {
		return type.cast(this.arguments[index]);
	}
	public <T> T[] argArray(int index, Class<T> type) {
		return (T[]) this.arguments[index];
	}
	public <T> List<T> argList(int index, Class<T> type) {
		return (List<T>) this.arguments[index];
	}
	public <T> Set<T> argSet(int index, Class<T> type) {
		return (Set<T>) this.arguments[index];
	}
	public <A, B> Map<A, B> argMap(int index, Class<A> keytype, Class<B> valuetype) {
		return (Map<A, B>) this.arguments[index];
	}
	public <T> Collection<T> argCollection(int index, Class<T> type) {
		return (Collection<T>) this.arguments[index];
	}
	
	public static CraftServer server() {
		return (CraftServer) Bukkit.getServer();
	}
	public static MinecraftServer mc() {
		return server().getServer();
	}
	
}