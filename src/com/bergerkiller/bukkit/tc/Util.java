package com.bergerkiller.bukkit.tc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.MathHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.utils.FaceUtil;

public class Util {
	public static final float DEGTORAD = 0.017453293F;
	public static final float RADTODEG = 57.29577951F;
	public static final double halfRootOfTwo = 0.707106781;
		
	private static Logger logger = Logger.getLogger("Minecraft");
	public static void log(Level level, String message) {
		logger.log(level, "[TrainCarts] " + message);
	}
	public static void broadcast(Vector vec) {
		broadcast("VECTOR: [X=" + round(vec.getX(), 3) + " | Y=" + round(vec.getY(), 3) + " | Z=" + round(vec.getZ(), 3) + "]");
	}
	public static void broadcast(String msg) {
		for (Player p : Bukkit.getServer().getOnlinePlayers()) {
			p.sendMessage(msg);
		}
		//Bukkit.getServer().broadcastMessage(msg);
	}
	public static void heartbeat() {
		broadcast("HEARTBEAT: " + System.currentTimeMillis());
	}
	
	public static double tryParse(String text, double def) {
		try {
			return Double.parseDouble(text);
		} catch (Exception ex) {
			return def;
		}
	}
	
	/**
	 * Converts a Location to a destination name.
	 * @param loc The Location to convert
	 * @return A string representing the destination name.
	 */
	public static String blockToString(Block block){
		return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
	}
	/**
	 * Converts a destination name to a String.
	 * @param str The String to convert
	 * @return A Location representing the String.
	 */
	public static Block stringToBlock(String str){
		try{
			String s[] = str.split("_");
			String w = "";
			int x = 0, y = 0, z = 0;
			for (int i = 0; i < s.length; i++){
				switch (s.length - i){
				case 1: z = Integer.parseInt(s[i]); break;
				case 2: y = Integer.parseInt(s[i]); break;
				case 3: x = Integer.parseInt(s[i]); break;
				default: if (!w.isEmpty()){w += "_";} w += s[i]; break;
				}
			}
			World world = Bukkit.getServer().getWorld(w);
			if (world == null) return null;
			return world.getBlockAt(x, y, z);
		} catch (Exception e){
			return null;
		}
	}

