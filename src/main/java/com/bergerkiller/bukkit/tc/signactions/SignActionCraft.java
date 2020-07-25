package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.RecipeUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimatedInventory;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.TransferSignUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getFirst;

public class SignActionCraft extends SignAction {
    private static final Material WORKBENCH_TYPE = getFirst("CRAFTING_TABLE", "LEGACY_WORKBENCH");

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("craft");
    }

    @Override
    public void execute(SignActionEvent info) {
        //parse the sign
        boolean docart = info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON) && info.isCartSign() && info.hasMember();
        boolean dotrain = !docart && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.isTrainSign() && info.hasGroup();
        if ((!docart && !dotrain) || !info.hasRailedMember() || !info.isPowered()) {
            return;
        }

        int radX, radY, radZ;
        radX = radY = radZ = ParseUtil.parseInt(info.getLine(1), TCConfig.defaultTransferRadius);
        BlockFace dir = info.getCartEnterFace();
        if (FaceUtil.isAlongX(dir)) {
            radX = 0;
        } else if (FaceUtil.isAlongZ(dir)) {
            radZ = 0;
        }
        World world = info.getWorld();
        Block m = info.getRails();
        Block w = null;
        for (int x = -radX; x <= radX && w == null; x++) {
            for (int y = -radY; y <= radY && w == null; y++) {
                for (int z = -radZ; z <= radZ && w == null; z++) {
                    BlockData data = WorldUtil.getBlockData(world, m.getX() + x, m.getY() + y, m.getZ() + z);
                    if (data.isType(WORKBENCH_TYPE)) {
                        w = m.getRelative(x, y, z);
                    }
                }
            }
        }
        if (w != null || !TCConfig.craftingRequireWorkbench) {
            //get the inventory to transfer in
            Inventory inventory = TransferSignUtil.getInventory(info);
            if (inventory == null) {
                return;
            }

            if (w != null && TCConfig.showTransferAnimations) {
                inventory = ItemAnimatedInventory.convert(inventory, info.getMember(), w);
            }

            // craft
            for (ItemParser item : Util.getParsers(info.getLine(2), info.getLine(3))) {
                RecipeUtil.craftItems(item, inventory);
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_CRAFTER)
                .setName("workbench item crafter")
                .setDescription("craft items inside storage minecarts")
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Crafter")
                .handle(event.getPlayer());
    }
}
