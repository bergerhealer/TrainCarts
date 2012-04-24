package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Minecart;

import net.minecraft.server.EntityMinecart;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.SafeField;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.events.MinecartSwapEvent;

public class Util {
		
	/*
	 * Entity states
	 */
	public static float getMinecartYaw(Minecart minecart) {
		return getMinecartYaw(EntityUtil.getNative(minecart));
	}
	public static float getMinecartYaw(EntityMinecart minecart) {
		if (minecart instanceof MinecartMember) return ((MinecartMember) minecart).getYaw();
		return minecart.yaw;
	}
	
	public static void setItemMaxSize(Material material, int maxstacksize) {
		setItemMaxSize(net.minecraft.server.Item.byId[material.getId()], maxstacksize);
	}
	public static void setItemMaxSize(net.minecraft.server.Item item, int maxstacksize) {
		SafeField.set(item, "maxStackSize", maxstacksize);
	}
	
	public static boolean hasAttachedSigns(final Block middle) {
		return addAttachedSigns(middle, null);
	}
	public static boolean addAttachedSigns(final Block middle, final Collection<Block> rval) {
		boolean found = false;
		Block b;
		for (BlockFace face : FaceUtil.axis) {
			b = middle.getRelative(face);
			if (b.getTypeId() == Material.WALL_SIGN.getId()) {
				if (BlockUtil.getAttachedFace(b) == face.getOppositeFace()) {
					if (rval != null) rval.add(b);
					found = true;
				}
			}
		}
		return found;
	}
	
	private static List<Block> blockbuff = new ArrayList<Block>();
	public static List<Block> getSignsFromRails(Block railsblock) {
		return getSignsFromRails(blockbuff, railsblock);
	}
	public static List<Block> getSignsFromRails(List<Block> rval, Block railsblock) {
		rval.clear();
		//ignore mid-sections
		railsblock = railsblock.getRelative(BlockFace.DOWN);
		addAttachedSigns(railsblock, rval);
		railsblock = railsblock.getRelative(BlockFace.DOWN);
		//loop downwards
		while (true) {
			if (railsblock.getTypeId() == Material.SIGN_POST.getId()) {
				rval.add(railsblock);
				railsblock = railsblock.getRelative(BlockFace.DOWN);
			} else if (addAttachedSigns(railsblock, rval)) {
				railsblock = railsblock.getRelative(BlockFace.DOWN);
			} else {
				break;
			}
		}
		return rval;
	}
			
	public static Block getRailsFromSign(Block signblock) {
		if (signblock == null) {
			return null;
		} else if (signblock.getTypeId() == Material.SIGN_POST.getId()) {
		} else if (signblock.getTypeId() == Material.WALL_SIGN.getId()) {
			signblock = BlockUtil.getAttachedBlock(signblock);
		} else {
			return null;
		}
		signblock = signblock.getRelative(BlockFace.UP);
		if (isRails(signblock)) return signblock;
		while (true) {
			if (isRails(signblock)) {
				return signblock;
			} else if (hasAttachedSigns(signblock)) {
				signblock = signblock.getRelative(BlockFace.UP);					
			} else {
				signblock = signblock.getRelative(BlockFace.UP);	
				if (isRails(signblock)) {
					return signblock;
				} else {
					return null;
				}
			}
		}
	}
	