	public static double lengthSquared(double... values) {
		double rval = 0;
		for (double value : values) {
			rval += value * value;
		}
		return rval;
	}
	public static double length(double... values) {
		return Math.sqrt(lengthSquared(values));
	}
	public static double distance(double x1, double y1, double x2, double y2) {
		return length(x1 - x2, y1 - y2);
	}
	public static double distanceSquared(double x1, double y1, double x2, double y2) {
		return lengthSquared(x1 - x2, y1 - y2);
	}
	public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		return length(x1 - x2, y1 - y2, z1 - z2);
	}
	public static double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
		return lengthSquared(x1 - x2, y1 - y2, z1 - z2);
	}
	
	public static float getAngleDifference(float angle1, float angle2) {
        return Math.abs(normalAngle(angle1 - angle2));
	}
	public static float normalAngle(float angle) {
        while (angle <= -180) angle += 360;
        while (angle > 180) angle -= 360;
        return angle;
	}
	public static double normalize(double x, double z, double reqx, double reqz) {
		return Math.sqrt(lengthSquared(reqx, reqz) / lengthSquared(x, z));
	}
	public static float getLookAtYaw(MinecartMember loc, MinecartMember lookat) {
		return getLookAtYaw(loc.getMinecart(), lookat.getMinecart());
	}
	public static float getLookAtYaw(Entity loc, Entity lookat) {
		return getLookAtYaw(loc.getLocation(), lookat.getLocation());
	}
	public static float getLookAtYaw(Block loc, Block lookat) {
		return getLookAtYaw(loc.getLocation(), lookat.getLocation());
	}
	public static float getLookAtYaw(Location loc, Location lookat) {
        // Values of change in distance (make it relative)
        return getLookAtYaw(lookat.getX() - loc.getX(), lookat.getZ() - loc.getZ());
	}
	public static float getLookAtYaw(Vector motion) {
		return getLookAtYaw(motion.getX(), motion.getZ());
	}
	public static float getLookAtYaw(double dx, double dz) {
        float yaw = 0;
        // Set yaw
        if (dx != 0) {
            // Set yaw start value based on dx
            if (dx < 0) {
            	yaw = 270;
            } else {
                yaw = 90;
            }
            yaw -= atan(dz / dx);
        } else if (dz < 0) {
        	yaw = 180;
        }
        return -yaw - 90;
	}
	public static float getLookAtPitch(double motX, double motY, double motZ) {
		return getLookAtPitch(motY, length(motX, motZ));
	}
	public static float getLookAtPitch(double motY, double motXZ) {
		return -atan(motY / motXZ);
	}
	public static float atan(double value) {
		return RADTODEG * (float) Math.atan(value);
	}
	public static Location move(Location loc, Vector offset) {
        // Convert rotation to radians
        float ryaw = -loc.getYaw() / 180f * (float) Math.PI;
        float rpitch = loc.getPitch() / 180f * (float) Math.PI;

        //Conversions found by (a lot of) testing
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        z -= offset.getX() * Math.sin(ryaw);
        z += offset.getY() * Math.cos(ryaw) * Math.sin(rpitch);
        z += offset.getZ() * Math.cos(ryaw) * Math.cos(rpitch);
        x += offset.getX() * Math.cos(ryaw);
        x += offset.getY() * Math.sin(rpitch) * Math.sin(ryaw);
        x += offset.getZ() * Math.sin(ryaw) * Math.cos(rpitch);
        y += offset.getY() * Math.cos(rpitch);
        y -= offset.getZ() * Math.sin(rpitch);
        return new Location(loc.getWorld(), x, y, z, loc.getYaw(), loc.getPitch());
    }
    public static double round(double Rval, int Rpl) {
  	  double p = Math.pow(10, Rpl);
  	  return Math.round(Rval * p) / p;
    }
    public static double fixNaN(double value, double def) {
    	if (Double.isNaN(value)) return def;
    	return value;
    }
    public static double fixNaN(double value) {
    	return fixNaN(value, 0);
    }
	public static int locToChunk(double loc) {
		return MathHelper.floor(loc / 16.0D);
	}
    
    public static String[] remove(String[] input, int index) {
    	String[] rval = new String[input.length - 1];
    	int i = 0;
    	for (int ii = 0; ii < input.length; ii++) {
    		if (ii != index) {
    			rval[i] = input[ii];
    			i++;
    		}
    	}
    	return rval;
    }
    public static String combineNames(Set<Material> items) {
		if (items.size() == 0) return "";
		String[] sitems = new String[items.size()];
		int i = 0;
		for (Material item : items) {
			sitems[i] = item.toString();
			i++;
		}
		return combineNames(sitems);
    }
	public static String combineNames(Collection<String> items) {
		if (items.size() == 0) return "";
		String[] sitems = new String[items.size()];
		int i = 0;
		for (String item : items) {
			sitems[i] = item;
			i++;
		}
		return combineNames(sitems);
	}
    public static String combine(String separator, String... lines) {
    	StringBuilder builder = new StringBuilder();
    	for (String line : lines) {
    		if (line != null && !line.equals("")) {
        		if (builder.length() != 0) builder.append(separator);
        		builder.append(line);
    		}
    	}
    	return builder.toString();
    }
	public static String combineNames(String[] items) {	
		if (items.length == 0) return "";
    	if (items.length == 1) return items[0];
    	int count = 1;
    	String name = "";
    	for (String item : items) {
    		name += item;
    		if (count == items.length - 1) {
    			name += " and ";
    		} else if (count != items.length) {
    			name += ", ";
    		}
    		count++;
    	}
		return name;
	}
	
	public static boolean getBool(String name) {
		name = name.toLowerCase().trim();
		if (name.equals("yes")) return true;
		if (name.equals("allow")) return true;
		if (name.equals("true")) return true;
		if (name.equals("ye")) return true;
		if (name.equals("y")) return true;
		if (name.equals("t")) return true;
		if (name.equals("on")) return true;
		if (name.equals("enabled")) return true;
		if (name.equals("enable")) return true;
		return false;
	}
	public static boolean isBool(String name) {
		name = name.toLowerCase().trim();
		if (name.equals("yes")) return true;
		if (name.equals("allow")) return true;
		if (name.equals("true")) return true;
		if (name.equals("ye")) return true;
		if (name.equals("y")) return true;
		if (name.equals("t")) return true;
		if (name.equals("on")) return true;
		if (name.equals("enabled")) return true;
		if (name.equals("enable")) return true;
		if (name.equals("no")) return true;
		if (name.equals("none")) return true;
		if (name.equals("deny")) return true;
		if (name.equals("false")) return true;
		if (name.equals("n")) return true;
		if (name.equals("f")) return true;
		if (name.equals("off")) return true;
		if (name.equals("disabled")) return true;
		if (name.equals("disable")) return true;
		return false;
	}

    /*
     * Stages the value between the two points using a stage from 0 to 1
     */
    public static double stage(double d1, double d2, double stage) {
    	if (Double.isNaN(stage)) return d2;
    	if (stage < 0) stage = 0;
    	if (stage > 1) stage = 1;
    	return d1 * (1 - stage) + d2 * stage;
    }
    public static Vector stage(Vector vec1, Vector vec2, double stage) {
    	Vector newvec = new Vector();
    	newvec.setX(stage(vec1.getX(), vec2.getX(), stage));
    	newvec.setY(stage(vec1.getY(), vec2.getY(), stage));
    	newvec.setZ(stage(vec1.getZ(), vec2.getZ(), stage));
    	return newvec;
    }
    public static Location stage(Location loc1, Location loc2, double stage) {
    	Location newloc = new Location(loc1.getWorld(), 0, 0, 0);
    	newloc.setX(stage(loc1.getX(), loc2.getX(), stage));
    	newloc.setY(stage(loc1.getY(), loc2.getY(), stage));
    	newloc.setZ(stage(loc1.getZ(), loc2.getZ(), stage));
    	newloc.setYaw((float) stage(loc1.getYaw(), loc2.getYaw(), stage));
    	newloc.setPitch((float) stage(loc1.getPitch(), loc2.getPitch(), stage));
    	return newloc;
    }
    	
	public static boolean isInverted(double value1, double value2) {
		return (value1 > 0 && value2 < 0) || (value1 < 0 && value2 > 0);
	}

	public static Vector getDirection(float yaw, float pitch) {
		return new Location(null, 0, 0, 0, yaw, pitch).getDirection();
	}
		
	public static double limit(double value, double limit) {
		return limit(value, -limit, limit);
	}
	public static double limit(double value, double min, double max) {
		if (value < min) return min;
		if (value > max) return max;
		return value;
	}
	
	public static void setVectorLength(Vector vector, double length) {
		if (length >= 0) {
			setVectorLengthSquared(vector, length * length);
		} else {
			setVectorLengthSquared(vector, -length * length);
		}
	}
	public static void setVectorLengthSquared(Vector vector, double lengthsquared) {
		double vlength = vector.lengthSquared();
		if (Math.abs(vlength) > 0.0001) {
			if (lengthsquared < 0) {
				vector.multiply(-Math.sqrt(-lengthsquared / vlength));
			} else {
				vector.multiply(Math.sqrt(lengthsquared / vlength));
			}
		}
	}

	public static boolean isHeadingTo(BlockFace direction, Vector velocity) {
		return isHeadingTo(FaceUtil.faceToVector(direction), velocity);
	}
	public static boolean isHeadingTo(Location from, Location to, Vector velocity) {
		return isHeadingTo(new Vector(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ()), velocity);
	}
	public static boolean isHeadingTo(Vector offset, Vector velocity) {
		double dbefore = offset.lengthSquared();
		if (dbefore < 0.0001) return true;
		velocity = velocity.clone();
		setVectorLengthSquared(velocity, dbefore);
		return dbefore > velocity.subtract(offset).lengthSquared();
	}
	
	public static <T extends Event> T call(T event) {
		Bukkit.getPluginManager().callEvent(event);
		return event;
	}
	
	public static List<WorldServer> getWorlds() {
		return getMCServer().worlds;
	}
	public static MinecraftServer getMCServer() {
		return getCraftServer().getServer();
	}
	public static CraftServer getCraftServer() {
		return (CraftServer) Bukkit.getServer();
	}
	
	public static void loadChunks(Location location) {
		int x = MathHelper.floor(location.getX()) >> 4;
		int z = MathHelper.floor(location.getX()) >> 4;	
	    loadChunks(location.getWorld(), x, z);
	}
	public static void loadChunks(World world, final int xmid, final int zmid) {
		for (int cx = xmid - 2; cx <= xmid + 2; cx++) {
			for (int cz = zmid - 2; cz <= zmid + 2; cz++) {
				world.getChunkAt(cx, cz);
			}
		}
	}
	
	public static UUID readUUID(DataInputStream stream) throws IOException {
		return new UUID(stream.readLong(), stream.readLong());
	}
	public static void writeUUID(DataOutputStream stream, UUID uuid) throws IOException {
		stream.writeLong(uuid.getMostSignificantBits());
		stream.writeLong(uuid.getLeastSignificantBits());
	}
	public static ChunkCoordinates readCoordinates(DataInputStream stream) throws IOException {
		return new ChunkCoordinates(stream.readInt(), stream.readInt(), stream.readInt());
	}
	public static void writeCoordinates(DataOutputStream stream, ChunkCoordinates coordinates) throws IOException {
		stream.writeInt(coordinates.x);
		stream.writeInt(coordinates.y);
		stream.writeInt(coordinates.z);
	}
		

}
