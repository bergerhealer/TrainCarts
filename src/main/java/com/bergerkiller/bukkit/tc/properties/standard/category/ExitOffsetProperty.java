package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.type.ExitOffset;

/**
 * Legacy property, which has become obsolete since exit offsets can be
 * configured per seat. If no exit offset is configured for a seat,
 * uses this property instead to eject a player some offset from the cart.
 */
public final class ExitOffsetProperty implements ICartProperty<ExitOffset> {

    @PropertyParser(value="exitoffset", processPerCart = true)
    public ExitOffset parseOffset(PropertyParseContext<ExitOffset> context) {
        final Vector vec = Util.parseVector(context.input(), null);
        if (vec == null) {
            throw new PropertyInvalidInputException("Not a vector");
        }

        if (vec.length() > TCConfig.maxEjectDistance) {
            vec.normalize().multiply(TCConfig.maxEjectDistance);
        }
        return ExitOffset.create(vec,
                context.current().getYaw(),
                context.current().getPitch());
    }

    @PropertyParser(value="exityaw", processPerCart = true)
    public ExitOffset parseYaw(PropertyParseContext<ExitOffset> context) {
        return ExitOffset.create(context.current().getRelativePosition(),
                context.inputFloat(),
                context.current().getPitch());
    }

    @PropertyParser(value="exitpitch", processPerCart = true)
    public ExitOffset parsePitch(PropertyParseContext<ExitOffset> context) {
        return ExitOffset.create(context.current().getRelativePosition(),
                context.current().getYaw(),
                context.inputFloat());
    }

    @PropertyParser(value="exitrot|exitrotation", processPerCart = true)
    public ExitOffset parseRotation(PropertyParseContext<ExitOffset> context) {
        String[] angletext = Util.splitBySeparator(context.input());
        final float new_yaw;
        final float new_pitch;
        if (angletext.length == 2) {
            new_yaw = ParseUtil.parseFloat(angletext[0], Float.NaN);
            new_pitch = ParseUtil.parseFloat(angletext[1], Float.NaN);
        } else if (angletext.length == 1) {
            new_yaw = ParseUtil.parseFloat(angletext[0], Float.NaN);
            new_pitch = 0.0f;
        } else {
            new_yaw = 0.0f;
            new_pitch = 0.0f;
        }
        if (Float.isNaN(new_yaw)) {
            throw new PropertyInvalidInputException("Rotation yaw is not a number");
        }
        if (Float.isNaN(new_pitch)) {
            throw new PropertyInvalidInputException("Rotation pitch is not a number");
        }
        return ExitOffset.create(context.current().getRelativePosition(),
                new_yaw, new_pitch);
    }

    @Override
    public ExitOffset getDefault() {
        return ExitOffset.DEFAULT;
    }

    @Override
    public Optional<ExitOffset> readFromConfig(ConfigurationNode config) {
        if (config.contains("exitOffset") || config.contains("exitYaw") || config.contains("exitPitch")) {
            Vector offset = config.get("exitOffset", new Vector());
            float yaw = config.get("exitYaw", 0.0f);
            float pitch = config.get("exitPitch", 0.0f);
            return Optional.of(ExitOffset.create(offset, yaw, pitch));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<ExitOffset> value) {
        if (value.isPresent()) {
            ExitOffset data = value.get();
            config.set("exitOffset", data.getRelativePosition());
            config.set("exitYaw", data.getYaw());
            config.set("exitPitch", data.getPitch());
        } else {
            config.remove("exitOffset");
            config.remove("exitYaw");
            config.remove("exitPitch");
        }
    }
}