	public static Block findRailsVertical(Block from, BlockFace mode) {
		int sy = from.getY();
		int x = from.getX();
		int z = from.getZ();
		World world = from.getWorld();
		if (mode == BlockFace.DOWN) {
			for (int y = sy - 1; y > 0; --y) {
				if (Util.isRails(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		} else if (mode == BlockFace.UP) {
			int height = world.getMaxHeight();
			for (int y = sy + 1; y < height; y++) {
				if (Util.isRails(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		}
		return null;
	}
				
	public static ItemParser[] getParsers(String... items) {
		StringBuilder total = new StringBuilder();
		for (String item : items) {
			if (item == null) continue;
			item = item.trim();
			if (item.length() == 0) continue;
			if (total.length() > 0) total.append(';');
			total.append(item);
		}
		return getParsers(total.toString());
	}
	public static ItemParser[] getParsers(final String items) {
        List<ItemParser> parsers = new ArrayList<ItemParser>();
        for (String type : items.split(";")) {
            int idx = StringUtil.firstIndexOf(type, "x", "X", " ", "*");
            Integer amount = null;
        	if (idx > 0) {
        		try {
            		amount = Integer.parseInt(type.substring(0, idx));
            		type = type.substring(idx + 1);
        		} catch (Exception ex) {
        		}
        	}
        	ItemParser[] keyparsers = TrainCarts.plugin.getParsers(type);
        	if (keyparsers.length != 0) {
        		if (amount == null) {
        			//add parsers directly
        			for (ItemParser p : keyparsers) {
        				parsers.add(p);
        			}
        		} else {
        			//add parsers with set modifier amount
        			//add parsers directly
        			for (ItemParser p : keyparsers) {
        				parsers.add(new ItemParser(
        						(p.hasAmount() ? p.getAmount() : 1) * amount, 
        						p.getType(), p.hasData() ? p.getData() : null));
        			}
        		}
        	} else {
        		parsers.add(ItemParser.parse(type, amount == null ? null : amount.toString()));
        	}
        }
        return parsers.toArray(new ItemParser[0]);
	}
	
	public static int parse(String line, int def) {
		try {
			return Integer.parseInt(line.substring(line.lastIndexOf(' ') + 1));
		} catch (Exception ex) {
			return def;
		}
	}
	
    public static boolean canDistractWire(Material type) {
		switch (type) {
		case REDSTONE_WIRE : return true;
		case REDSTONE_TORCH_ON : return true;
		case REDSTONE_TORCH_OFF : return true;
		case LEVER : return true;
		case WOOD_PLATE : return true;
		case STONE_PLATE : return true;
		case STONE_BUTTON : return true;
		case DETECTOR_RAIL : return true;
		}
		return false;
    }
	
	public static void replaceMinecarts(EntityMinecart toreplace, EntityMinecart with) {
		with.yaw = toreplace.yaw;
		with.pitch = toreplace.pitch;
		with.locX = toreplace.locX;
		with.locY = toreplace.locY;
		with.locZ = toreplace.locZ;
		with.motX = toreplace.motX;
		with.motY = toreplace.motY;
		with.motZ = toreplace.motZ;
		with.b = toreplace.b;
		with.c = toreplace.c;
		with.fallDistance = toreplace.fallDistance;
		with.ticksLived = toreplace.ticksLived;
		with.uniqueId = toreplace.uniqueId;
		with.setDamage(toreplace.getDamage());
		ItemUtil.transfer(toreplace, with);
		with.dead = false;
		toreplace.bz = true; //force removal in chunk
		toreplace.dead = true;
		
		with.setDerailedVelocityMod(toreplace.getDerailedVelocityMod());
		with.setFlyingVelocityMod(toreplace.getFlyingVelocityMod());
		
		//no longer public in 1.0.0... :-(
		//with.e = toreplace.e;

		//swap
		MinecartSwapEvent.call(toreplace, with);
		WorldUtil.getTracker(toreplace.world).untrackEntity(toreplace);
		toreplace.world.removeEntity(toreplace);
		with.world.addEntity(with);
		if (toreplace.passenger != null) toreplace.passenger.setPassengerOf(with);
	}
		
	public static boolean isRails(Block block) {
		return block != null && isRails(block.getTypeId());
	}
	public static boolean isRails(Material type) {
		return isRails(type.getId());
	}
	public static boolean isRails(int id) {
		return BlockUtil.isRails(id) || isPressurePlate(id);
	}
	
	public static boolean isPressurePlate(int id) {
		return BlockUtil.isType(id, Material.WOOD_PLATE.getId(), Material.STONE_PLATE.getId());
	}
	
	public static Block getRailsBlock(Block from) {
		if (isRails(from)) {
			return from;
		} else {
			from = from.getRelative(BlockFace.DOWN);
			return isRails(from) ? from : null;
		}
	}
	public static boolean isRails(Block block, BlockFace direction) {
		return getRailsBlock(block.getRelative(direction)) != null;
	}
	
	/*
	 * This will return:
	 * South or west if it's a straight piece
	 * Self if it is a cross-intersection
	 */
	public static BlockFace getPlateDirection(Block plate) {
		boolean s = isRails(plate, BlockFace.NORTH) || isRails(plate, BlockFace.SOUTH);
		boolean w = isRails(plate, BlockFace.EAST) || isRails(plate, BlockFace.WEST);
		if (s && w) {
			return BlockFace.SELF;
		} else if (w) {
			return BlockFace.WEST;
		} else if (s) {
			return BlockFace.SOUTH;
		} else {
			return BlockFace.SELF;
		}
	}
	
	private static double getValue(String text, int offset) {
		try {
			return Double.parseDouble(text.substring(offset).trim());
		} catch (NumberFormatException ex) {
			return 0.0;
		}
	}
	
	public static boolean isOperator(char character) {
		final char[] characters = new char[] {'!', '=', '<', '>'};
		for (char c : characters) {
			if (c == character) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean evaluate(double value, String text) {
		if (text == null || text.isEmpty()) {
			return false; //no valid input
		}
		while (!isOperator(text.charAt(0))) {
			text = text.substring(1);
			if (text.isEmpty()) {
				return false; //no operators
			}
		}
		if (text.startsWith(">=") || text.startsWith("=>")) {
			return value >= getValue(text, 2);
		} else if (text.startsWith("<=") || text.startsWith("=<")) {
			return value <= getValue(text, 2);
		} else if (text.startsWith("==")) {
			return value == getValue(text, 2);
		} else if (text.startsWith("!=") || text.startsWith("<>") || text.startsWith("><")) {
			return value != getValue(text, 2);
		} else if (text.startsWith(">")) {
			return value > getValue(text, 1);
		} else if (text.startsWith("<")) {
			return value < getValue(text, 1);
		} else if (text.startsWith("=")) {
			return value == getValue(text, 1);
		} else {
			return false;
		}
	}
}
