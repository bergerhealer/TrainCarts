package com.bergerkiller.bukkit.tc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;

import net.minecraft.server.ChunkCoordinates;

public class DetectorSign {

	private static final Map<ChunkCoordinates, Set<DetectorSign>> signs = new HashMap<ChunkCoordinates, Set<DetectorSign>>();
	private static Set<DetectorSign> getSigns(ChunkCoordinates at) {
		Set<DetectorSign> rval = signs.get(at);
		if (rval == null) {
			rval = new HashSet<DetectorSign>(1);
			signs.put(at, rval);
		}
		return rval;
	}
	public static List<DetectorSign> getSigns(Block at) {
		Set<DetectorSign> signset = signs.get(BlockUtil.getCoordinates(at));
		if (signset == null) return new ArrayList<DetectorSign>(0);
		List<DetectorSign> signs = new ArrayList<DetectorSign>(signset.size());
		for (DetectorSign sign : signset) {
			if (sign.world.equals(at.getWorld().getUID())) {
				signs.add(sign);
			}
		}
		return signs;
	}
	public static DetectorSign add(Block startsign, Block endsign, TrackMap rails) {
		return new DetectorSign(startsign, endsign, rails.getCoordinates());
	}
	
	public static void init(String filename) {
		signs.clear();
		try {
			DataInputStream stream = new DataInputStream(new FileInputStream(filename));
			try {
				int count = stream.readInt();
				for (int i = 0; i < count; i++) {
					readFrom(stream);
				}
			} catch (IOException ex) {
				Util.log(Level.WARNING, "An IO exception occured while reading detector sign regions!");
				ex.printStackTrace();
			} catch (Exception ex) {
				Util.log(Level.WARNING, "A general exception occured while reading detector sign regions!");
				ex.printStackTrace();
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException ex) {
			//nothing, we allow non-existence of this file
		} catch (Exception ex) {
			Util.log(Level.WARNING, "An exception occured at the end while reading detector sign regions!");
			ex.printStackTrace();
		}
	}
	public static void deinit(String filename) {
		try {
			File f = new File(filename);
			if (f.exists()) f.delete();
			DataOutputStream stream = new DataOutputStream(new FileOutputStream(filename));
			try {
				//find all possible detector signs
				Set<DetectorSign> dsigns = new HashSet<DetectorSign>();
				for (Set<DetectorSign> coordsign : signs.values()) {
					dsigns.addAll(coordsign);
				}
				//clear them
				signs.clear();
				//save them
				stream.writeInt(dsigns.size());
				for (DetectorSign sign : dsigns) {
					sign.writeTo(stream);
				}
			} catch (IOException ex) {
				Util.log(Level.WARNING, "An IO exception occured while reading detector sign regions!");
				ex.printStackTrace();
			} catch (Exception ex) {
				Util.log(Level.WARNING, "A general exception occured while reading detector sign regions!");
				ex.printStackTrace();
			} finally {
				stream.close();
			}
		} catch (FileNotFoundException ex) {
			Util.log(Level.WARNING, "Failed to write to the detector sign regions save file!");
			ex.printStackTrace();
		} catch (Exception ex) {
			Util.log(Level.WARNING, "An exception occured at the end while reading detector sign regions!");
			ex.printStackTrace();
		}
	}
	
	private DetectorSign(Block sign1, Block sign2, final Set<ChunkCoordinates> rails) {
		this(BlockUtil.getCoordinates(sign1), BlockUtil.getCoordinates(sign2), sign1.getWorld().getUID(), rails);
	}
	private DetectorSign(final ChunkCoordinates sign1, final ChunkCoordinates sign2, final UUID world, final Set<ChunkCoordinates> rails) {
		this.sign1 = sign1;
		this.sign2 = sign2;
		this.world = world;
		this.rails = rails;
	    for (ChunkCoordinates coord : rails) {
	    	getSigns(coord).add(this);
	    }
	}
	public final Set<ChunkCoordinates> rails;
	public final ChunkCoordinates sign1;
	public final ChunkCoordinates sign2;
	public final UUID world;
	private boolean sign1down = false;
	private boolean sign2down = false;
	
	public Block getSign1(World world) {
		return BlockUtil.getBlock(world, this.sign1);
	}
	public Block getSign2(World world) {
		return BlockUtil.getBlock(world, this.sign2);
	}
	
	public void remove() {
	    for (ChunkCoordinates coord : this.rails) {
	    	getSigns(coord).remove(this);
	    }
	}
	
	public static boolean validate(String[] lines) {
		return SignActionMode.fromString(lines[0]) != SignActionMode.NONE &&
				lines[1].toLowerCase().startsWith("detector");
	}
	public void updateSigns(final Block bsign1, final Block bsign2) {
	    BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(bsign1), this.sign1down);
	    BlockUtil.setLeversAroundBlock(BlockUtil.getAttachedBlock(bsign2), this.sign2down);
	}
	public void refresh(MinecartGroup group) {
		
	}
	public static boolean isDown(String line1, String line2, MinecartGroup group) {
		if (line1.isEmpty()) {
			if (line2.isEmpty()) {
				return true;
			} else {
				return group.hasTag(line2);
			}
		} else if (line2.isEmpty()) {
			return group.hasTag(line1);
		} else {
			return group.hasTag(line1) ||  group.hasTag(line2);
		}
	}
	
