package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.suggestions.TrainNameSuggestionProvider;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainNearbyException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainSelectedException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command.Builder;
import org.incendo.cloud.annotations.BuilderModifier;
import org.incendo.cloud.bukkit.parser.WorldParser;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

/**
 * The flags added at the end of commands to target trains or individual carts
 */
public class TrainTargetingFlags implements BuilderModifier<CommandTargetTrain, CommandSender> {
    public static final TrainTargetingFlags INSTANCE = new TrainTargetingFlags();

    private final CommandFlag<TrainProperties> flagTrain = CommandFlag.<CommandSender>builder("train")
            .withComponent(CommandComponent.builder("train_name", trainFlagParser()))
            .build();

    private final CommandFlag<CartSelectorResult> flagCart = CommandFlag.<CommandSender>builder("cart")
            .withComponent(CommandComponent.builder("cart_uuid", cartFlagParser()))
            .build();

    private final CommandFlag<World> flagWorld = CommandFlag.<CommandSender>builder("world")
            .withComponent(CommandComponent.builder("world_name", WorldParser.worldParser()))
            .build();

    private final CommandFlag<NearPosition> flagNear = CommandFlag.<CommandSender>builder("near")
            .withComponent(CommandComponent.builder("where", NearPosition.nearParser()))
            .build();

    private final CommandFlag<Void> flagNearest = CommandFlag.<CommandSender>builder("nearest")
            .build();

    // Make private
    private TrainTargetingFlags() {
    }

    public boolean isTrainTargetingFlag(CommandFlag<?> flag) {
        return flag == flagTrain
                || flag == flagCart
                || flag == flagWorld
                || flag == flagNear
                || flag == flagNearest;
    }

    @Override
    public Builder<? extends CommandSender> modifyBuilder(@NonNull CommandTargetTrain annotation, Builder<CommandSender> builder) {
        builder = builder.flag(flagTrain).flag(flagCart);
        builder = builder.flag(flagWorld).flag(flagNear).flag(flagNearest);
        return builder;
    }

    /**
     * Checks the command context to figure out what cart was selected.
     * Flags overrule the 'currently edited' cart behavior.
     * 
     * @param context Input command context
     * @return cart properties that are targeted
     */
    public CartProperties findCartProperties(CommandContext<CommandSender> context) {
        TrainProperties trainProperties = null;
        CartProperties cartProperties = null;

        // Process --train selector
        if (context.flags().hasFlag(flagTrain.name())) {
            trainProperties = context.flags().get(flagTrain.name());
            if (!trainProperties.isEmpty()) {
                cartProperties = trainProperties.get(0);
            }
        }

        // For --near world and to filter results from global selectors
        World atWorld = context.flags().getValue(flagWorld.name(), null);

        // Process --near to find carts nearby
        if (context.flags().hasFlag(flagNear.name()) || context.flags().hasFlag(flagNearest.name())) {
            // Perms
            Permission.COMMAND_TARGET_NEAR.handle(context.sender());

            final NearPosition near;
            if (context.flags().hasFlag(flagNear.name())) {
                near = context.flags().get(flagNear.name());
            } else {
                ArgumentParseResult<NearPosition> parseResult = NearPosition.parseNearest(context);
                if (parseResult.failure().isPresent()) {
                    Throwable t = parseResult.failure().get();
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else {
                        return null; //Uhhhh....
                    }
                }
                near = parseResult.parsedValue().get();
            }
            if (atWorld != null) {
                near.at.setWorld(atWorld);
            }

            // Find all nearby entities at these coordinates
            List<Entity> nearby = WorldUtil.getNearbyEntities(near.at, near.radius, near.radius, near.radius);

            // Find the closest MinecartMember to this position, within radius
            final double distanceSquaredMax = (near.radius * near.radius);
            Stream<MemberResult> nearbyMembers = nearby.stream()
                    .map(MinecartMemberStore::getFromEntity)
                    .filter(Objects::nonNull)
                    .map(member -> new MemberResult(member, near.at))
                    .filter(r -> r.distanceSquared <= distanceSquaredMax);

            // If --train was specified, filter to within carts of that train
            if (trainProperties != null) {
                final TrainProperties inTrain = trainProperties;
                nearbyMembers = nearbyMembers.filter(r -> r.member.getProperties().getTrainProperties() == inTrain);
            }

            // Find the closest MinecartMember to this position
            Optional<MemberResult> result = nearbyMembers.sorted().findFirst();

            // If failed, show error
            if (result.isPresent()) {
                cartProperties = result.get().member.getProperties();
                trainProperties = cartProperties.getTrainProperties();
            } else {
                throw new NoTrainNearbyException();
            }
        }

        // If no cart was selected (and no train either), pick what the player is editing
        if (cartProperties == null && trainProperties == null && context.sender() instanceof Player) {
            cartProperties = context.inject(TrainCartsPlayer.class).get().getEditedCart();
            if (cartProperties != null) {
                trainProperties = cartProperties.getTrainProperties();
            }
        }

        // Process --cart selector last
        if (context.flags().hasFlag(flagCart.name())) {
            CartSelectorResult cartSelector = context.flags().get(flagCart.name());
            if (cartSelector.cart_result != null) {
                // If --train was used to set a train, disallow selecting a cart not from that train
                if (trainProperties != null && trainProperties != cartSelector.cart_result.getTrainProperties()) {
                    throw new CloudLocalizedException(context,
                            Localization.COMMAND_CART_NOT_FOUND_IN_TRAIN,
                            "uuid=" + cartSelector.cart_result.getUUID().toString());
                }

                cartProperties = cartSelector.cart_result;
                trainProperties = cartProperties.getTrainProperties();
            } else {
                // Cart by number in the selected train
                // For this, a train must have been selected somehow before.
                if (trainProperties == null) {
                    throw new NoTrainSelectedException();
                }

                // Check in range
                int indexInCart = cartSelector.index_in_train < 0
                        ? (trainProperties.size() + cartSelector.index_in_train) : cartSelector.index_in_train;
                if (indexInCart >= 0 && indexInCart < trainProperties.size()) {
                    MinecartGroup group = trainProperties.getHolder();
                    if (group == null) {
                        // Might not be the right order
                        cartProperties = trainProperties.get(indexInCart);
                    } else {
                        cartProperties = group.get(indexInCart).getProperties();
                    }
                } else {
                    throw new CloudLocalizedException(context,
                            Localization.COMMAND_CART_NOT_FOUND_IN_TRAIN,
                            "index=" + cartSelector.index_in_train);
                }
            }
        }

        // Make sure it is in the world
        if (cartProperties != null && atWorld != null) {
            BlockLocation loc = cartProperties.getLocation();
            if (loc == null || loc.getWorld() != atWorld) {
                throw new NoTrainSelectedException();
            }
        }

        // If at the end no cart was selected, throw
        if (cartProperties == null) {
            throw new NoTrainSelectedException();
        }

        return cartProperties;
    }

