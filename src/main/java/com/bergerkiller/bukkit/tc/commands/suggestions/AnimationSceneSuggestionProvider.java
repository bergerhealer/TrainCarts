package com.bergerkiller.bukkit.tc.commands.suggestions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
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
public final class AnimationSceneSuggestionProvider implements BlockingSuggestionProvider.Strings<CommandSender> {
    public static final AnimationSceneSuggestionProvider TRAIN_ANIMATION_SCENE = new AnimationSceneSuggestionProvider(true);
    public static final AnimationSceneSuggestionProvider CART_ANIMATION_SCENE = new AnimationSceneSuggestionProvider(false);

    private final boolean forTrain;

    private AnimationSceneSuggestionProvider(boolean forTrain) {
        this.forTrain = forTrain;
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> context, @NonNull CommandInput commandInput) {
        String input = commandInput.lastRemainingToken();

        // Animation name is input before the scene is specified
        String animationName = context.getOrDefault("animation_name", "");
        if (animationName.isEmpty()) {
            return Collections.emptyList();
        }

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
        List<String> filtered = holder.getAnimationScenes(animationName).stream()
                .filter(name -> name.startsWith(input))
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            return filtered;
        }

        // Default animation names have scenes too!
        Animation defaultAnim = TCConfig.defaultAnimations.get(animationName);
        return (defaultAnim == null) ? Collections.emptyList()
                : new ArrayList<String>(defaultAnim.getSceneNames());
    }
}
