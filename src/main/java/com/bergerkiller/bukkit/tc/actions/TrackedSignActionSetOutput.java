package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.registry.ActionRegistry;
import com.bergerkiller.bukkit.tc.controller.components.ActionTracker;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Similar to {@link BlockActionSetLevers} but also supports fake signs with their
 * own output mechanics.
 */
public class TrackedSignActionSetOutput extends Action {
    private final TrainCarts traincarts;
    private final TrackedSign sign;
    private final boolean output;

    public TrackedSignActionSetOutput(TrainCarts traincarts, TrackedSign sign, boolean output) {
        this.traincarts = traincarts;
        this.sign = sign;
        this.output = output;
    }

    public TrackedSign getSign() {
        return sign;
    }

    public boolean getOutput() {
        return output;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    @Override
    public void start() {
        if (!this.sign.isRemoved()) {
            this.sign.setOutput(output);
        }
    }

    public static class Serializer implements ActionRegistry.Serializer<TrackedSignActionSetOutput> {
        private final TrainCarts plugin;

        public Serializer(TrainCarts plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean save(TrackedSignActionSetOutput action, OfflineDataBlock data, ActionTracker tracker) throws IOException {
            final byte[] signData = plugin.getTrackedSignLookup().serializeUniqueKey(action.getSign().getUniqueKey());
            if (signData == null) {
                return false;
            }

            data.addChild("sign-output", stream -> {
                Util.writeByteArray(stream, signData);
                stream.writeBoolean(action.getOutput());
            });
            return true;
        }

        @Override
        public TrackedSignActionSetOutput load(OfflineDataBlock data, ActionTracker tracker) throws IOException {
            final TrackedSign sign;
            final boolean output;
            try (DataInputStream stream = data.findChildOrThrow("sign-output").readData()) {
                Object uniqueKey = plugin.getTrackedSignLookup().deserializeUniqueKey(Util.readByteArray(stream));
                if (uniqueKey == null) {
                    throw new IllegalStateException("Sign unique key is not understood");
                }
                sign = plugin.getTrackedSignLookup().getTrackedSign(uniqueKey);
                if (sign == null) {
                    throw new IllegalStateException("Sign [" + uniqueKey + "] is missing");
                }
                output = stream.readBoolean();
            }
            return new TrackedSignActionSetOutput(plugin, sign, output);
        }
    }
}
