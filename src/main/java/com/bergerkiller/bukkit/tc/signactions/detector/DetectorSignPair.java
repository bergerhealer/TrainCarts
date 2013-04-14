package com.bergerkiller.bukkit.tc.signactions.detector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorListener;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;

public class DetectorSignPair implements DetectorListener {

	public DetectorSignPair(Block sign1, Block sign2) {
		this.sign1 = new DetectorSign(this, sign1);
		this.sign2 = new DetectorSign(this, sign2);
	}

	public DetectorSignPair(IntVector3 sign1, IntVector3 sign2) {
		this.sign1 = new DetectorSign(this, sign1);
		this.sign2 = new DetectorSign(this, sign2);
	}

	public DetectorSign sign1, sign2;
	public DetectorRegion region;

	public static DetectorSignPair read(DataInputStream stream) throws IOException {
		IntVector3 sign1 = IntVector3.read(stream);
		IntVector3 sign2 = IntVector3.read(stream);
		DetectorSignPair detector = new DetectorSignPair(sign1, sign2);
		detector.sign1.wasDown = stream.readBoolean();
		detector.sign2.wasDown = stream.readBoolean();
		return detector;
	}

	public void write(DataOutputStream stream) throws IOException {
		this.sign1.getLocation().write(stream);
		this.sign2.getLocation().write(stream);
		stream.writeBoolean(this.sign1.wasDown);
		stream.writeBoolean(this.sign2.wasDown);
	}

	@Override
	public void onRegister(DetectorRegion region) {
		this.region = region;
	}

	@Override
	public void onUnregister(DetectorRegion region) {
		if (this.region == region) this.region = null;
	}

	@Override
	public void onLeave(MinecartMember<?> member) {
		this.sign1.onLeave(member);
		this.sign2.onLeave(member);
	}

	@Override
	public void onEnter(MinecartMember<?> member) {
		this.sign1.onEnter(member);
		this.sign2.onEnter(member);
	}

	@Override
	public void onLeave(MinecartGroup group) {
		this.sign1.onLeave(group);
		this.sign2.onLeave(group);
	}

	@Override
	public void onEnter(MinecartGroup group) {
		this.sign1.onEnter(group);
		this.sign2.onEnter(group);
	}

	@Override
	public void onUpdate(MinecartMember<?> member) {
		this.sign1.onUpdate(member);
		this.sign2.onUpdate(member);
	}

	@Override
	public void onUpdate(MinecartGroup group) {
		this.sign1.onUpdate(group);
		this.sign2.onUpdate(group);
	}

	@Override
	public void onUnload(MinecartGroup group) {
		//TODO: Unloaded group storage system
		this.onLeave(group);
	}
}
