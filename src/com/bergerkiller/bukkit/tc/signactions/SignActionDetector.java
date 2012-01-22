package com.bergerkiller.bukkit.tc.signactions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.utils.BlockMap;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.detector.DetectorListener;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;

public class SignActionDetector extends SignAction {
	public static void removeDetector(Block at) {
		Detector dec = detectors.get(at);
		if (dec != null) {
			detectors.remove(at.getWorld(), dec.sign1);
			detectors.remove(dec.getSign2(at.getWorld()));
			dec.region.remove();
		}
	}
	private static BlockMap<Detector> detectors = new BlockMap<Detector>();
	private static class Detector implements DetectorListener {
		private DetectorRegion region;
			
		public Detector(Block sign1, Block sign2) {
			this.sign1 = BlockUtil.getCoordinates(sign1);
			this.sign2 = BlockUtil.getCoordinates(sign2);
		}
		public Detector() {};
		
		private boolean sign1down = false;
		private boolean sign2down = false;
		public ChunkCoordinates sign1;
		public ChunkCoordinates sign2;
		public Block getSign1(World world) {
			Block b = BlockUtil.getBlock(world, this.sign1);
			if (BlockUtil.isSign(b)) return b;
			return null;
		}
		public Block getSign2(World world) {
			Block b = BlockUtil.getBlock(world, this.sign2);
			if (BlockUtil.isSign(b)) return b;
			return null;
		}
				
