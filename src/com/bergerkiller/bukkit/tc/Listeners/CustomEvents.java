package com.bergerkiller.bukkit.tc.Listeners;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.VelocityTarget;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Utils.FaceUtil;

public class CustomEvents {
	
	public static class SignInfo {
		public SignInfo(Block signblock, MinecartMember member) {
			this.signblock = signblock;
			this.member = member;
			this.memberchecked = true;
		}
		public SignInfo(Block signblock) {
			this.signblock = signblock;
		}
		private Block signblock;
		private BlockFace facing = null;
		private BlockFace raildirection = null;
		private Sign sign = null;
		private MinecartMember member = null;
		private boolean memberchecked = false;
		
		public boolean isPowered(BlockFace from) {
			return this.getBlock().getRelative(from).isBlockIndirectlyPowered();
		}
		public boolean isPowered() {
			return this.getBlock().isBlockIndirectlyPowered();
		}
		public Block getBlock() {
			return this.signblock;
		}
		public Block getRails() {
			return this.signblock.getRelative(0, 2, 0);
		}
		public boolean hasRails() {
			return BlockUtil.isRails(this.getRails());
		}
		public BlockFace getRailDirection() {
			if (this.raildirection == null) {
				this.raildirection = BlockUtil.getRails(getRails()).getDirection();
			}
			return this.raildirection;
		}
		public Location getRailLocation() {
			return getLocation().add(0, 2, 0);
		}
		public Location getLocation() {
			return this.signblock.getLocation();
		}
		public BlockFace getFacing() {
			if (this.facing == null) {
				this.facing = BlockUtil.getFacing(this.getBlock());
			}
			return this.facing;
		}
		public boolean isFacing() {
			if (getMember() == null) return false;
			return getMember().getDirection() != getFacing();
		}
		public Sign getSign() {
			if (this.sign == null) {
				this.sign = BlockUtil.getSign(signblock);
			}
			return this.sign;
		}
		public MinecartMember getMember() {
			if (!this.memberchecked) {
				this.member = MinecartMember.getAt(getRailLocation());
				this.memberchecked = true;
			}
			return this.member;
		}
		public MinecartGroup getGroup() {
			MinecartMember mm = this.getMember();
			if (mm == null) return null;
			return mm.getGroup();
		}
		public String getLine(int index) {
			return this.getSign().getLine(index);
		}
	}
	
	public static enum ActionType {REDSTONE_CHANGE, REDSTONE_ON, REDSTONE_OFF, MEMBER_ENTER, MEMBER_MOVE, GROUP_ENTER, GROUP_LEAVE}
	
