package com.bergerkiller.bukkit.tc.utils;

public class SoftReference<T> {

	private java.lang.ref.SoftReference<T> ref = null;
	
	/**
	 * Gets the value from this reference
	 * @return the value
	 */
	public T get() {
		return this.ref == null ? null : this.ref.get();
	}

	/**
	 * Sets the reference to the given value
	 * @param value
	 * @return the value
	 */
	public T set(T value) {
		this.ref = value == null ? null : new java.lang.ref.SoftReference<T>(value);
		return value;
	}
}