		public boolean updateMembers(final Block signblock, final String line1, final String line2) {
			for (MinecartMember mm : this.region.getMembers()) {
				if (isDown(line1, line2, mm)) {
					BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(signblock), true);
					return true;
				}
			}
			BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(signblock), false);
			return false;
		}
		public boolean updateGroups(final Block signblock, final String line1, final String line2) {
			for (MinecartGroup g : this.region.getGroups()) {
				if (isDown(line1, line2, g)) {
					BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(signblock), true);
					return true;
				}
			}
			BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(signblock), false);
			return false;
		}
		
		public boolean validate(Sign sign) {
			if (isValid(sign)) {
				return true;
			} else {
				if (this.region != null) {
					this.region.unregister(this);
					if (!this.region.isRegistered()) this.region.remove();
				}
				return false;
			}
		}
						
		@Override
		public void onLeave(MinecartGroup group) {
			Block sign1, sign2;
			if (this.sign1down && (sign1 = getSign1(group.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign1);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.TRAIN) {
					if (isDown(sign.getLine(2), sign.getLine(3), group)) {
						this.sign1down = updateGroups(sign1, sign.getLine(2), sign.getLine(3));
					}
				}
			}
			if (this.sign2down && (sign2 = getSign2(group.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign2);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.TRAIN) {
					if (isDown(sign.getLine(2), sign.getLine(3), group)) {
						this.sign2down = updateGroups(sign2, sign.getLine(2), sign.getLine(3));
					}
				}
			}
		}
		@Override
		public void onEnter(MinecartGroup group) {
			Block sign1, sign2;
			if (!this.sign1down && (sign1 = getSign1(group.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign1);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.TRAIN) {
					if (isDown(sign.getLine(2), sign.getLine(3), group)) {
						this.sign1down = true;
						BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(sign1), true);
					}
				}
			}
			if (!this.sign2down && (sign2 = getSign2(group.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign2);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.TRAIN) {
					if (isDown(sign.getLine(2), sign.getLine(3), group)) {
						this.sign2down = true;
						BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(sign2), true);
					}
				}
			}
		}
		
		@Override
		public void onLeave(MinecartMember member) {
			Block sign1, sign2;
			if (this.sign1down && (sign1 = getSign1(member.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign1);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.CART) {
					if (isDown(sign.getLine(2), sign.getLine(3), member)) {
						this.sign1down = updateMembers(sign1, sign.getLine(2), sign.getLine(3));
					}
				}
			}
			if (this.sign2down && (sign2 = getSign2(member.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign2);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.CART) {
					if (isDown(sign.getLine(2), sign.getLine(3), member)) {
						this.sign2down = updateMembers(sign2, sign.getLine(2), sign.getLine(3));
					}
				}
			}
		}
		@Override
		public void onEnter(MinecartMember member) {
			Block sign1, sign2;
			if (!this.sign1down && (sign1 = getSign1(member.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign1);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.CART) {
					if (isDown(sign.getLine(2), sign.getLine(3), member)) {
						this.sign1down = true;
						BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(sign1), true);
					}
				}
			}
			if (!this.sign2down && (sign2 = getSign2(member.getWorld())) != null) {
				Sign sign = BlockUtil.getSign(sign2);
				if (!validate(sign)) return;
				if (SignActionMode.fromSign(sign) == SignActionMode.CART) {
					if (isDown(sign.getLine(2), sign.getLine(3), member)) {
						this.sign2down = true;
						BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(sign2), true);
					}
				}
			}
		}
		
		public void onRegister(DetectorRegion region) {
			this.region = region;
		}
		public void onUnregister(DetectorRegion region) {
			if (this.region == region) this.region = null;
		}
		
		public static boolean isDown(final String line1, final String line2, final MinecartMember member) {
			if (line1.isEmpty()) {
				if (line2.isEmpty()) {
					return true;
				} else {
					return member.hasTag(line2);
				}
			} else if (line2.isEmpty()) {
				return member.hasTag(line1);
			} else {
				return member.hasTag(line1) || member.hasTag(line2);
			}
		}
		public static boolean isDown(final String line1, final String line2, final MinecartGroup group) {
			if (line1.isEmpty()) {
				if (line2.isEmpty()) {
					return true;
				} else {
					return group.hasTag(line2);
				}
			} else if (line2.isEmpty()) {
				return group.hasTag(line1);
			} else {
				return group.hasTag(line1) || group.hasTag(line2);
			}
		}
	}
	
	public static boolean isValid(Sign sign) {
		if (sign == null) return false;
		return isValid(sign.getLines());
	}
	public static boolean isValid(String[] lines) {
		return SignActionMode.fromString(lines[0]) != SignActionMode.NONE &&
				lines[1].toLowerCase().startsWith("detector");
	}
	
	@Override
	public void execute(SignActionEvent info) {
		//nothing happens here, relies on rail detector events
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (!isValid(event.getLines())) {
			return;
		}
		if (handleBuild(event, Permission.BUILD_DETECTOR, "train detector", "detects the presence of trains between this detector sign and another")) {
			//try to create the other sign
			Block startsign = event.getBlock();
			Block startrails = BlockUtil.getRailsBlockFromSign(startsign);
			if (startrails == null) {
				event.getPlayer().sendMessage(ChatColor.RED + "Note that no rails are nearby, so it is not yet activated.");
				return;
			}
			TrackMap map = new TrackMap(startrails, BlockUtil.getFacing(startsign));
			//now try to find the end rails : find the other sign
			boolean found = false;
			int i = TrainCarts.maxDetectorLength;
			Block next;
			Block endsign = null;
			Sign sign;
			while (!found) {
				next = map.next();
				if (--i < 0 || next == null) {
					//no second sign found
					event.getPlayer().sendMessage(ChatColor.RED + "Could not find a second connected detector sign, place one to activate this detector.");
					return;
				}
				for (Block signblock : BlockUtil.getSignsAttached(next)) {
					sign = BlockUtil.getSign(signblock);
					if (SignActionMode.fromSign(sign) != SignActionMode.NONE) {
						if (sign.getLine(1).toLowerCase().startsWith("detector")) {
							endsign = signblock;
							found = true;
							break;
						}
					}
				}
			}
			//start and end found : add it
			event.getPlayer().sendMessage(ChatColor.GREEN + "A second connected detector sign was found, detector region set.");
			Detector detector = new Detector(startsign, endsign);
			detectors.put(startsign, detector);
			detectors.put(endsign, detector);
			DetectorRegion.create(map).register(detector);
		}
	}

	public static void init(String filename) {
		detectors.clear();
		try {
			DataInputStream stream = new DataInputStream(new FileInputStream(filename));
			try {
				int count = stream.readInt();
				for (;count > 0; --count) {
					//get required info
					UUID id = Util.readUUID(stream);
					//init a new detector
					Detector det = new Detector();
					det.sign1 = Util.readCoordinates(stream);
					det.sign2 = Util.readCoordinates(stream);
					det.sign1down = stream.readBoolean();
					det.sign2down = stream.readBoolean();
					//register
					det.region = DetectorRegion.getRegion(id);
					if (det.region == null) continue;
					det.region.register(det);
					detectors.put(det.region.getWorldName(), det.sign1, det);
					detectors.put(det.region.getWorldName(), det.sign2, det);
				}
			} catch (IOException ex) {
				Util.log(Level.WARNING, "An IO exception occured while reading detector sign locations!");
				ex.printStackTrace();
			} catch (Exception ex) {
				Util.log(Level.WARNING, "A general exception occured while reading detector sign locations!");
				ex.printStackTrace();
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException ex) {
			//nothing, we allow non-existence of this file
		} catch (Exception ex) {
			Util.log(Level.WARNING, "An exception occured at the end while reading detector sign locations!");
			ex.printStackTrace();
		}
	}
	public static void deinit(String filename) {
		try {
			File f = new File(filename);
			if (f.exists()) f.delete();
			DataOutputStream stream = new DataOutputStream(new FileOutputStream(filename));
			try {
				Set<Detector> detectorset = new HashSet<Detector>(detectors.size() / 2);
				for (Detector dec : detectors.values()) {
					detectorset.add(dec);
				}
				stream.writeInt(detectorset.size());
				for (Detector det : detectorset) {
					Util.writeUUID(stream, det.region.getUniqueId());
					Util.writeCoordinates(stream, det.sign1);
					Util.writeCoordinates(stream, det.sign2);
					stream.writeBoolean(det.sign1down);
					stream.writeBoolean(det.sign2down);
				}
			} catch (IOException ex) {
				Util.log(Level.WARNING, "An IO exception occured while reading detector sign locations!");
				ex.printStackTrace();
			} catch (Exception ex) {
				Util.log(Level.WARNING, "A general exception occured while reading detector sign locations!");
				ex.printStackTrace();
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException ex) {
			Util.log(Level.WARNING, "Failed to write to the detector  sign locations save file!");
			ex.printStackTrace();
		} catch (Exception ex) {
			Util.log(Level.WARNING, "An exception occured at the end while reading detector sign locations!");
			ex.printStackTrace();
		}
	}
	
}
