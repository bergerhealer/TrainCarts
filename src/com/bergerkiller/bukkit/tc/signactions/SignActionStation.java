package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.utils.FaceUtil;

public class SignActionStation extends SignAction {
	
	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE)) {
			if (info.isTrainSign() || info.isCartSign()) {
				if (info.isType("station")) {
					if (info.hasRails()) {
						MinecartGroup group = info.getGroup();
						if (group != null) {
							if (info.isAction(SignActionType.GROUP_LEAVE)) {
							} else if (!info.isPowered()) {
								//TODO: Correctly handle this! (zomg)
								group.clearActions();
							} else {
								//Check if not already targeting
								if (group != null && info.hasRails()) {		
									//Get station length
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
									boolean west = info.isPowered(BlockFace.WEST);
									boolean east = info.isPowered(BlockFace.EAST);
									boolean north = info.isPowered(BlockFace.NORTH);
									boolean south = info.isPowered(BlockFace.SOUTH);

									//which directions to move, or brake?
									BlockFace instruction = BlockFace.UP; //SELF is brake
									if (dir == BlockFace.WEST) {
										if (west && !east) {
											instruction = BlockFace.WEST;
										} else if (east && !west) {
											instruction = BlockFace.EAST;
										} else {
											instruction = BlockFace.SELF;
										}
									} else if (dir == BlockFace.SOUTH) {
										if (north && !south) {
											instruction = BlockFace.NORTH;
										} else if (south && !north) {
											instruction = BlockFace.SOUTH;
										} else {
											instruction = BlockFace.SELF;
										}
									}
									if (instruction == BlockFace.UP) return; 

									//What do we do?
									Location l = info.getRailLocation();
									if (instruction == BlockFace.SELF) {
										if (north || east || south || west) {
											//Redstone change and moving?
											if (!info.isAction(SignActionType.REDSTONE_CHANGE) || !info.getMember().isMoving()) {
												//Brake
												//TODO: ADD CHECK?!
												group.clearActions();
												midd.addActionLaunch(l, 0);
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
													group.addActionWait(delayMS);
													midd.addActionLaunch(l, TrainCarts.launchForce);
												} else {
													group.addActionWaitForever();
												}
											}
										}
									} else {
										//Launch
										group.clearActions();
										Location next = l.clone().add(instruction.getModX() * length, 0, instruction.getModZ() * length);
										MinecartMember head = group.head();
										if (delayMS > 0 || (head.isMoving() && head.getDirection() != instruction)) {
											//Reversing or has delay, need to center it in the middle first
											midd.addActionLaunch(l, 0);
										}
										group.addActionWait(delayMS);
										midd.addActionLaunch(next, TrainCarts.launchForce);
									}
								}
							}
						}
						if (!info.isAction(SignActionType.REDSTONE_CHANGE)) {
							info.setLevers(info.isAction(SignActionType.GROUP_ENTER));
						}
					}
				}
			}
		}
	}
}
