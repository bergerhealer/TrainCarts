package com.bergerkiller.bukkit.tc.controller;

import java.util.UUID;

import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.common.conversion.CastingConverter;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.conversion.Converter;
import com.bergerkiller.bukkit.common.conversion.ConverterPair;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

public class MemberConverter implements Converter<MinecartMember> {
	public static final MemberConverter toMember = new MemberConverter();

	@Override
	public MinecartMember convert(Object value, MinecartMember def) {
		if (value == null) {
			return def;
		}
		if (value instanceof UUID) {
			return LogicUtil.fixNull(MinecartMember.getFromUID((UUID) value), def);
		}
		final MinecartMember member;
		if (value instanceof MinecartMember) {
			member = (MinecartMember) value;
		} else if (value instanceof Minecart) {
			Object entityHandle = Conversion.toEntityHandle.convert(value);
			if (entityHandle instanceof MinecartMember) {
				member = (MinecartMember) entityHandle;
			} else {
				return def;
			}
		} else {
			return def;
		}
		if (member.isUnloaded()) {
			return def;
		} else {
			return member;
		}
	}

	@Override
	public final MinecartMember convert(Object value) {
		return convert(value, null);
	}

	@Override
	public Class<MinecartMember> getOutputType() {
		return MinecartMember.class;
	}

	@Override
	public boolean isCastingSupported() {
		return true;
	}

	@Override
	public boolean isRegisterSupported() {
		return true;
	}

	@Override
	public <K> ConverterPair<MinecartMember, K> formPair(Converter<K> converterB) {
		return new ConverterPair<MinecartMember, K>(this, converterB);
	}

	@Override
	public <K> Converter<K> cast(Class<K> type) {
		return new CastingConverter<K>(type, this);
	}
}