	public void update(World world, MinecartGroup addgroup) {
	    //read the signs
		Block bsign1 = this.getSign1(world);
		Block bsign2 = this.getSign2(world);
		if (BlockUtil.isSign(bsign1) && BlockUtil.isSign(bsign2)) {
			Sign sign1 = BlockUtil.getSign(bsign1);
			Sign sign2 = BlockUtil.getSign(bsign2);
			if (validate(sign1.getLines()) && validate(sign2.getLines())) {
				//find the groups
			    Set<MinecartGroup> foundGroups = new HashSet<MinecartGroup>();
			    if (addgroup != null) foundGroups.add(addgroup);
			    for (ChunkCoordinates coord : this.rails) {
			    	MinecartMember member = MinecartMember.getAt(world, coord);
			    	if (member != null && !member.dead) foundGroups.add(member.getGroup());
			    }
			    //parse
			    sign1down = false;
			    sign2down = false;
			    if (!foundGroups.isEmpty()) {
			    	for (MinecartGroup group : foundGroups) {
			    		//parse this group
			    		if (!sign1down) {
				    		String s1l1 = sign1.getLine(2);
				    		String s1l2 = sign1.getLine(3);
				    		if (s1l1.isEmpty()) {
				    			if (s1l2.isEmpty()) {
				    				sign1down = true;
				    			} else {
				    				sign1down = group.hasTag(s1l2);
				    			}
				    		} else if (s1l2.isEmpty()) {
				    			sign1down = group.hasTag(s1l1);
				    		} else {
				    			sign1down = group.hasTag(s1l1) ||  group.hasTag(s1l2);
				    		}
			    		}
			    		if (!sign2down) {
				    		String s2l1 = sign2.getLine(2);
				    		String s2l2 = sign2.getLine(3);
				    		if (s2l1.isEmpty()) {
				    			if (s2l2.isEmpty()) {
				    				sign2down = true;
				    			} else {
				    				sign2down = group.hasTag(s2l2);
				    			}
				    		} else if (s2l2.isEmpty()) {
				    			sign2down = group.hasTag(s2l1);
				    		} else {
				    			sign2down = group.hasTag(s2l1) ||  group.hasTag(s2l2);
				    		}
			    		}
			    	}
			    }
			    //set the signs
			    this.updateSigns(bsign1, bsign2);
			    return;
			}
		}
		//invalid
		this.remove();
	}

	public void writeTo(DataOutputStream stream) throws IOException {
		Util.writeUUID(stream, this.world);
		Util.writeCoordinates(stream, this.sign1);
		Util.writeCoordinates(stream, this.sign2);
		stream.writeInt(this.rails.size());
		for (ChunkCoordinates coord : this.rails) {
			Util.writeCoordinates(stream, coord);
		}
	}
	public static DetectorSign readFrom(DataInputStream stream) throws IOException {
		UUID world = Util.readUUID(stream);
		ChunkCoordinates sign1 = Util.readCoordinates(stream);
		ChunkCoordinates sign2 = Util.readCoordinates(stream);
		int size = stream.readInt();
		Set<ChunkCoordinates> rails = new HashSet<ChunkCoordinates>(size);
		for (int i = 0; i < size; i++) {
			rails.add(Util.readCoordinates(stream));
		}
		return new DetectorSign(sign1, sign2, world, rails);
	}
}
