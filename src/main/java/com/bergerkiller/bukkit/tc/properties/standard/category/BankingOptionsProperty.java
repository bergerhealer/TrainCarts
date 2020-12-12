package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;

/**
 * Configures whether a train rolls inwards when taking (sharp) turns.
 */
public final class BankingOptionsProperty extends FieldBackedStandardTrainProperty<BankingOptions> {

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
