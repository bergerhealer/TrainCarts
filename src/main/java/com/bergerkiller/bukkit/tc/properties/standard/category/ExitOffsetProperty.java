package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.type.ExitOffset;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Legacy property, which has become obsolete since exit offsets can be
 * configured per seat. If no exit offset is configured for a seat,
 * uses this property instead to eject a player some offset from the cart.
 */
public final class ExitOffsetProperty implements ICartProperty<ExitOffset> {

    @CommandTargetTrain
    @PropertyCheckPermission("exitoffset")
    @CommandMethod("train exit offset <dx> <dy> <dz>")
    @CommandDescription("Sets an offset relative to the cart where players exit it")
    private void trainSetOffsetProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("dx") double dx,
            final @Argument("dy") double dy,
            final @Argument("dz") double dz
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(dx, dy, dz, old.getYaw(), old.getPitch()));
        trainGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitoffset")
    @CommandMethod("train exit location <posX> <posY> <posZ>")
    @CommandDescription("Sets world coordinates where players are teleported to when exiting")
    private void trainSetLocationProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("posX") double posX,
            final @Argument("posY") double posY,
            final @Argument("posZ") double posZ
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.createAbsolute(posX, posY, posZ, old.getYaw(), old.getPitch()));
        trainGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("train exit rotation <yaw> <pitch>")
    @CommandDescription("Sets the rotation of the player relative to the cart where players exit it")
    private void trainSetRotationProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("yaw") float yaw,
            final @Argument("pitch") float pitch
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), yaw, pitch));
        trainGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitoffset")
    @CommandMethod("cart exit location <posX> <posY> <posZ>")
    @CommandDescription("Sets world coordinates where players are teleported to when exiting")
    private void cartSetlocationProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("posX") double posX,
            final @Argument("posY") double posY,
            final @Argument("posZ") double posZ
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.createAbsolute(posX, posY, posZ, old.getYaw(), old.getPitch()));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitoffset")
    @CommandMethod("cart exit offset <dx> <dy> <dz>")
    @CommandDescription("Sets an offset relative to the cart where players exit it")
    private void cartSetOffsetProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("dx") double dx,
            final @Argument("dy") double dy,
            final @Argument("dz") double dz
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(dx, dy, dz, old.getYaw(), old.getPitch()));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("cart exit rotation <yaw> <pitch>")
    @CommandDescription("Sets the rotation of the player relative to the cart where players exit it")
    private void cartSetRotationProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("yaw") float yaw,
            final @Argument("pitch") float pitch
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), yaw, pitch));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("cart exit yaw <yaw>")
    @CommandDescription("Sets the yaw rotation relative to the cart exiting players are positioned at")
    private void cartSetRotationYawProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("yaw") float yaw
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), yaw, old.getPitch()));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("cart exit yaw free")
    @CommandDescription("Sets the yaw orientation of the player after exiting remains as it was before")
    private void cartSetRotationYawFreeProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), Float.NaN, old.getPitch()));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("cart exit pitch <pitch>")
    @CommandDescription("Sets the pitch rotation relative to the cart exiting players are positioned at")
    private void cartSetRotationPitchProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("pitch") float pitch
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), old.getYaw(), pitch));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("cart exit pitch free")
    @CommandDescription("Sets the pitch orientation of the player after exiting remains as it was before")
    private void cartSetRotationPitchFreeProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), old.getYaw(), Float.NaN));
        cartGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("train exit yaw <yaw>")
    @CommandDescription("Sets the yaw rotation relative to the cart exiting players are positioned at")
    private void trainSetRotationYawProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("yaw") float yaw
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), yaw, old.getPitch()));
        trainGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("train exit yaw free")
    @CommandDescription("Sets the yaw orientation of the player after exiting remains as it was before")
    private void trainSetRotationYawFreeProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), Float.NaN, old.getPitch()));
        trainGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("train exit pitch <pitch>")
    @CommandDescription("Sets the pitch rotation relative to the cart exiting players are positioned at")
    private void trainSetRotationPitchProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("pitch") float pitch
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), old.getYaw(), pitch));
        trainGetProperty(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("exitrotation")
    @CommandMethod("train exit pitch free")
    @CommandDescription("Sets the pitch orientation of the player after exiting remains as it was before")
    private void trainSetRotationPitchFreeProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        ExitOffset old = properties.get(this);
        properties.set(this, ExitOffset.create(old.getPosition(), old.getYaw(), Float.NaN));
        trainGetProperty(sender, properties);
    }

    @CommandMethod("train exit")
    @CommandDescription("Displays the current exit offset and rotation set for the train")
    private void trainGetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        showProperty(sender, "Train", properties.get(this));
    }

    @CommandMethod("cart exit")
    @CommandDescription("Displays the current exit offset and rotation set for the cart")
    private void cartGetProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        showProperty(sender, "Cart", properties.get(this));
    }

    private void showProperty(CommandSender sender, String prefix, ExitOffset offset) {
        MessageBuilder builder = new MessageBuilder();
        if (offset.isAbsolute()) {
            builder.yellow(prefix + " exit coordinates are set to:");
            builder.newLine().yellow("  Location X: ").white(offset.getX());
            builder.newLine().yellow("  Location Y: ").white(offset.getY());
            builder.newLine().yellow("  Location Z: ").white(offset.getZ());
        } else {
            builder.yellow(prefix + " exit offset is set to:");
            builder.newLine().yellow("  Relative X: ").white(offset.getX());
            builder.newLine().yellow("  Relative Y: ").white(offset.getY());
            builder.newLine().yellow("  Relative Z: ").white(offset.getZ());
        }
        if (offset.hasLockedYaw()) {
            builder.newLine().yellow("  Yaw: ").white(offset.getYaw());
        } else {
            builder.newLine().yellow("  Yaw: ").green("Not set (free)");
        }
        if (offset.hasLockedPitch()) {
            builder.newLine().yellow("  Pitch: ").white(offset.getPitch());
        } else {
            builder.newLine().yellow("  Pitch: ").green("Not set (free)");
        }
        builder.send(sender);
    }

    @PropertyParser(value="exitlocation", processPerCart = true)
    public ExitOffset parseLocation(PropertyParseContext<ExitOffset> context) {
        final Vector vec = Util.parseVector(context.input(), null);
        if (vec == null) {
            throw new PropertyInvalidInputException("Not a vector");
        }

        return ExitOffset.createAbsolute(vec,
                context.current().getYaw(),
                context.current().getPitch());
    }

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
        return ExitOffset.create(context.current().isAbsolute(),
                context.current().getPosition(),
                context.inputFloatOrNaN(),
                context.current().getPitch());
    }

    @PropertyParser(value="exitpitch", processPerCart = true)
    public ExitOffset parsePitch(PropertyParseContext<ExitOffset> context) {
        return ExitOffset.create(context.current().isAbsolute(),
                context.current().getPosition(),
                context.current().getYaw(),
                context.inputFloatOrNaN());
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
            new_pitch = Float.NaN;
        } else {
            new_yaw = Float.NaN;
            new_pitch = Float.NaN;
        }
        return ExitOffset.create(context.current().isAbsolute(),
                context.current().getPosition(),
                new_yaw, new_pitch);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_EXIT_OFFSET.has(sender);
    }

    @Override
    public ExitOffset getDefault() {
        return ExitOffset.DEFAULT;
    }

    @Override
    public Optional<ExitOffset> readFromConfig(ConfigurationNode config) {
        if (config.contains("exitOffset") || config.contains("exitYaw") || config.contains("exitPitch")) {
            Vector absoluteCoords = config.get("exitLocation", Vector.class, null);
            Vector offset = (absoluteCoords == null) ? config.get("exitOffset", new Vector()) : null;
            float yaw = config.get("exitYaw", Float.NaN);
            float pitch = config.get("exitPitch", Float.NaN);
            if (!config.get("exitYawLocked", false)) {
                yaw = Float.NaN;
            }
            if (!config.get("exitPitchLocked", false)) {
                pitch = Float.NaN;
            }
            if (absoluteCoords != null) {
                return Optional.of(ExitOffset.createAbsolute(absoluteCoords, yaw, pitch));
            } else {
                return Optional.of(ExitOffset.create(offset, yaw, pitch));
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<ExitOffset> value) {
        if (value.isPresent()) {
            ExitOffset data = value.get();
            if (data.isAbsolute()) {
                config.set("exitLocation", data.getPosition());
                config.remove("exitOffset");
            } else {
                config.set("exitOffset", data.getPosition());
                config.remove("exitLocation");
            }
            if (data.hasLockedYaw()) {
                config.set("exitYawLocked", true);
                config.set("exitYaw", data.getYaw());
            } else {
                config.set("exitYawLocked", false);
                config.set("exitYaw", 0.0f);
            }
            if (data.hasLockedPitch()) {
                config.set("exitPitchLocked", true);
                config.set("exitPitch", data.getPitch());
            } else {
                config.set("exitPitchLocked", false);
                config.set("exitPitch", 0.0f);
            }
        } else {
            config.remove("exitOffset");
            config.remove("exitLocation");
            config.remove("exitYaw");
            config.remove("exitYawLocked");
            config.remove("exitPitch");
            config.remove("exitPitchLocked");
        }
    }
}
