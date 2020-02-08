package com.bergerkiller.bukkit.tc.statements;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class StatementMob extends Statement {

    @Override
    public boolean match(String text) {
        return text.equals("mob") || text.equals("mobs");
    }

    @Override
    public boolean handle(MinecartMember<?> member, String text, SignActionEvent event) {
        for (Entity passenger : member.getEntity().getPassengers()) {
            if (EntityUtil.isMob(passenger)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handle(MinecartGroup group, String text, SignActionEvent event) {
        int count = 0;
        for (MinecartMember<?> member : group) {
            for (Entity passenger : member.getEntity().getPassengers()) {
                if (EntityUtil.isMob(passenger)) {
                    count++;
                }
            }
        }
        return Util.evaluate(count, text);
    }

    @Override
    public boolean matchArray(String text) {
        return text.equals("m");
    }

    public boolean hasMob(MinecartMember<?> member, String mob) {
        int idx = Util.getOperatorIndex(mob);
        if (idx == 0) {
            return false;
        } else if (idx > 0) {
            mob = mob.substring(0, idx - 1);
        }
        //contains one of the defined mobs?
        for (Entity passenger : member.getEntity().getPassengers()) {
            if (EntityUtil.isMob(passenger)) {
                String mobname = EntityUtil.getName(passenger);
                if (mobname.contains(mob)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handleArray(MinecartMember<?> member, String[] mobs, SignActionEvent event) {
        if (mobs.length == 0) {
            return this.handle(member, null, event);
        } else {
            //contains one of the defined mobs?
            for (int i = 0; i < mobs.length; i++) {
                mobs[i] = mobs[i].replace("_", "").replace(" ", "");
            }
            for (String mob : mobs) {
                if (hasMob(member, mob)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public boolean handleArray(MinecartGroup group, String[] mobs, SignActionEvent event) {
        if (mobs.length == 0) {
            return this.handle(group, null, event);
        } else {
            //contains one of the defined mobs?
            for (int i = 0; i < mobs.length; i++) {
                mobs[i] = mobs[i].replace("_", "").replace(" ", "");
            }
            for (String mob : mobs) {
                int count = 0;
                for (MinecartMember<?> member : group) {
                    if (hasMob(member, mob)) {
                        count++;
                    }
                }
                if (Util.evaluate(count, mob)) {
                    return true;
                }
            }
            return false;
        }
    }
}
