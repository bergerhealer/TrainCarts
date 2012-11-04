package com.bergerkiller.bukkit.tc.signactions;

import net.minecraft.server.World;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.common.items.ItemParser;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.InventoryWatcher;

public class SignActionCraft extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isType("craft")) {
			//parse the sign
			boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
			boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
			if (!docart && !dotrain) return;
			if (!info.hasRailedMember()) return;
			if (!info.isPowered()) return;

			int radX, radY, radZ;
			radX = radY = radZ = ParseUtil.parseInt(info.getLine(1), TrainCarts.defaultTransferRadius);
			BlockFace dir = info.getRailDirection();
			if (dir == BlockFace.SOUTH) {
				radX = 0;
			} else if (dir == BlockFace.WEST) {
				radZ = 0;
			}
			World world = WorldUtil.getNative(info.getWorld());
			Block m = info.getRails();
			int id;
			Block w = null;
			for (int x = -radX; x <= radX && w == null; x++) {
				for (int y = -radY; y <= radY && w == null; y++) {
					for (int z = -radZ; z <= radZ && w == null; z++) {
						id = world.getTypeId(m.getX() + x, m.getY() + y, m.getZ() + z);
						if (id == Material.WORKBENCH.getId()) {
							w = m.getRelative(x, y, z);
						}
					}
				}
			}
			if (w != null) {
				//get the inventory to transfer in
				Inventory inventory;
				if (docart) {
					if (!info.getMember().isStorageCart()) return;
					inventory = info.getMember().getInventory();
				} else {
					inventory = info.getGroup().getInventory();
				}
				if (TrainCarts.showTransferAnimations) {
					inventory = InventoryWatcher.convert(w, info.getMember(), inventory);
				}

				// craft
				for (ItemParser item : Util.getParsers(info.getLine(2), info.getLine(3))) {
					RecipeUtil.craftItems(item, inventory);
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			if (event.isType("craft")) {
				return handleBuild(event, Permission.BUILD_CRAFTER, "workbench item crafter", "craft items inside storage minecarts");
			}
		}
		return false;
	}

}
