package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.components.AnimationController;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import cloud.commandframework.context.CommandContext;

/**
 * Suggests animation names used in a train or cart
 */
public final class AnimationName implements BiFunction<CommandContext<CommandSender>, String, List<String>> {
    public static final AnimationName TRAIN_ANIMATION_NAME = new AnimationName(true);
    public static final AnimationName CART_ANIMATION_NAME = new AnimationName(false);

    private final boolean forTrain;

    private AnimationName(boolean forTrain) {
        this.forTrain = forTrain;
    }

    @Override
    public List<String> apply(CommandContext<CommandSender> context, String input) {
        AnimationController holder;

        // Get list of animation names for the train or cart
        // May throw if permissions aren't set right for the player, return empty list then
        try {
            if (forTrain) {
                TrainProperties properties = context.inject(TrainProperties.class).get();
                holder = properties.getHolder();
            } else {
                CartProperties properties = context.inject(CartProperties.class).get();
                holder = properties.getHolder();
            }
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }

        // If no holder, is not loaded
        if (holder == null) {
            return Collections.emptyList();
        }

        // Get all animation names defined, if result list is empty, try the default TC animation names
        List<String> filtered = holder.GetAnimationNames().stream()
                .filter(name -> name.startsWith(input))
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            return filtered;
        }

        // Default names
        return new ArrayList<String>(TCConfig.defaultAnimations.keySet());
    }
}
