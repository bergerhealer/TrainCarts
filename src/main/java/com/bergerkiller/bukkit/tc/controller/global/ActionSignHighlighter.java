package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.block.BlockRayTrace;
import com.bergerkiller.bukkit.common.component.LibraryComponent;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayBlockEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayTextEntity;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.ListCallbackCollector;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps track of what block a Player is looking at while holding a lever
 * item, and activates highlighting for the signs attached to that block.<br>
 * <br>
 * Only highlights signs that contain actual registered sign actions, to avoid
 * disturbing vanilla behavior.
 */
public class ActionSignHighlighter implements LibraryComponent {
    private static final Material LEVER_TYPE = MaterialUtil.getFirst("LEVER", "LEGACY_LEVER");

    // Color wheel of values assigned to signs around the block the lever is attached to
    // Generally won't exceed 6, but it can handle more with a modulus
    private static final ChatColor[] HIGHLIGHT_COLORS = new ChatColor[] {
            ChatColor.RED,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.YELLOW,
            ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE,
            ChatColor.WHITE
    };

    private final TrainCarts plugin;
    private final Task updateTask;
    private final Listener listener;
    private final Map<Player, PlayerViewedBlockTracker> trackers = new IdentityHashMap<>();
    private boolean enabled = false;

    public ActionSignHighlighter(TrainCarts plugin) {
        this.plugin = plugin;
        this.updateTask = new Task(plugin) {
            int stateCtr = 0;

            @Override
            public void run() {
                int state = ++stateCtr;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerViewedBlockTracker tracker = trackers.computeIfAbsent(player, PlayerViewedBlockTracker::new);
                    tracker.state = state;
                    tracker.update();
                }
                trackers.values().removeIf(tracker -> {
                    if (tracker.state == state)
                        return false;

                    tracker.resetAndClearViewedBlock();
                    return true;
                });
            }
        };
        this.listener = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerInteract(PlayerInteractEvent event) {
                invalidateBlock(event.getPlayer(), event.getClickedBlock());
            }
        };
    }

    public void updateEnabled() {
        if (TCConfig.debugOutputLevers) {
            if (!enabled) {
                enabled = true;
                updateTask.start(1, 1);
                plugin.register(listener);
            }
        } else {
            disable();
        }
    }

    @Override
    public void enable() {
        updateEnabled();
    }

    @Override
    public void disable() {
        if (enabled) {
            enabled = false;
            trackers.values().forEach(PlayerViewedBlockTracker::resetAndClearViewedBlock);
            trackers.clear();
            updateTask.stop();
            CommonUtil.unregisterListener(listener);
        }
    }

    private void invalidateBlock(Player player, Block block) {
        if (block == null) {
            return;
        }

        PlayerViewedBlockTracker tracker = trackers.get(player);
        if (tracker == null || tracker.lastHighlightedBlock == null) {
            return;
        }

        // Invalidate when clicking on the block being highlighted
        if (block.equals(tracker.lastHighlightedBlock)) {
            tracker.reset();
            return;
        }

        // Also do so when clicking on the (presumed lever) of the block
        if (block.equals(tracker.lastHighlightedBlock.getRelative(tracker.lastHighlightedFace))) {
            tracker.reset();
            return;
        }
    }

    private static final class HighlightedSign {
        public final RailLookup.TrackedSign sign;
        public final ChatColor highlightColor;
        public final String outputDescription;
        public Runnable despawnHighlightCallback = () -> {};

        public HighlightedSign(RailLookup.TrackedSign sign, ChatColor highlightColor, String outputDescription) {
            this.sign = sign;
            this.highlightColor = highlightColor;
            this.outputDescription = outputDescription;
        }

        public void showDebug(AttachmentViewer viewer) {
            despawnHighlightCallback = sign.showDebugHighlight(viewer, new RailLookup.TrackedSign.DebugDisplayOptions() {
                @Override
                public ChatColor getTeamColor() {
                    return highlightColor;
                }

                @Override
                public TrainCarts getTrainCarts() {
                    return viewer.getTrainCarts();
                }
            });
        }

        public void hideDebug() {
            despawnHighlightCallback.run();
        }
    }

    private final class ViewedBlock {
        public final Block block;
        public final BlockFace face;
        public final BlockData blockDataAtFace;
        public final List<HighlightedSign> highlightedSigns;

        VirtualDisplayBlockEntity highlightLeverPos;
        VirtualDisplayTextEntity signDisplay;

        public ViewedBlock(Block block, BlockFace face, BlockData blockDataAtFace, List<HighlightedSign> highlightedSigns) {
            this.block = block;
            this.face = face;
            this.blockDataAtFace = blockDataAtFace;
            this.highlightedSigns = highlightedSigns;
        }

        public void hide(AttachmentViewer viewer) {
            if (highlightLeverPos != null) {
                highlightLeverPos.destroy(viewer);
            }
            if (signDisplay != null) {
                signDisplay.destroy(viewer);
            }

            highlightedSigns.forEach(HighlightedSign::hideDebug);
        }

        public void show(AttachmentViewer viewer) {
            // Decide where to put the text display
            // We want to show it somewhere relative to the lever without it
            // going inside another block, so try to find a free air spot.
            // If we can't find any, just put it in the middle (0.5, 0.5, 0.5) of the lever block
            BlockFace labelFace = BlockFace.SELF;
            if (canShowLabel(BlockFace.UP)) {
                labelFace = BlockFace.UP;
            } else if (canShowLabel(face)) {
                labelFace = face;
            } else if (canShowLabel(FaceUtil.rotate(face, 2))) {
                labelFace = FaceUtil.rotate(face, 2);
            } else if (canShowLabel(FaceUtil.rotate(face, -2))) {
                labelFace = FaceUtil.rotate(face, -2);
            } else if (canShowLabel(BlockFace.DOWN)) {
                labelFace = BlockFace.DOWN;
            }

            if (blockDataAtFace.getType() == LEVER_TYPE) {
                // Highlight the existing lever, in whatever state it is currently in
                Matrix4x4 m = new Matrix4x4();
                m.translate(block.getRelative(face).getLocation().toVector());
                m.translate(0.5, 0.0, 0.5);

                highlightLeverPos = new VirtualDisplayBlockEntity(null);
                highlightLeverPos.updatePosition(m);
                highlightLeverPos.setBlockData(blockDataAtFace);
            } else {
                // Show a dummy smaller lever to indicate the placement is possible
                Vector s = new Vector(0.5, 0.5, 0.5);
                Vector d = new Vector(0.5, -0.5, -0.01);

                Matrix4x4 m = new Matrix4x4();
                m.translate(block.getLocation().toVector());
                m.translate(0.5, 0.5, 0.5);
                m.translate(FaceUtil.faceToVector(face).multiply(0.5));

                m.rotate(Quaternion.fromLookDirection(FaceUtil.faceToVector(face.getOppositeFace())));


                d = d.clone().multiply(s);
                m.translate(d);

                highlightLeverPos = new VirtualDisplayBlockEntity(null);
                highlightLeverPos.updatePosition(m);
                highlightLeverPos.setBlockData(BlockData.fromMaterial(Material.LEVER));
                highlightLeverPos.setScale(s);
            }

            highlightLeverPos.setGlowColor(ChatColor.RED);
            highlightLeverPos.setBrightness(Brightness.FULL_ALL);
            highlightLeverPos.spawn(viewer, new Vector());

            {
                signDisplay = new VirtualDisplayTextEntity(null);

                Vector labelPosition = block.getLocation().toVector();
                MathUtil.addToVector(labelPosition, 0.5, 0.5, 0.5);
                labelPosition.add(FaceUtil.faceToVector(face));
                labelPosition.add(FaceUtil.faceToVector(labelFace).multiply(0.5));

                Matrix4x4 m = new Matrix4x4();
                m.translate(labelPosition);

                //m.rotate(Quaternion.fromLookDirection(FaceUtil.faceToVector(face)));

                signDisplay.updatePosition(m);
                //signDisplay.setStyleFlags(DisplayHandle.TextDisplayHandle.STYLE_FLAG_USE_DEFAULT_BACKGROUND);

                signDisplay.getMetadata().set(DisplayHandle.DATA_BILLBOARD_RENDER_CONSTRAINTS, DisplayHandle.BILLBOARD_RENDER_CENTER);

                signDisplay.setScale(new Vector(0.25, 0.25, 0.25));

                signDisplay.setText(ChatText.fromMessage(highlightedSigns.stream()
                        .map(s -> s.highlightColor + s.outputDescription)
                        .collect(Collectors.joining("\n"))
                ));

                signDisplay.setBackgroundColor(Color.fromARGB(128, 64, 64, 64));
                signDisplay.setBrightness(Brightness.FULL_ALL);
                signDisplay.spawn(viewer, new Vector());
            }

            highlightedSigns.forEach(s -> s.showDebug(viewer));
        }

        private boolean canShowLabel(BlockFace face) {
            Block block = this.block.getRelative(this.face).getRelative(face);
            return block.getType() == Material.AIR;
        }
    }

    private final class PlayerViewedBlockTracker {
        private final AttachmentViewer viewer;
        private int state = -1;
        private ViewedBlock lastViewedBlock = null;

        // Keeps track of the last block the player looked at to avoid having to calculate
        // things too often.
        private BlockRayTrace lastRayTrace = null;
        private BlockRayTrace.HitResult lastHitResult = null;
        private Block lastHighlightedBlock = null;
        private BlockFace lastHighlightedFace = null;

        public PlayerViewedBlockTracker(Player player) {
            this.viewer = plugin.getAttachmentViewer(player);
        }

        public void onViewedBlockChanged(ViewedBlock previousViewedBlock, ViewedBlock newViewedBlock) {
            if (previousViewedBlock != null) {
                previousViewedBlock.hide(viewer);
            }
            if (newViewedBlock != null) {
                newViewedBlock.show(viewer);
            }
        }

        public void reset() {
            lastRayTrace = null;
            lastHitResult = null;
            lastHighlightedBlock = null;
            lastHighlightedFace = null;
        }

        public void resetAndClearViewedBlock() {
            reset();
            clearViewedBlock();
        }

        public void clearViewedBlock() {
            ViewedBlock lastViewedBlockTmp = lastViewedBlock;
            if (lastViewedBlockTmp != null) {
                lastViewedBlock = null;
                onViewedBlockChanged(lastViewedBlockTmp, null);
            }
        }

        public void update() {
            // Ignore if this player does not support display entities and such (viaversion)
            if (!viewer.supportsDisplayEntities()) {
                return;
            }

            // Is the player holding a lever item in their main hand at all?
            ItemStack mainHandItem = HumanHand.getItemInMainHand(viewer.getPlayer());
            if (mainHandItem == null || mainHandItem.getType() != LEVER_TYPE) {
                resetAndClearViewedBlock();
                return;
            }

            // Initialize the (new) ray trace parameters and see if they changed compared to last tick
            BlockRayTrace rayTrace = BlockRayTrace.fromEyeOf(viewer.getPlayer());
            if (lastRayTrace != null && !isRayTraceDifferent(lastRayTrace, rayTrace)) {
                return;
            }

            // Perform a ray trace hit test
            lastRayTrace = rayTrace;
            BlockRayTrace.HitResult hit = rayTrace.rayTrace();
            if (hit == null) {
                lastHitResult = null;
                lastHighlightedBlock = null;
                lastHighlightedFace = null;
                clearViewedBlock();
                return;
            }

            // Recalculate only when the hit block / hit face changes
            if (
                    lastHitResult == null
                            || !lastHitResult.getHitBlock().equals(hit.getHitBlock())
                            || lastHitResult.getHitFace() != hit.getHitFace()
            ) {
                lastHitResult = hit;
            } else {
                return;
            }

            // Calculate what block is highlighted
            Block highlightedBlock = hit.getHitBlock();
            BlockFace highlightedFace = hit.getHitFace();
            BlockData blockDataAtFace = WorldUtil.getBlockData(highlightedBlock);
            if (blockDataAtFace.getType() == LEVER_TYPE) {
                // Looking at a lever highlights the block it is attached to, instead
                highlightedFace = blockDataAtFace.getAttachedFace();
                highlightedBlock = highlightedBlock.getRelative(highlightedFace);
                highlightedFace = highlightedFace.getOppositeFace();
            } else {
                // See what block is occupying the face looking at
                blockDataAtFace = WorldUtil.getBlockData(highlightedBlock.getRelative(highlightedFace));
            }

            // Recalculate only when the highlighted block / face changes
            if (
                    lastHighlightedBlock == null
                            || !lastHighlightedBlock.equals(highlightedBlock)
                            || lastHighlightedFace != highlightedFace
            ) {
                lastHighlightedBlock = highlightedBlock;
                lastHighlightedFace = highlightedFace;
            } else {
                return;
            }

            // If there is already a non-air block there (that is not a lever), do not highlight, since
            // we cannot actually place a lever here at this time
            if (blockDataAtFace.getType() == LEVER_TYPE) {
                if (blockDataAtFace.getAttachedFace() != highlightedFace.getOppositeFace()) {
                    clearViewedBlock();
                    return; // Lever is here but not attached to this block
                }
            } else if (blockDataAtFace.getType() != Material.AIR) {
                clearViewedBlock();
                return;
            }

            // Look up what signs are assigned to this block, and if they
            // have actions or not. If not, there is nothing to display.
            ListCallbackCollector<HighlightedSign> highlightedSignsTmp = new ListCallbackCollector<>();
            int colorWheelIdx = 0;
            for (RailLookup.TrackedSign sign : plugin.getTrackedSignLookup().getOutputtingTrackedSigns(highlightedBlock)) {
                SignAction action = sign.getAction();
                if (action == null) {
                    continue;
                }
                String outputDescription = action.getDescriptiveOutputName(sign.createEvent(SignActionType.NONE));
                if (outputDescription == null) {
                    continue;
                }
                ChatColor color = HIGHLIGHT_COLORS[colorWheelIdx++ % HIGHLIGHT_COLORS.length];
                highlightedSignsTmp.accept(new HighlightedSign(sign, color, outputDescription));
            }
            List<HighlightedSign> highlightedSigns = highlightedSignsTmp.result();

            // If there are no highlighted signs, despawn
            if (highlightedSigns.isEmpty()) {
                clearViewedBlock();
                return;
            }

            // Update the viewed block
            ViewedBlock lastViewedBlockTmp = lastViewedBlock;
            lastViewedBlock = new ViewedBlock(highlightedBlock, highlightedFace, blockDataAtFace, highlightedSigns);
            onViewedBlockChanged(lastViewedBlockTmp, lastViewedBlock);
        }
    }

    private static boolean isRayTraceDifferent(BlockRayTrace a, BlockRayTrace b) {
        if (a.getWorld() != b.getWorld()) return true;
        if (a.getStartPosition().distanceSquared(b.getStartPosition()) > 1e-4) return true;
        if (a.getEndPosition().distanceSquared(b.getEndPosition()) > 1e-4) return true;
        return false;
    }
}
