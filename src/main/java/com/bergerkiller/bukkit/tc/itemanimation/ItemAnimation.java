package com.bergerkiller.bukkit.tc.itemanimation;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;

public class ItemAnimation {
    /** Duration of a single item transfer animation in ticks. */
    public static final int DURATION_TICKS = 10;

    private static final ArrayList<ItemAnimation> runningAnimations = new ArrayList<>();
    private static Task task;
    private final Target from;
    private final Target to;
    private final ItemAnimationVirtualItem item;
    public int ticksToFinish = DURATION_TICKS;

    private ItemAnimation(Target from, Target to, org.bukkit.inventory.ItemStack data) {
        this.from = from;
        this.to = to;
        Location f = this.getFrom();
        Location t = this.getTo();
        if (f.getWorld() != t.getWorld()) {
            throw new IllegalArgumentException("Locations are on different worlds!");
        }
        this.item = new ItemAnimationVirtualItem(f, data);
    }

    public static void start(Target from, Target to, org.bukkit.inventory.ItemStack data) {
        start(from, to, CommonItemStack.of(data));
    }

    public static void start(Target from, Target to, CommonItemStack data) {
        if (from == null || to == null || data.isEmpty()) {
            return;
        }
        data = data.clone();
        // Try to stack the item onto a nearby running animation first
        Location l1 = from.getLocation();
        for (ItemAnimation anim : runningAnimations) {
            Location l2 = anim.item.getLocation();
            if (l1.getWorld() == l2.getWorld() && l1.distanceSquared(l2) < 4.0) {
                CommonItemStack thisdata = CommonItemStack.of(anim.item.getItemStack());
                if (thisdata.isEmpty()) {
                    continue;
                }
                data.transferTo(thisdata, -1);
                if (data.isEmpty()) {
                    return;
                }
            }
        }

        runningAnimations.add(new ItemAnimation(from, to, data.toBukkit()));
        // Start the updating task if needed
        if (task == null) {
            task = new Task(TrainCarts.plugin) {
                public void run() {
                    Iterator<ItemAnimation> iter = runningAnimations.iterator();
                    ItemAnimation anim;
                    while (iter.hasNext()) {
                        anim = iter.next();
                        if (anim.update()) {
                            anim.item.die();
                            iter.remove();
                        }
                    }
                    if (runningAnimations.isEmpty()) {
                        Task.stop(task);
                        task = null;
                    }
                }
            }.start(1, 1);
        }
    }

    public static void deinit() {
        for (ItemAnimation anim : runningAnimations) {
            anim.item.die();
        }
        runningAnimations.clear();
        Task.stop(task);
        task = null;
    }

    public Location getTo() {
        return this.to.getLocation();
    }

    public Location getFrom() {
        return this.from.getLocation();
    }

    public boolean update() {
        if (--this.ticksToFinish > 0) {
            Location currentLoc = this.item.getLocation();
            Location targetLoc = this.getTo();
            Vector dir = targetLoc.toVector().subtract(currentLoc.toVector());
            double distancePerTick = dir.length() / this.ticksToFinish;
            dir.normalize().multiply(distancePerTick);
            this.item.update(dir);
        } else {
            return true;
        }
        return false;
    }

    /**
     * Represents an endpoint of an item animation that can supply its current location.
     */
    @FunctionalInterface
    public interface Target {
        Location getLocation();

        /**
         * Creates a Target positioned at the centre of the given block
         * (i.e. block corner + 0.5 on each axis).
         *
         * @param block The block
         * @return A Target for the centre of that block
         */
        static Target forBlock(Block block) {
            final Location blockLocation = block.getLocation().add(0.5, 0.5, 0.5);
            return () -> blockLocation;
        }

        /**
         * Creates a Target that follows the given entity's location.
         * Puts it slightly above the entity's feet (0.5 blocks above the entity's location).
         *
         * @param entity The entity
         * @return A Target for that entity's current location
         */
        static Target forEntity(Entity entity) {
            return () -> {
                double height = EntityHandle.fromBukkit(entity).getHeight();
                return entity.getLocation().add(0.0, height, 0.0);
            };
        }

        /**
         * Creates a Target that follows the location of the Bukkit entity
         * wrapped by the given {@link MinecartMember}.
         *
         * @param member The minecart member
         * @return A Target for that member's current location
         */
        static Target forMember(MinecartMember<?> member) {
            return forEntity(member.getEntity().getEntity());
        }

        /**
         * Creates a Target from any Bukkit {@link InventoryHolder}.
         * Handles Entities, BlockStates and DoubleChests.
         *
         * @param holder The inventory holder
         * @return A Target whose {@link #getLocation()} returns the holder's location
         */
        static Target of(InventoryHolder holder) {
            if (holder instanceof Entity) {
                return forEntity((Entity) holder);
            } else if (holder instanceof DoubleChest) {
                return ((DoubleChest) holder)::getLocation;
            } else if (holder instanceof BlockState) {
                return forBlock(((BlockState) holder).getBlock());
            }
            throw new IllegalArgumentException("Cannot determine location of " + holder.getClass().getName());
        }
    }
}
