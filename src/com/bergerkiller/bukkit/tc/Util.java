package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.server.EntityHuman;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

import com.bergerkiller.bukkit.common.MaterialTypeProperty;
import com.bergerkiller.bukkit.common.items.ItemParser;
import com.bergerkiller.bukkit.common.reflection.SafeField;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

public class Util {
	public static final MaterialTypeProperty ISTCRAIL = new MaterialTypeProperty(Material.RAILS, Material.POWERED_RAIL, Material.DETECTOR_RAIL, 
			Material.LADDER, Material.STONE_PLATE, Material.WOOD_PLATE);
	public static final MaterialTypeProperty ISVERTRAIL = new MaterialTypeProperty(Material.LADDER);

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

	private static BlockFace[] possibleFaces = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
	private static List<Block> blockbuff = new ArrayList<Block>();
	public static List<Block> getSignsFromRails(Block railsblock) {
		return getSignsFromRails(blockbuff, railsblock);
	}
	public static List<Block> getSignsFromRails(List<Block> rval, Block railsblock) {
		rval.clear();
		if (ISVERTRAIL.get(railsblock)) {
			BlockFace dir = getVerticalRailDirection(railsblock.getData());
			railsblock = railsblock.getRelative(dir);
			// Loop into the direction to find signs
			while (true) {
				if (addAttachedSigns(railsblock, rval)) {
					railsblock = railsblock.getRelative(dir);
				} else {
					break;
				}
			}
		} else {
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
		}
		return rval;
	}

	public static Block getRailsFromSign(Block signblock) {
		int id;
		if (signblock == null) {
			return null;
		} else {
			id = signblock.getTypeId();
			if (id == Material.WALL_SIGN.getId()) {
				signblock = BlockUtil.getAttachedBlock(signblock);
			} else if (id != Material.SIGN_POST.getId()) {
				return null;
			}
		}
		Block mainBlock = signblock;
		for (BlockFace dir : possibleFaces) {
			signblock = mainBlock.getRelative(dir);
			if (ISTCRAIL.get(signblock)) {
				return signblock;
			}
			while (true) {
				if (ISTCRAIL.get(signblock)) {
					return signblock;
				} else if (hasAttachedSigns(signblock)) {
					signblock = signblock.getRelative(dir);
				} else {
					signblock = signblock.getRelative(dir);
					if (ISTCRAIL.get(signblock)) {
						return signblock;
					} else {
						break;
					}
				}
			}
		}
		return null;
	}

