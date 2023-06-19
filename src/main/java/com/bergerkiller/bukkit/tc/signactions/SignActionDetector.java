package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSign;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignMetadataHandler;
import com.bergerkiller.bukkit.tc.offline.sign.OfflineSignStore;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.signactions.detector.DetectorSign;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SignActionDetector extends SignAction {
    public static final SignActionDetector INSTANCE = new SignActionDetector();

    /**
     * Called during loading of TrainCarts to register the detector sign
     * metadata handler
     *
     * @param plugin
     */
    public void enable(TrainCarts plugin) {
        plugin.getOfflineSigns().registerHandler(DetectorSign.Metadata.class, new OfflineSignMetadataHandler<DetectorSign.Metadata>() {
            @Override
            public int getMetadataVersion() {
                return 1;
            }

            @Override
            public void onUpdated(OfflineSignStore store, OfflineSign sign, DetectorSign.Metadata oldValue, DetectorSign.Metadata newValue) {
                // If owner changes something pretty bad is going on, perform a full re-add in that case
                // In other cases the sign owner already tracks it and there's nothing more to do
                if (oldValue.owner != newValue.owner) {
                    onUnloaded(store, sign, oldValue);

                    // Only do this when the new metadata has no owner associated with it yet
                    if (newValue.owner == null) {
                        onAdded(store, sign, newValue);
                    }
                }
            }

            @Override
            public void onAdded(OfflineSignStore store, OfflineSign sign, DetectorSign.Metadata metadata) {
                metadata.owner = new DetectorSign(store, sign, metadata);
                metadata.region.register(metadata.owner);
            }

            @Override
            public void onUnloaded(OfflineSignStore store, OfflineSign sign, DetectorSign.Metadata metadata) {
                DetectorSign prevOwner = metadata.owner;
                if (prevOwner != null) {
                    metadata.owner = null; // Mark as removed so no further store updates are done
                    metadata.region.unregister(prevOwner);

                    // We presume/hope that the other sign will also unload at the same time
                }
            }

            @Override
            public void onRemoved(OfflineSignStore store, OfflineSign sign, DetectorSign.Metadata metadata) {
                DetectorSign prevOwner = metadata.owner;
                if (prevOwner != null) {
                    metadata.owner = null; // Mark as removed so no further store updates are done
                    metadata.region.unregister(prevOwner);

                    if (!metadata.region.isRegistered()) {
                        metadata.region.remove(); // Remove once both signs/others are all gone
                    }

                    // See if theres metadata stored for the other sign, and if so, remove that one too
                    DetectorSign.Metadata otherMeta = store.get(metadata.otherSign, metadata.otherSignFront, DetectorSign.Metadata.class);
                    if (otherMeta != null && metadata.region == otherMeta.region) {
                        store.remove(metadata.otherSign, metadata.otherSignFront, DetectorSign.Metadata.class);
                    }
                }
            }

            @Override
            public void onEncode(DataOutputStream stream, OfflineSign sign, DetectorSign.Metadata value) throws IOException {
                value.otherSign.getPosition().write(stream);
                stream.writeBoolean(value.otherSignFront);
                StreamUtil.writeUUID(stream, value.region.getUniqueId());
                stream.writeBoolean(value.isLeverDown);
            }

            @Override
            public DetectorSign.Metadata onDecode(DataInputStream stream, OfflineSign sign) throws IOException {
                OfflineBlock otherSign = sign.getWorld().getBlockAt(IntVector3.read(stream));
                boolean otherSignFront = stream.readBoolean();
                DetectorRegion region = DetectorRegion.getRegion(StreamUtil.readUUID(stream));
                boolean isLeverDown = stream.readBoolean();

                if (region == null) {
                    throw new InvalidMetadataException();
                }

                return new DetectorSign.Metadata(otherSign, otherSignFront, region, isLeverDown);
            }

            @Override
            public DataMigrationDecoder<DetectorSign.Metadata> getMigrationDecoder(OfflineSign gsign, int gdataVersion) {
                if (gdataVersion == 0) {
                    // v0 didn't store a 'front' field for the other sign, which is needed on 1.20+
                    return (stream, sign, dataVersion) -> {
                        OfflineBlock otherSign = sign.getWorld().getBlockAt(IntVector3.read(stream));
                        DetectorRegion region = DetectorRegion.getRegion(StreamUtil.readUUID(stream));
                        boolean isLeverDown = stream.readBoolean();

                        if (region == null) {
                            throw new InvalidMetadataException();
                        }

                        return new DetectorSign.Metadata(otherSign, true, region, isLeverDown);
                    };
                } else {
                    return OfflineSignMetadataHandler.super.getMigrationDecoder(gsign, gdataVersion);
                }
            }
        });
    }

    /**
     * Called when TrainCarts disables to disable the detector sign
     * metadata handler
     *
     * @param plugin
     */
    public void disable(TrainCarts plugin) {
        plugin.getOfflineSigns().unregisterHandler(DetectorSign.Metadata.class);
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info != null && info.getMode() != SignActionMode.NONE && info.isType("detect");
    }

    @Override
    public boolean canSupportFakeSign(SignActionEvent info) {
        return false;
    }

    /**
     * Matches the sign to check that it is indeed a detector sign. If labels are used on either
     * sign, then the labels must match as well. If label is null, but the sign has a label, then
     * the signs do not match.
     * 
     * @param info sign information
     * @param label to match, null to not check for this label
     * @return True if the sign is a detector sign with the same label
     */
    public boolean matchLabel(SignActionEvent info, String label) {
        if (!match(info)) {
            return false;
        }
        String otherLabel = getLabel(info);
        return (label == null) ? (otherLabel == null) : label.equalsIgnoreCase(otherLabel);
    }

    /**
     * Reads the label put on the second line of the detector sign.
     * This label is used to uniquely pair two detector signs when multiple
     * exist on the same tracks.
     * 
     * @param info to read
     * @return detector sign label
     */
    public String getLabel(SignActionEvent info) {
        String data = info.getLine(1);
        int index = Util.minStringIndex(data.indexOf(' '), data.indexOf(':'));
        return (index == -1) ? null : data.substring(index + 1).trim();
    }

    @Override
    public void execute(SignActionEvent info) {
        //nothing happens here, relies on rail detector events

        // I lied! We have to double-check a detector region for this detector sign exists
        // Just in case data is corrupted, it can be restored by the first train driving over the detector
        if (info.getAction().isRedstone() || info.isAction(SignActionType.GROUP_ENTER)) {
            if (info.getTrainCarts().getOfflineSigns().get(info.getTrackedSign(), DetectorSign.Metadata.class) == null) {
                handlePlacement(info);
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!match(event)) {
            return false;
        }

        if (!SignBuildOptions.create()
                .setPermission(Permission.BUILD_DETECTOR)
                .setName("train detector")
                .setDescription("detect trains between this detector sign and another")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Detector")
                .handle(event.getPlayer()))
        {
            return false;
        }

        //must be a real sign
        if (!event.getTrackedSign().isRealSign()) {
            event.getPlayer().sendMessage(ChatColor.RED + "Detector signs must be placed using real signs");
            return false;
        }

        //try to create the other sign
        if (!event.hasRails()) {
            event.getPlayer().sendMessage(ChatColor.RED + "No rails are nearby: This detector sign has not been activated!");
            return true;
        }
        if (!handlePlacement(event)) {
            event.getPlayer().sendMessage(ChatColor.RED + "Failed to find a second detector sign: No region set.");
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Place a second connected detector sign to finish this region!");
            return true;
        }
        event.getPlayer().sendMessage(ChatColor.GREEN + "A second detector sign was found: Region set.");
        return true;
    }

    private boolean handlePlacement(SignActionEvent event) {
        if (!event.hasRails() || !event.getTrackedSign().isRealSign()) {
            return false;
        }
        RailLookup.TrackedRealSign startSign = (RailLookup.TrackedRealSign) event.getTrackedSign();
        Block startrails = event.getRails();
        BlockFace dir = event.getFacing();
        String label = getLabel(event);
        if (!tryBuild(event.getTrainCarts(), label, startrails, startSign, dir)) {
            if (!tryBuild(event.getTrainCarts(), label, startrails, startSign, FaceUtil.rotate(dir, 2))) {
                if (!tryBuild(event.getTrainCarts(), label, startrails, startSign, FaceUtil.rotate(dir, -2))) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean tryBuild(TrainCarts traincarts, String label, Block startrails, RailLookup.TrackedRealSign startSign, BlockFace direction) {
        final TrackMap map = new TrackMap(startrails, direction, TCConfig.maxDetectorLength);
        map.next();
        //now try to find the end rails : find the other sign
        TrackedSign endsign;
        SignActionEvent info;
        while (map.hasNext()) {
            map.next();
            for (TrackedSign sign : map.getRailPiece().signs()) {
                if (!sign.isRealSign() || sign.isRemoved()) {
                    continue;
                }
                if (sign.signBlock.equals(startSign.signBlock) &&
                        ((RailLookup.TrackedRealSign) sign).isFrontText() == startSign.isFrontText()
                ) {{
                    continue;
                }

                info = new SignActionEvent(sign);
                if (matchLabel(info, label)) {
                    endsign = sign;

                    // Create a new DetectorRegion using the path we found inbetween
                    DetectorRegion region = DetectorRegion.create(map);

                    // Register detector sign metadata for both start and end sign with this region
                    // The handler will initialize the rest (listeners, etc.)
                    OfflineSignStore store = traincarts.getOfflineSigns();
                    store.put(startSign, new DetectorSign.Metadata(endsign, region, false));
                    store.put(endsign, new DetectorSign.Metadata(startSign, region, false));

                    // Detect minecarts next-tick (don't want to do too much during this logic-)
                    CommonUtil.nextTick(() -> region.detectMinecarts());

                    return true;
                }
            }
        }

        return false;
    }
}
