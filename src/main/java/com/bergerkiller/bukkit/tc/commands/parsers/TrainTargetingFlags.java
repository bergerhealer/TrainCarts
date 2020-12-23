package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainNearbyException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainSelectedException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

import cloud.commandframework.Command;
import cloud.commandframework.Command.Builder;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.flags.CommandFlag;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.bukkit.parsers.WorldArgument;
import cloud.commandframework.context.CommandContext;

/**
 * The flags added at the end of commands to target trains or individual carts
 */
public class TrainTargetingFlags implements BiFunction<CommandTargetTrain, Command.Builder<CommandSender>, Command.Builder<CommandSender>> {
    public static final TrainTargetingFlags INSTANCE = new TrainTargetingFlags();

    private final CommandFlag<TrainProperties> flagTrain = CommandFlag.newBuilder("train")
            .withArgument(CommandArgument.<CommandSender, TrainProperties>ofType(TrainProperties.class, "train_name")
                    .withParser(new TrainFlagParser()))
            .build();
    private final CommandFlag<CartSelectorResult> flagCart = CommandFlag.newBuilder("cart")
            .withArgument(CommandArgument.<CommandSender, CartSelectorResult>ofType(CartSelectorResult.class, "cart_uuid")
                    .withParser(new CartFlagParser()))
            .build();

    private final CommandFlag<World> flagWorld = CommandFlag.newBuilder("world")
            .withArgument(WorldArgument.of("world_name"))
            .build();

    private final CommandFlag<NearPosition> flagNear = CommandFlag.newBuilder("near")
            .withArgument(NearPosition.asArgument("where"))
            .build();

    // Make private
    private TrainTargetingFlags() {
    }

    public boolean isTrainTargetingFlag(CommandFlag<?> flag) {
        return flag == flagTrain
                || flag == flagCart
                || flag == flagWorld
                || flag == flagNear;
    }

    @Override
    public Builder<CommandSender> apply(CommandTargetTrain annotation, Builder<CommandSender> builder) {
        builder = builder.flag(flagTrain).flag(flagCart);
        builder = builder.flag(flagWorld).flag(flagNear);
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
        if (context.flags().hasFlag(flagTrain.getName())) {
            trainProperties = context.flags().get(flagTrain.getName());
            if (!trainProperties.isEmpty()) {
                cartProperties = trainProperties.get(0);
            }
        }

        // Process --cart selector
        if (context.flags().hasFlag(flagCart.getName())) {
            CartSelectorResult cartSelector = context.flags().get(flagCart.getName());
            if (cartSelector.cart_result != null) {
                // If --train was used to set a train, disallow selecting a cart not from that train
                if (trainProperties != null && trainProperties != cartSelector.cart_result.getTrainProperties()) {
                    throw new LocalizedParserException(context,
                            Localization.COMMAND_CART_NOT_FOUND_IN_TRAIN,
                            "uuid=" + cartSelector.cart_result.getUUID().toString());
                }

                cartProperties = cartSelector.cart_result;
                trainProperties = cartProperties.getTrainProperties();
            } else {
                // Cart by number in the selected train
                // If no --train was specified, assume the currently editing train is meant
                if (trainProperties == null && context.getSender() instanceof Player) {
                    CartProperties editing = CartPropertiesStore.getEditing((Player) context.getSender());
                    if (editing != null) {
                        trainProperties = editing.getTrainProperties();
                    }
                }
                if (trainProperties == null) {
                    throw new NoTrainSelectedException();
                }

                // Check in range
                int indexInCart = cartSelector.index_in_train < 0
                        ? (trainProperties.size() + cartSelector.index_in_train) : cartSelector.index_in_train;
                if (indexInCart >= 0 && indexInCart < trainProperties.size()) {
                    cartProperties = trainProperties.get(indexInCart);
                } else {
                    throw new LocalizedParserException(context,
                            Localization.COMMAND_CART_NOT_FOUND_IN_TRAIN,
                            "index=" + cartSelector.index_in_train);
                }
            }
        }

        // For --near world and to filter results from global selectors
        World atWorld = context.flags().getValue(flagWorld.getName(), null);

        // Process --near to find carts nearby
        if (context.flags().hasFlag(flagNear.getName())) {
            // Perms
            Permission.COMMAND_TARGET_NEAR.handle(context.getSender());

            final NearPosition near = context.flags().get(flagNear.getName());
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
        if (cartProperties == null && trainProperties == null && context.getSender() instanceof Player) {
            cartProperties = CartPropertiesStore.getEditing((Player) context.getSender());
            if (cartProperties != null) {
                trainProperties = cartProperties.getTrainProperties();
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

    /**
     * Parses the --cart [uuid] or --cart [num] flag
     */
    private static class CartFlagParser implements ArgumentParser<CommandSender, CartSelectorResult> {
        @Override
        public ArgumentParseResult<CartSelectorResult> parse(
                final CommandContext<CommandSender> commandContext,
                final Queue<String> inputQueue
        ) {
            String uuidName = inputQueue.poll();

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
                    return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                            Localization.COMMAND_CART_NOT_FOUND_BY_UUID, uuid.toString()));
                }

                return ArgumentParseResult.success(new CartSelectorResult(prop));
            } catch (IllegalArgumentException ex) {
                return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                        Localization.COMMAND_CART_NOT_FOUND_BY_UUID, uuidName));
            }
        }

        @Override
        public List<String> suggestions(
                final CommandContext<CommandSender> commandContext,
                final String input
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

    /**
     * Parses the --train name flag
     */
    private static class TrainFlagParser implements ArgumentParser<CommandSender, TrainProperties> {
        @Override
        public ArgumentParseResult<TrainProperties> parse(
                final CommandContext<CommandSender> commandContext,
                final Queue<String> inputQueue
        ) {
            String trainName = inputQueue.poll();
            if (!TrainPropertiesStore.exists(trainName)) {
                return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                        Localization.COMMAND_TRAIN_NOT_FOUND, trainName));
            }

            TrainProperties properties = TrainPropertiesStore.get(trainName);
            commandContext.set("trainProperties", properties);
            return ArgumentParseResult.success(properties);
        }

        @Override
        public List<String> suggestions(
                final CommandContext<CommandSender> commandContext,
                final String input
        ) {
            return TrainPropertiesStore.getAll().stream()
                    .map(TrainProperties::getTrainName)
                    .collect(Collectors.toList());
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
