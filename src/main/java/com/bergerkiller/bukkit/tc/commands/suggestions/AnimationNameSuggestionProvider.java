package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.components.AnimationController;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Suggests animation names used in a train or cart
 */
public final class AnimationNameSuggestionProvider implements BlockingSuggestionProvider.Strings<CommandSender> {
    public static final AnimationNameSuggestionProvider TRAIN_ANIMATION_NAME = new AnimationNameSuggestionProvider(true);
    public static final AnimationNameSuggestionProvider CART_ANIMATION_NAME = new AnimationNameSuggestionProvider(false);

    private final boolean forTrain;

    private AnimationNameSuggestionProvider(boolean forTrain) {
        this.forTrain = forTrain;
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> context, @NonNull CommandInput commandInput) {
        String input = commandInput.lastRemainingToken();
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
        List<String> filtered = holder.getAnimationNames().stream()
                .filter(name -> name.startsWith(input))
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            return filtered;
        }

        // Default names
        return new ArrayList<String>(TCConfig.defaultAnimations.keySet());
    }
}
