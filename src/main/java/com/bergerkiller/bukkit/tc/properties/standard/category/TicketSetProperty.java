package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.PropertySelectorCondition;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.tickets.Ticket;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Stores a set of tickets that a player can use to enter a train.
 * If empty, no tickets are required.
 */
public final class TicketSetProperty implements ITrainProperty<Set<String>> {

    @CommandTargetTrain
    @PropertyCheckPermission("ticket")
    @CommandMethod("train ticket list assigned")
    @CommandDescription("Displays the ticket names assigned to the train")
    private void getTrainTickets(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        Set<String> tickets = properties.getTickets();
        if (!tickets.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Train has tickets: " + ChatColor.WHITE
                    + StringUtil.combineNames(tickets));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train has tickets: "
                    + ChatColor.RED + "None");
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("ticket")
    @CommandMethod("train ticket assign <ticket>")
    @CommandDescription("Assigns the ticket with the given name to the train")
    private void assignTrainTicket(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("ticket") Ticket ticket
    ) {
        properties.addTicket(ticket.getName());
        sender.sendMessage(ChatColor.GREEN + "Ticket '" + ticket.getName() + "' assigned to train '" +
                properties.getTrainName() + "'!");
    }

    @PropertyCheckPermission("ticket")
    @CommandMethod("train ticket assign")
    @CommandDescription("Assigns the currently-edited ticket to the train")
    private void assignEditedTrainTicket(
            final Player sender,
            final TrainProperties properties,
            final Ticket ticket
    ) {
        assignTrainTicket(sender, properties, ticket);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("ticket")
    @CommandMethod("train ticket unassign <ticket>")
    @CommandDescription("Un-assigns the ticket with the given name from the train")
    private void unassignTrainTicket(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("ticket") Ticket ticket
    ) {
        if (properties.getTickets().contains(ticket.getName())) {
            properties.removeTicket(ticket.getName());
            sender.sendMessage(ChatColor.GREEN + "Ticket '" + ticket.getName() + "' un-assigned from train '" +
                    properties.getTrainName() + "'!");
        } else {
            sender.sendMessage(ChatColor.RED + "Ticket '" + ticket.getName() + "' was not assigned");
        }
    }

    @PropertyCheckPermission("ticket")
    @CommandMethod("train ticket unassign")
    @CommandDescription("Un-assigns the currently-edited ticket from the train")
    private void unassignEditedTrainTicket(
            final Player sender,
            final TrainProperties properties,
            final Ticket ticket
    ) {
        unassignTrainTicket(sender, properties, ticket);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("ticket")
    @CommandMethod("train ticket clearassigned")
    @CommandDescription("Un-assigns all tickets currently assigned to a train")
    private void clearAssignedTickets(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.clearTickets();
        sender.sendMessage(ChatColor.YELLOW + "Tickets cleared of train '" + properties.getTrainName() + "'");
    }

    @PropertyParser("setticket|tickets set")
    public Set<String> parseSet(String input) {
        return input.isEmpty() ? Collections.emptySet() : Collections.singleton(input);
    }

    @PropertyParser("clrticket|cleartickets|tickets clear")
    public Set<String> parseClear(String input) {
        return Collections.emptySet();
    }

    @PropertyParser(value = "addticket|tickets add", processPerCart = true)
    public Set<String> parseAdd(PropertyParseContext<Set<String>> context) {
        if (context.input().isEmpty() || context.current().contains(context.input())) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.add(context.input());
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @PropertyParser(value = "remticket|tickets rem|tickets remove", processPerCart = true)
    public Set<String> parseRemove(PropertyParseContext<Set<String>> context) {
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.remove(context.input());
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @PropertySelectorCondition("ticket")
    public boolean selectorMatchesAnyTicket(TrainProperties properties, SelectorCondition condition) {
        return condition.matchesAnyText(get(properties));
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_TICKETS.has(sender);
    }

    @Override
    public Set<String> getDefault() {
        return Collections.emptySet();
    }

    @Override
    public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
        return Util.getConfigStringSetOptional(config, "tickets");
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
        Util.setConfigStringCollectionOptional(config, "tickets", value);
    }
}
