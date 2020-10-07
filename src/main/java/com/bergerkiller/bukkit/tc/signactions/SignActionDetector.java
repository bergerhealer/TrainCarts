package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.detector.DetectorSignPair;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SignActionDetector extends SignAction {
    private static boolean hasChanges = false;
    public static final SignActionDetector INSTANCE = new SignActionDetector();
    private final BlockMap<DetectorSignPair> detectors = new BlockMap<>();

    @Override
    public boolean match(SignActionEvent info) {
        return info != null && info.getMode() != SignActionMode.NONE && info.isType("detect");
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

    /**
     * Loads all detector sign regions from the state file.
     * 
     * @param filename of the file to load the state information from
     */
    public void init(String filename) {
        detectors.clear();
        new DataReader(filename) {
            public void read(DataInputStream stream) throws IOException {
                for (int count = stream.readInt(); count > 0; --count) {
                    //get required info
                    UUID id = StreamUtil.readUUID(stream);
                    //init a new detector
                    DetectorSignPair det = DetectorSignPair.read(stream);
                    //register
                    det.region = DetectorRegion.getRegion(id);
                    if (det.region == null) continue;
                    det.region.register(det);
                    detectors.put(det.region.getWorldName(), det.sign1.getLocation(), det);
                    detectors.put(det.region.getWorldName(), det.sign2.getLocation(), det);
                }
            }
        }.read();
        hasChanges = false;
    }

    /**
     * Saves all detector sign regions to a state file
     * 
     * @param filename of the file to save the state information to
     */
    public void save(boolean autosave, String filename) {
        if (autosave && !hasChanges) {
            return;
        }
        new DataWriter(filename) {
            public void write(DataOutputStream stream) throws IOException {
                Set<DetectorSignPair> detectorset = new HashSet<>(detectors.size() / 2);
                for (DetectorSignPair dec : detectors.values()) {
                    detectorset.add(dec);
                }
                stream.writeInt(detectorset.size());
                for (DetectorSignPair det : detectorset) {
                    StreamUtil.writeUUID(stream, det.region.getUniqueId());
                    det.write(stream);
                }
            }
        }.write();
        hasChanges = false;
    }

    @Override
    public void execute(SignActionEvent info) {
        //nothing happens here, relies on rail detector events

        // I lied! We have to double-check a detector region for this detector sign exists
        // Just in case data is corrupted, it can be restored by the first train driving over the detector
        if (info.getAction().isRedstone() || info.isAction(SignActionType.GROUP_ENTER)) {
            handlePlacement(info, false);
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

        //try to create the other sign
        if (!event.hasRails()) {
            event.getPlayer().sendMessage(ChatColor.RED + "No rails are nearby: This detector sign has not been activated!");
            return true;
        }
        if (!handlePlacement(event, true)) {
            event.getPlayer().sendMessage(ChatColor.RED + "Failed to find a second detector sign: No region set.");
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Place a second connected detector sign to finish this region!");
            return true;
        }
        event.getPlayer().sendMessage(ChatColor.GREEN + "A second detector sign was found: Region set.");
        return true;
    }

    @Override
    public void destroy(SignActionEvent info) {
        Block at = info.getBlock();
        DetectorSignPair dec = detectors.get(at);
        if (dec != null) {
            detectors.remove(at.getWorld(), dec.sign1.getLocation());
            detectors.remove(at.getWorld(), dec.sign2.getLocation());
            dec.region.remove();
            hasChanges = true;
        }
    }

    private boolean handlePlacement(SignActionEvent event, boolean signBuilt) {
        if (!event.hasRails()) {
            return false;
        }
        Block startsign = event.getBlock();
        Block startrails = event.getRails();
        BlockFace dir = event.getFacing();
        String label = getLabel(event);
        if (!tryBuild(label, startrails, startsign, dir, signBuilt)) {
            if (!tryBuild(label, startrails, startsign, FaceUtil.rotate(dir, 2), signBuilt)) {
                if (!tryBuild(label, startrails, startsign, FaceUtil.rotate(dir, -2), signBuilt)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean tryBuild(String label, Block startrails, Block startsign, BlockFace direction, boolean signBuilt) {
        DetectorSignPair detector = null;
        if (!signBuilt) {
            detector = detectors.get(startsign);
        }
        if (detector == null) {
            detector = createPair(label, startrails, startsign, direction);
        }
        return detector != null;
    }

    private DetectorSignPair createPair(String label, Block startrails, Block startsign, BlockFace direction) {
        final TrackMap map = new TrackMap(startrails, direction, TCConfig.maxDetectorLength);
        map.next();
        //now try to find the end rails : find the other sign
        Block endsign;
        SignActionEvent info;
        while (map.hasNext()) {
            for (Block signblock : Util.getSignsFromRails(map.next())) {
                if (signblock.equals(startsign)) {
                    continue;
                }
                info = new SignActionEvent(signblock);
                if (matchLabel(info, label)) {
                    endsign = signblock;

                    //start and end found : add it
                    final DetectorSignPair detector = new DetectorSignPair(startsign, endsign);
                    detectors.put(startsign, detector);
                    detectors.put(endsign, detector);
                    hasChanges = true;
                    CommonUtil.nextTick(new Runnable() {
                        public void run() {
                            DetectorRegion region = DetectorRegion.create(map);
                            region.register(detector);
                            region.detectMinecarts();
                        }
                    });
                    return detector;
                }
            }
        }
        return null;
    }
}
