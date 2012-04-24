package com.bergerkiller.bukkit.tc.statements;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;

public class StatementMob extends Statement {

	@Override
	public boolean match(String text) {
		return text.equals("mob") || text.equals("mobs");
	}
	
	@Override
	public boolean handle(MinecartMember member, String text) {
		return member.hasPassenger() && EntityUtil.isMob(member.passenger);
	}
	
	@Override
	public boolean matchArray(String text) {
		return text.equals("m");
	}
	
	public boolean hasMob(MinecartMember member, String[] fixedMobs) {
		//contains one of the defined mobs?
		if (member.hasPassenger() && EntityUtil.isMob(member.passenger)) {
			String mobname = EntityUtil.getName(member.passenger);
			for (String type : fixedMobs) {
			    if (mobname.contains(type)) {
			    	return true;
			    }
			}
		}
		return false;
	}
	
	@Override
	public boolean handleArray(MinecartMember member, String[] mobs) {
		if (mobs.length == 0) {
			return this.handle(member, null);
		} else {
			//contains one of the defined mobs?
			for (int i = 0; i < mobs.length; i++) {
				mobs[i] = mobs[i].replace("_", "").replace(" ", "");
			}
			return hasMob(member, mobs);
		}
	}
	
	@Override
	public boolean handleArray(MinecartGroup group, String[] mobs) {
		if (mobs.length == 0) {
			return this.handle(group, null);
		} else {
			//contains one of the defined mobs?
			for (int i = 0; i < mobs.length; i++) {
				mobs[i] = mobs[i].replace("_", "").replace(" ", "");
			}
			for (MinecartMember member : group) {
				if (hasMob(member, mobs)) {
					return true;
				}
			}
			return false;
		}
	}
}
