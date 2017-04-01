package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.controller.EntityController;
import com.bergerkiller.bukkit.common.conversion.Converter;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

import org.bukkit.entity.Minecart;

import java.util.UUID;

public class MemberConverter extends Converter<MinecartMember<?>> {
    public static final MemberConverter toMember = new MemberConverter();

    public MemberConverter() {
        super(MinecartMember.class);
    }

    @Override
    public MinecartMember<?> convert(Object value, MinecartMember<?> def) {
        if (value == null) {
            return def;
        }
        if (value instanceof UUID) {
            return LogicUtil.fixNull(MinecartMemberStore.getFromUID((UUID) value), def);
        }
        final MinecartMember<?> member;
        if (value instanceof MinecartMember) {
            member = (MinecartMember<?>) value;
        } else if (value instanceof Minecart) {
            EntityController<?> controller = CommonEntity.get((Minecart) value).getController();
            if (controller instanceof MinecartMember) {
                member = (MinecartMember<?>) controller;
            } else {
                return def;
            }
        } else {
            return def;
        }
        if (member.isUnloaded() || TrainCarts.isWorldDisabled(member.getEntity().getWorld())) {
            return def;
        } else {
            return member;
        }
    }

    @Override
    public boolean isCastingSupported() {
        return true;
    }

    @Override
    public boolean isRegisterSupported() {
        return true;
    }

}