	public static Block findRailsVertical(Block from, BlockFace mode) {
		int sy = from.getY();
		int x = from.getX();
		int z = from.getZ();
		World world = from.getWorld();
		if (mode == BlockFace.DOWN) {
			for (int y = sy - 1; y > 0; --y) {
				if (ISTCRAIL.get(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		} else if (mode == BlockFace.UP) {
			int height = world.getMaxHeight();
			for (int y = sy + 1; y < height; y++) {
				if (ISTCRAIL.get(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		}
		return null;
	}

	public static void addItemsToString(Collection<Integer> itemIds, StringBuilder builder) {
		for (int type : itemIds) {
			if (builder.length() > 0) {
				builder.append(';');
			}
			Material mat = Material.getMaterial(type);
			if (mat == null) {
				builder.append(type);
			} else {
				builder.append(mat.toString().toLowerCase());
			}
		}
	}

	public static String getFurnaceItemString(boolean burnables, boolean heatables) {
		StringBuilder tmp = new StringBuilder();
		if (burnables) {
			addItemsToString(RecipeUtil.getFuelTimes().keySet(), tmp);
		}
		if (heatables) {
			addItemsToString(RecipeUtil.getHeatableItems(), tmp);
		}
		return tmp.toString();
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
					// add parsers directly
					for (ItemParser p : keyparsers) {
						parsers.add(p);
					}
				} else {
					// add parsers with set modifier amount
					// add parsers directly
					for (ItemParser p : keyparsers) {
						parsers.add(new ItemParser((p.hasAmount() ? p.getAmount() : 1) * amount, p.getType(), p.hasData() ? p.getData() : null));
					}
				}
			} else {
				parsers.add(ItemParser.parse(type, amount == null ? null : amount.toString()));
			}
		}
		return parsers.toArray(new ItemParser[0]);
	}

	public static Block getRailsBlock(Block from) {
		if (ISTCRAIL.get(from)) {
			return from;
		} else {
			from = from.getRelative(BlockFace.DOWN);
			return ISTCRAIL.get(from) ? from : null;
		}
	}
	public static boolean isRails(Block block, BlockFace direction) {
		return getRailsBlock(block.getRelative(direction)) != null;
	}

	/**
	 * Sets the invulerability state of an Entity
	 * 
	 * @param entity to set it for
	 * @param state to set to
	 * @return old invulnerability state
	 */
	public static boolean setInvulnerable(Entity entity, boolean state) {
		if (entity instanceof HumanEntity) {
			EntityHuman human = EntityUtil.getNative(entity, EntityHuman.class);
			boolean old = human.abilities.isInvulnerable;
			human.abilities.isInvulnerable = state;
			return old;
		}
		return false;
	}

	/**
	 * Parses a long time value to a readable time String
	 * 
	 * @param time to parse
	 * @return time in the hh:mm:ss format
	 */
	public static String getTimeString(long time) {
		if (time == 0) return "00:00:00";
		time /= 1000; // msec -> sec
		String seconds = Integer.toString((int)(time % 60));
		String minutes = Integer.toString((int)((time % 3600) / 60));
		String hours = Integer.toString((int)(time / 3600)); 
		if (seconds.length() == 1) seconds = "0" + seconds;
		if (minutes.length() == 1) minutes = "0" + minutes;
		if (hours.length() == 1) hours = "0" + hours;
		return hours + ":" + minutes + ":" + seconds;
	}

	/**
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

	/**
	 * Checks if a given rail is sloped
	 * 
	 * @param railsData of the rails
	 * @return True if sloped, False if not
	 */
	public static boolean isSloped(int railsData) {
		railsData &= 0x7;
		return railsData >= 0x2 && railsData <= 0x5;
	}

	/**
	 * Gets the direction a vertical rail pushes the minecart (the wall side)
	 * 
	 * @param raildata of the vertical rail
	 * @return the direction the minecart is pushed
	 */
	public static BlockFace getVerticalRailDirection(int raildata) {
		switch (raildata) {
			case 0x2:
				return BlockFace.WEST;
			case 0x3:
				return BlockFace.EAST;
			case 0x4:
				return BlockFace.SOUTH;
			default:
			case 0x5:
				return BlockFace.NORTH;
		}
	}

	public static int getOperatorIndex(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (isOperator(text.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	public static boolean isOperator(char character) {
		return LogicUtil.containsChar(character, '!', '=', '<', '>');
	}

	/**
	 * Gets if a given Entity can be a passenger of a Minecart
	 * 
	 * @param entity to check
	 * @return True if it can be a passenger, False if not
	 */
	public static boolean canBePassenger(Entity entity) {
		return entity instanceof LivingEntity;
	}

	public static boolean matchText(Collection<String> textValues, String expression) {
		if (textValues.isEmpty() || expression.isEmpty()) {
			return false;
		} else if (expression.startsWith("!")) {
			return !matchText(textValues, expression.substring(1));
		} else {
			String[] elements = expression.split("\\*");
			boolean first = expression.startsWith("*");
			boolean last = expression.endsWith("*");
			for (String text : textValues) {
				if (matchText(text, elements, first, last)) {
					return true;
				}
			}
			return false;
		}
	}
	public static boolean matchText(String text, String expression) {
		if (expression.isEmpty()) {
			return false;
		} else if (expression.startsWith("!")) {
			return !matchText(text, expression.substring(1));
		} else {
			return matchText(text, expression.split("\\*"), expression.startsWith("*"), expression.endsWith("*"));
		}
	}
	public static boolean matchText(String text, String[] elements, boolean firstAny, boolean lastAny) {
		if (elements == null|| elements.length == 0) {
			return true;
		}
		int index = 0;
		boolean has = true;
		boolean first = true;
		for (int i = 0; i < elements.length; i++) {
			if (elements[i].length() == 0) continue;
			index = text.indexOf(elements[i], index);
			if (index == -1 || (first && !firstAny && index != 0)) {
				has = false;
				break;
			} else {
				index += elements[i].length();
			}
			first = false;
		}
		if (has) {
			if (lastAny || index == text.length()) {
				return true;
			}
		}
		return false;
	}

	public static boolean evaluate(double value, String text) {
		if (text == null || text.isEmpty()) {
			return false; //no valid input
		}
		int idx = getOperatorIndex(text);
		if (idx == -1) {
			return value > 0; //no operators, just perform a 'has'
		} else {
			text = text.substring(idx);
		}
		if (text.startsWith(">=") || text.startsWith("=>")) {
			return value >= ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith("<=") || text.startsWith("=<")) {
			return value <= ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith("==")) {
			return value == ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith("!=") || text.startsWith("<>") || text.startsWith("><")) {
			return value != ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith(">")) {
			return value > ParseUtil.parseDouble(text.substring(1), 0.0);
		} else if (text.startsWith("<")) {
			return value < ParseUtil.parseDouble(text.substring(1), 0.0);
		} else if (text.startsWith("=")) {
			return value == ParseUtil.parseDouble(text.substring(1), 0.0);
		} else {
			return false;
		}
	}
}
