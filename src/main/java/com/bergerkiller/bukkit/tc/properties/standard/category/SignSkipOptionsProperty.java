package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;

/**
 * Persistently stores the sign-skipping state of carts and trains. This makes sure
 * that when trains despawn and respawn, or when the server restarts, they
 * keep ignoring signs they were configured to ignore.
 */
public final class SignSkipOptionsProperty extends FieldBackedProperty<SignSkipOptions> {

    @Override
    public SignSkipOptions getDefault() {
        return SignSkipOptions.NONE;
    }

    @Override
    public Optional<SignSkipOptions> readFromConfig(ConfigurationNode config) {
        if (!config.isNode("skipOptions")) {
            return Optional.empty();
        }

        ConfigurationNode skipOptions = config.getNode("skipOptions");
        int ignoreCtr = skipOptions.get("ignoreCtr", 0);
        int skipCtr = skipOptions.get("skipCtr", 0);
        String filter = skipOptions.get("filter", "");
        Set<BlockLocation> signs = Collections.emptySet();
        if (skipOptions.contains("signs")) {
            List<String> signLocationNames = skipOptions.getList("signs", String.class);
            if (!signLocationNames.isEmpty()) {
                signs = new LinkedHashSet<BlockLocation>(signLocationNames.size());
                for (String signLocationName : signLocationNames) {
                    BlockLocation loc = BlockLocation.parseLocation(signLocationName);
                    if (loc != null) {
                        signs.add(loc);
                    }
                }
                signs = Collections.unmodifiableSet(signs);
            }
        }

        return Optional.of(SignSkipOptions.create(ignoreCtr, skipCtr, filter, signs));
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<SignSkipOptions> value) {
        if (value.isPresent()) {
            SignSkipOptions data = value.get();
            ConfigurationNode skipOptions = config.getNode("skipOptions");
            skipOptions.set("ignoreCtr", data.ignoreCounter());
            skipOptions.set("skipCtr", data.skipCounter());
            skipOptions.set("filter", data.filter());

            // Save signs in an offline friendly format
            if (data.hasSkippedSigns()) {
                List<String> signs = skipOptions.getList("signs", String.class);
                Iterator<BlockLocation> signBlockIter = data.skippedSigns().iterator();

                int num_signs = 0;
                while (signBlockIter.hasNext()) {
                    if (num_signs >= signs.size()) {
                        signs.add(signBlockIter.next().toString());
                    } else {
                        signs.set(num_signs, signBlockIter.next().toString());
                    }
                    ++num_signs;
                }
                while (signs.size() > num_signs) {
                    signs.remove(signs.size()-1);
                }
            } else {
                skipOptions.remove("signs");
            }
        } else {
            config.remove("skipOptions");
        }
    }

    @Override
    public SignSkipOptions get(CartProperties properties) {
        return CartInternalData.get(properties).signSkipOptionsData;
    }

    @Override
    public void set(CartProperties properties, SignSkipOptions value) {
        if (value.equals(SignSkipOptions.NONE)) {
            CartInternalData.get(properties).signSkipOptionsData = SignSkipOptions.NONE;
            this.writeToConfig(properties.getConfig(), Optional.empty());
        } else {
            CartInternalData.get(properties).signSkipOptionsData = value;
            this.writeToConfig(properties.getConfig(), Optional.of(value));
        }
    }

    @Override
    public SignSkipOptions get(TrainProperties properties) {
        return TrainInternalData.get(properties).signSkipOptionsData;
    }

    @Override
    public void set(TrainProperties properties, SignSkipOptions value) {
        if (value.equals(SignSkipOptions.NONE)) {
            TrainInternalData.get(properties).signSkipOptionsData = SignSkipOptions.NONE;
            this.writeToConfig(properties.getConfig(), Optional.empty());
        } else {
            TrainInternalData.get(properties).signSkipOptionsData = value;
            this.writeToConfig(properties.getConfig(), Optional.of(value));
        }
    }

    @Override
    public void onConfigurationChanged(CartProperties properties) {
        Optional<SignSkipOptions> opt = this.readFromConfig(properties.getConfig());
        CartInternalData.get(properties).signSkipOptionsData = opt.isPresent() ? opt.get() : SignSkipOptions.NONE;
    }

    @Override
    public void onConfigurationChanged(TrainProperties properties) {
        Optional<SignSkipOptions> opt = this.readFromConfig(properties.getConfig());
        TrainInternalData.get(properties).signSkipOptionsData = opt.isPresent() ? opt.get() : SignSkipOptions.NONE;
    }
}