	public static void handleStation(SignInfo info) {
		//Check if not already targeting
		MinecartGroup group = info.getGroup();
		if (group != null && info.hasRails()) {			
			//Get station length
			if (!info.getLine(0).equalsIgnoreCase("[train]")) return;
			if (!info.getLine(1).toLowerCase().startsWith("station")) return;
			double length = 0;
			try {
				length = Double.parseDouble(info.getLine(1).substring(7).trim());
			} catch (Exception ex) {};
			long delayMS = 0;
			try {
				 delayMS = (long) (Double.parseDouble(info.getLine(2)) * 1000);
			} catch (Exception ex) {};
			//Get the mode used
			int mode = 0;
			if (info.getLine(3).equalsIgnoreCase("continue")) {
				mode = 1;
			} else if (info.getLine(3).equalsIgnoreCase("reverse")) {
				mode = 2;
			} else if (info.getLine(3).equalsIgnoreCase("left")) {
				mode = 3;
			} else if (info.getLine(3).equalsIgnoreCase("right")) {
				mode = 4;
			}
			
			
			//Get the middle minecart
			MinecartMember midd = group.middle();
			//First, get the direction of the tracks above
			BlockFace dir = info.getRailDirection();
			//Get the length of the track to center in
			if (length == 0) {
				//manually calculate the length
				//use the amount of straight blocks
				for (BlockFace face : FaceUtil.getFaces(dir)) {
					int tlength = 0;
					//get the type of rail required
					BlockFace checkface = face;
					if (checkface == BlockFace.NORTH)
						checkface = BlockFace.SOUTH;
					if (checkface == BlockFace.EAST)
						checkface = BlockFace.WEST;
					
					Block b = info.getRails();
					int maxlength = 20;
					while (true) {
						//Next until invalid
						b = b.getRelative(face);
						Rails rr = BlockUtil.getRails(b);
						if (rr == null || rr.getDirection() != checkface)
							break;
						tlength++;
						
						//prevent inf. loop or long processing
						maxlength--;
						if (maxlength <= 0) break;
					}
					//Update the length
					if (length == 0 || tlength < length) length = tlength;
				}
			}
			
			//which directions to move, or brake?
			BlockFace instruction = BlockFace.UP; //SELF is brake
			if (dir == BlockFace.WEST) {
				boolean west = info.isPowered(BlockFace.WEST);
				boolean east = info.isPowered(BlockFace.EAST);
				if (west && !east) {
					instruction = BlockFace.WEST;
				} else if (east && !west) {
					instruction = BlockFace.EAST;
				} else {
					instruction = BlockFace.SELF;
				}
			} else if (dir == BlockFace.SOUTH) {
				boolean north = info.isPowered(BlockFace.NORTH);
				boolean south = info.isPowered(BlockFace.SOUTH);
				if (north && !south) {
					instruction = BlockFace.NORTH;
				} else if (south && !north) {
					instruction = BlockFace.SOUTH;
				} else {
					instruction = BlockFace.SELF;
				}
			}
			if (instruction == BlockFace.UP) return; 
			
			VelocityTarget lastTarget = null;
			
			//What do we do?
			Location l = info.getRailLocation().add(0.5, 0, 0.5);
			if (instruction == BlockFace.SELF && info.isPowered()) {
				//Brake
				if (TrainCarts.pushAwayStation) {
					group.ignorePushes = true;
				}
				lastTarget = midd.setTarget(l, 0, 0);			
				BlockFace trainDirection = null;
				if (mode == 1) {
					//Continue
					trainDirection = midd.getDirection();
				} else if (mode == 2) {
					//Reverse
					trainDirection = midd.getDirection().getOppositeFace();
				} else if (mode == 3 || mode == 4) {
					//Relative left/right
					BlockFace signdir = info.getFacing();
					//Convert :)
					float yaw = FaceUtil.faceToYaw(signdir);
					if (mode == 3) {
						//Left
						yaw += 90;
					} else {
						//Right
						yaw -= 90;
					}
					//Apply
					trainDirection = FaceUtil.yawToFace(yaw);					
				} else {
					l = null; //Nothing
				}
				if (l != null) {
					//Actual launching here
					l = l.add(trainDirection.getModX() * length, 0, trainDirection.getModZ() * length);
					lastTarget = midd.addTarget(l, midd.maxSpeed, delayMS);
				}
			} else {
				//Launch
				if (TrainCarts.pushAwayStation) {
					group.ignorePushes = true;
				}
				l = l.add(instruction.getModX() * length, 0, instruction.getModZ() * length);
				lastTarget = midd.setTarget(l, midd.maxSpeed, delayMS);
			}
			if (TrainCarts.pushAwayStation && lastTarget != null) {
				lastTarget.afterTask = new Task(TrainCarts.plugin, group) {
					public void run() {
						MinecartGroup group = (MinecartGroup) getArg(0);
						group.ignorePushes = false;
					}
				};
			}
		
		}
	}
	public static void spawnTrain(SignInfo info) {
		double force = 0;
		try {
			force = Double.parseDouble(info.getLine(1).substring(5).trim());
		} catch (Exception ex) {};

		//Get the cart types to spawn
		ArrayList<Integer> types = new ArrayList<Integer>();
		for (char cart : (info.getLine(2) + info.getLine(3)).toCharArray()) {
			if (cart == 'm') {
				types.add(0);
			} else if (cart == 's') {
				types.add(1);
			} else if (cart == 'p') {
				types.add(2);
			}
		}
		
		if (types.size() == 0) return;
		
		//Create the group
		MinecartGroup g = new MinecartGroup();
		BlockFace dir = info.getFacing();
		Location[] locs = TrackMap.walk(info.getRails(), dir, types.size(), TrainCarts.cartDistance);
		
		//Check if spot is taken
		for (int i = 0;i < locs.length;i++) {
			if (MinecartMember.getAt(locs[i]) != null) return;
		}
		
		//Spawn the train
		for (int i = 0;i < types.size();i++) {
			g.addMember(MinecartMember.get(locs[i], types.get(i), g));
		}
		g.tail().setForwardForce(force);
		MinecartGroup.load(g);
	}
	
