package com.bergerkiller.bukkit.tc.utils.signtracker;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

class SignChangeTrackerFallback extends SignChangeTrackerWrap {
    private final Block signBlock;
    private Sign sign;

    public SignChangeTrackerFallback(Block signBlock) {
        this.signBlock = signBlock;
        this.sign = BlockUtil.getSign(signBlock);
    }

    @Override
    public boolean isRemoved() {
        return this.sign == null;
    }

    @Override
    public Block getBlock() {
        return this.signBlock;
    }

    @Override
    public Sign getSign() {
        return this.sign;
    }

    @Override
    public boolean update() {
        Block block = this.signBlock;

        // If removed, try to find it again
        if (this.sign == null) {
            if (MaterialUtil.ISSIGN.get(block)) {
                this.sign = BlockUtil.getSign(block);
                return true;
            }
            return false;
        }

        // Check removed
        // Retrieve BlockState again and check whether the lines have changed
        Sign oldSign = this.sign;
        Sign newSign;
        if (!MaterialUtil.ISSIGN.get(block) || (this.sign = newSign = BlockUtil.getSign(block)) == null) {
            this.sign = null;
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (!oldSign.getLine(i).equals(newSign.getLine(i))) {
                return true;
            }
        }
        return false;
    }
}
