package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Configures whether a train rolls inwards when taking (sharp) turns.
 */
public final class BankingOptionsProperty extends FieldBackedStandardTrainProperty<BankingOptions> {

    @CommandTargetTrain
    @PropertyCheckPermission("banking")
    @CommandMethod("train banking <strength> <smoothness>")
    @CommandDescription("Sets a new train banking strength and smoothness")
    private void trainSetBanking(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("strength") double strength,
            final @Argument("smoothness") double smoothness
    ) {
        properties.setBanking(strength, smoothness);
        trainGetBankingInfo(sender, properties);
    }

    @CommandMethod("train banking")
    @CommandDescription("Displays the current train banking settings")
    private void trainGetBankingInfo(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.getBankingStrength() == 0.0) {
            sender.sendMessage(ChatColor.YELLOW + "Train banking "
                    + ChatColor.RED + "is inactive. " + ChatColor.YELLOW + "Change strength to enable.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train banking "
                    + ChatColor.BLUE + "strength " + ChatColor.WHITE + properties.getBankingStrength()
                    + ChatColor.BLUE + " smoothness " + ChatColor.WHITE + properties.getBankingSmoothness());
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("banking")
    @CommandMethod("train banking strength <strength>")
    @CommandDescription("Sets a new train banking strength")
    private void trainSetBankingStrength(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("strength") double strength
    ) {
        properties.setBankingStrength(strength);
        trainGetBankingStrength(sender, properties);
    }

    @CommandMethod("train banking strength")
    @CommandDescription("Displays the currently configured train banking strength")
    private void trainGetBankingStrength(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.getBankingStrength() == 0.0) {
            sender.sendMessage(ChatColor.YELLOW + "Train banking strength: "
                    + ChatColor.WHITE + "0 " + ChatColor.RED + "(Inactive)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train banking strength: "
                    + ChatColor.WHITE + properties.getBankingStrength());
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("banking")
    @CommandMethod("train banking smoothness <strength>")
    @CommandDescription("Sets a new train banking smoothness")
    private void trainSetBankingSmoothness(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("strength") double strength
    ) {
        properties.setBankingSmoothness(strength);
        trainGetBankingSmoothness(sender, properties);
    }

    @CommandMethod("train banking smoothness")
    @CommandDescription("Displays the currently configured train banking smoothness")
    private void trainGetBankingSmoothness(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Train banking smoothness: "
                + ChatColor.WHITE + properties.getBankingSmoothness());
    }

    @PropertyParser("banking")
    public BankingOptions parseBanking(PropertyParseContext<BankingOptions> context) {
        String[] args = context.input().trim().split(" ");
        double newStrength, newSmoothness;
        if (args.length >= 2) {
            newStrength = ParseUtil.parseDouble(args[0], Double.NaN);
            newSmoothness = ParseUtil.parseDouble(args[1], Double.NaN);
        } else {
            newStrength = ParseUtil.parseDouble(context.input(), Double.NaN);
            newSmoothness = context.current().smoothness();
        }
        if (Double.isNaN(newStrength)) {
            throw new PropertyInvalidInputException("Banking strength is not a number");
        }
        if (Double.isNaN(newSmoothness)) {
            throw new PropertyInvalidInputException("Banking smoothness is not a number");
        }
        return BankingOptions.create(newStrength, newSmoothness);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_BANKING.has(sender);
    }

    @Override
    public BankingOptions getDefault() {
        return BankingOptions.DEFAULT;
    }

    @Override
    public BankingOptions getData(TrainInternalData data) {
        return data.bankingOptionsData;
    }

    @Override
    public void setData(TrainInternalData data, BankingOptions value) {
        data.bankingOptionsData = value;
    }

    @Override
    public Optional<BankingOptions> readFromConfig(ConfigurationNode config) {
        if (!config.isNode("banking")) {
            return Optional.empty();
        }

        ConfigurationNode banking = config.getNode("banking");
        return Optional.of(BankingOptions.create(
                banking.get("strength", 0.0),
                banking.get("smoothness", 0.0)
        ));
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<BankingOptions> value) {
        if (value.isPresent()) {
            BankingOptions data = value.get();
            ConfigurationNode banking = config.getNode("banking");
            banking.set("strength", data.strength());
            banking.set("smoothness", data.smoothness());
        } else {
            config.remove("banking");
        }
    }
}