	public static void onSign(SignInfo info, ActionType actionType) {
		if (actionType == ActionType.REDSTONE_ON) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				String secondline = info.getLine(1).toLowerCase();
				if (secondline.startsWith("spawn")) {
					spawnTrain(info);
				}
			}
		} else if (actionType == ActionType.REDSTONE_CHANGE || 
				actionType == ActionType.GROUP_ENTER || 
				actionType == ActionType.GROUP_LEAVE) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).toLowerCase().startsWith("station")) {
					if (info.hasRails()) {
						MinecartGroup group = info.getGroup();
						if (group != null && actionType != ActionType.GROUP_LEAVE) {
							handleStation(info);
						}
						if (actionType != ActionType.REDSTONE_CHANGE) {
							//Toggle the lever if present
							Block main = BlockUtil.getAttachedBlock(info.getBlock());
							boolean down = actionType == ActionType.GROUP_ENTER;
							for (Block b : BlockUtil.getRelative(main, FaceUtil.getAttached())) {
								BlockUtil.setLever(b, down);
							}
						}
					}
				}
			}
		}
		
		
		if (actionType == ActionType.REDSTONE_ON || actionType == ActionType.GROUP_ENTER) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).toLowerCase().startsWith("trigger")) {
					if (actionType == ActionType.REDSTONE_ON || info.isFacing()) {
						ArrivalSigns.trigger(info.getSign());
					}
				} else if (info.getLine(1).equalsIgnoreCase("push deny")) {
					if (info.isFacing() && info.getGroup() != null) {
						info.getGroup().ignorePushes = true;
					}
				} else if (info.getLine(1).equalsIgnoreCase("push allow")) {
					if (info.isFacing() && info.getGroup() != null) {
						info.getGroup().ignorePushes = false;
					}
				}
			}
		}
		
		if (actionType == ActionType.REDSTONE_ON || actionType == ActionType.MEMBER_ENTER) {
			if (info.isFacing()) {
				if (info.getLine(1).equalsIgnoreCase("destroy") && info.isPowered()) {
					if (info.getMember() != null) {
						info.getMember().destroy();
					}
				} else if (info.getLine(1).equalsIgnoreCase("destroy all") && info.isPowered() ) {
					if (info.getGroup() != null) {
						MinecartGroup group = info.getGroup();
						group.destroy();
					}	
				} else if (info.getLine(1).toLowerCase().startsWith("eject") && info.isPowered()) {
					String[] offsettext = info.getLine(2).split("/");
					Vector offset = new Vector();
					if (offsettext.length == 3) {
						offset.setX(Util.tryParse(offsettext[0], 0));
						offset.setY(Util.tryParse(offsettext[1], 0));
						offset.setZ(Util.tryParse(offsettext[2], 0));
					} else if (offsettext.length == 1) {
						offset.setY(Util.tryParse(offsettext[0], 0));
					}
					if (info.getLine(1).equalsIgnoreCase("eject all") && info.getGroup() != null) {
						for (MinecartMember mm : info.getGroup().getMembers()) {
							if (offset.equals(new Vector())) {
								mm.eject();
							} else {
								mm.eject(offset);
							}
						}
					} else if (info.getMember() != null) {
						if (offset.equals(new Vector())) {
							info.getMember().eject();
						} else {
							info.getMember().eject(offset);
						}
					}
				}
			}
		}
	}
	
}