    private static @NotNull ParserDescriptor<CommandSender, CartSelectorResult> cartFlagParser() {
        return ParserDescriptor.of(new CartFlagParser(), CartSelectorResult.class);
    }

    /**
     * Parses the --cart [uuid] or --cart [num] flag
     */
    private static class CartFlagParser implements ArgumentParser<CommandSender, CartSelectorResult>, BlockingSuggestionProvider.Strings<CommandSender> {
        @Override
        public @NonNull ArgumentParseResult<@NonNull CartSelectorResult> parse(
                @NonNull CommandContext<@NonNull CommandSender> commandContext,
                @NonNull CommandInput commandInput
        ) {
            String uuidName = commandInput.readString();

            if (uuidName.equalsIgnoreCase("head")) {
                return ArgumentParseResult.success(new CartSelectorResult(0));
            } else if (uuidName.equalsIgnoreCase("tail")) {
                return ArgumentParseResult.success(new CartSelectorResult(-1));
            }

            // Try parse as number
            int numCart = ParseUtil.parseInt(uuidName, Integer.MIN_VALUE);
            if (numCart != Integer.MIN_VALUE) {
                return ArgumentParseResult.success(new CartSelectorResult(numCart));
            }

            // Try parse as UUID
            try {
                UUID uuid = UUID.fromString(uuidName);
                CartProperties prop = CartPropertiesStore.getByUUID(uuid);
                if (prop == null) {
                    return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                            Localization.COMMAND_CART_NOT_FOUND_BY_UUID, uuid.toString()));
                }

                return ArgumentParseResult.success(new CartSelectorResult(prop));
            } catch (IllegalArgumentException ex) {
                return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                        Localization.COMMAND_CART_NOT_FOUND_BY_UUID, uuidName));
            }
        }

        @Override
        public @NonNull Iterable<@NonNull String> stringSuggestions(
                @NonNull CommandContext<CommandSender> commandContext,
                @NonNull CommandInput input
        ) {
            if (input.isEmpty()) {
                return Stream.concat(Stream.of("<uuid>", "head", "tail"), IntStream.range(0, 10)
                        .mapToObj(Integer::toString))
                        .collect(Collectors.toList());
            } else {
                return IntStream.range(0, 10)
                        .mapToObj(Integer::toString)
                        .map(o -> input + o)
                        .collect(Collectors.toList());
            }
        }
    }

    private static @NotNull ParserDescriptor<CommandSender, TrainProperties> trainFlagParser() {
        return ParserDescriptor.of(new TrainFlagParser(), TrainProperties.class);
    }

    /**
     * Parses the --train name flag
     */
    private static class TrainFlagParser implements ArgumentParser<CommandSender, TrainProperties> {
        private final TrainNameSuggestionProvider suggestionProvider = new TrainNameSuggestionProvider();

        @Override
        public @NonNull ArgumentParseResult<@NonNull TrainProperties> parse(
                @NonNull CommandContext<@NonNull CommandSender> commandContext,
                @NonNull CommandInput commandInput
        ) {
            String trainName = commandInput.readString();
            TrainProperties properties = TrainPropertiesStore.get(trainName);
            if (properties == null) {
                properties = TrainPropertiesStore.getRelaxed(trainName);
            }
            if (properties == null) {
                return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                        Localization.COMMAND_TRAIN_NOT_FOUND, trainName));
            }

            commandContext.set("trainProperties", properties);
            return ArgumentParseResult.success(properties);
        }

        @Override
        public @NonNull SuggestionProvider<CommandSender> suggestionProvider() {
            return suggestionProvider;
        }
    }

    // Result of --cart, either UUID or index in train where negative values are from tail-end
    private static class CartSelectorResult {
        /** Exact result (matched by UUID) */
        public final CartProperties cart_result;
        /** Index in a required train argument */
        public final int index_in_train;

        public CartSelectorResult(int index) {
            this.cart_result = null;
            this.index_in_train = index;
        }

        public CartSelectorResult(CartProperties result) {
            this.cart_result = result;
            this.index_in_train = Integer.MAX_VALUE;
        }
    }

    // Member found near a position
    private static class MemberResult implements Comparable<MemberResult> {
        public final MinecartMember<?> member;
        public final double distanceSquared;

        public MemberResult(MinecartMember<?> member, Location at) {
            this.member = member;
            this.distanceSquared = member.getEntity().loc.distanceSquared(at);
        }

        @Override
        public int compareTo(MemberResult o) {
            return Double.compare(this.distanceSquared, o.distanceSquared);
        }
    }
}
