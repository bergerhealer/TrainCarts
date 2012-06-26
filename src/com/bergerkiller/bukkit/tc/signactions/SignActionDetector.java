package com.bergerkiller.bukkit.tc.signactions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.BlockMap;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.common.config.DataReader;
import com.bergerkiller.bukkit.common.config.DataWriter;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.signactions.detector.DetectorSignPair;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionDetector extends SignAction {
	public static void removeDetector(Block at) {
		DetectorSignPair dec = detectors.get(at);
		if (dec != null) {
			detectors.remove(at.getWorld(), dec.sign1.signLocation);
			detectors.remove(at.getWorld(), dec.sign2.signLocation);
			dec.region.remove();
		}
	}
	private static BlockMap<DetectorSignPair> detectors = new BlockMap<DetectorSignPair>();

	public static boolean isValid(Sign sign) {
		if (sign == null) return false;
		return isValid(sign.getLines());
	}
	public static boolean isValid(String[] lines) {
		return SignActionMode.fromString(lines[0]) != SignActionMode.NONE &&
				lines[1].toLowerCase().startsWith("detector");
	}

	@Override
	public void execute(SignActionEvent info) {
		//nothing happens here, relies on rail detector events
	}

	public boolean tryBuild(Block startrails, Block startsign, BlockFace direction) {
		final TrackMap map = new TrackMap(startrails, direction, TrainCarts.maxDetectorLength);
		map.next();
		//now try to find the end rails : find the other sign
		Block endsign = null;
		Sign sign;
		while (map.hasNext()) {
			for (Block signblock : Util.getSignsFromRails(map.next())) {
				sign = BlockUtil.getSign(signblock);
				if (SignActionMode.fromSign(sign) != SignActionMode.NONE) {
					if (sign.getLine(1).toLowerCase().startsWith("detector")) {
						endsign = signblock;
						//start and end found : add it
						final DetectorSignPair detector = new DetectorSignPair(startsign, endsign);
						detectors.put(startsign, detector);
						detectors.put(endsign, detector);
						new Task(TrainCarts.plugin) {
							public void run() {
								DetectorRegion.create(map).register(detector);
							}
						}.start();
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (!isValid(event.getLines())) {
			return false;
		}
		if (handleBuild(event, Permission.BUILD_DETECTOR, "train detector", "detect trains between this detector sign and another")) {
			//try to create the other sign
			Block startsign = event.getBlock();
			Block startrails = Util.getRailsFromSign(startsign);
			if (startrails == null) {
				event.getPlayer().sendMessage(ChatColor.RED + "No rails are nearby: This detector sign has not been activated!");
				return true;
			}
			BlockFace dir = BlockUtil.getFacing(startsign);
			if (!tryBuild(startrails, startsign, dir)) {
				if (!tryBuild(startrails, startsign, FaceUtil.rotate(dir, 2))) {
					if (!tryBuild(startrails, startsign, FaceUtil.rotate(dir, -2))) {
						event.getPlayer().sendMessage(ChatColor.RED + "Failed to find a second detector sign: No region set.");
						event.getPlayer().sendMessage(ChatColor.YELLOW + "Place a second connected detector sign to finish this region!");
						return true;
					}
				}
			}
			event.getPlayer().sendMessage(ChatColor.GREEN + "A second detector sign was found: Region set.");
			return true;
		}
		return false;
	}

	public static void init(String filename) {
		detectors.clear();
		new DataReader(filename) {
			public void read(DataInputStream stream) throws IOException {
				int count = stream.readInt();
				for (;count > 0; --count) {
					//get required info
					UUID id = StreamUtil.readUUID(stream);
					//init a new detector
					DetectorSignPair det = DetectorSignPair.read(stream);
					//register
					det.region = DetectorRegion.getRegion(id);
					if (det.region == null) continue;
					det.region.register(det);
					detectors.put(det.region.getWorldName(), det.sign1.signLocation, det);
					detectors.put(det.region.getWorldName(), det.sign2.signLocation, det);
				}
			}
		}.read();
	}

	public static void deinit(String filename) {
		new DataWriter(filename) {
			public void write(DataOutputStream stream) throws IOException {
				Set<DetectorSignPair> detectorset = new HashSet<DetectorSignPair>(detectors.size() / 2);
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
	}
}
