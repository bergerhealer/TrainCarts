package com.bergerkiller.bukkit.tc.attachments.control;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

public class CartAttachmentPlatformTestVersion extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "PLATFORM";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/platform.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentPlatformTestVersion();
        }
    };

    private List<Grounded> grounded = new ArrayList<Grounded>();
    private double width = 5.0;
    private double length= 5.0;

    @Override
    public void onDetached() {
        super.onDetached();
    }

    @Override
    public void onAttached() {
        super.onAttached();
    }

    @Override
    public void applyPassengerSeatTransform(Matrix4x4 transform) {
        Matrix4x4 relativeMatrix = new Matrix4x4();
        relativeMatrix.translate(0.0, 1.0, 0.0);
        Matrix4x4.multiply(relativeMatrix, transform, transform);
    }

    @Override
    @Deprecated
    public void makeVisible(Player player) {
        makeVisible(getManager().asAttachmentViewer(player));
    }

    @Override
    @Deprecated
    public void makeHidden(Player player) {
        makeHidden(getManager().asAttachmentViewer(player));
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        Iterator<Grounded> iter = this.grounded.iterator();
        while (iter.hasNext()) {
            Grounded g = iter.next();
            if (g.player == viewer) {
                g.destroy();
                iter.remove();
            }
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        Matrix4x4 transform_inv = transform.clone();
        transform_inv.invert();

        double half_width = 0.5 * this.width;
        double half_length = 0.5 * this.length;

        // Track when existing grounded players go off the platform
        // TODO: Counteract this when the platform is 'bounded'
        Iterator<Grounded> grounded_iter = this.grounded.iterator();
        while (grounded_iter.hasNext()) {
            Grounded g = grounded_iter.next();

            // Check whether the player is within the width/length of the platform,
            // and above (y>0) it. If so, the player can potentially fall/land on the platform.
            Vector p = g.player.getLocation().toVector();
            double player_y = p.getY();
            transform_inv.transformPoint(p);
            if (p.getX() < -half_width || p.getX() > half_width ||
                p.getZ() < -half_length || p.getZ() > half_length)
            {
                g.destroy();
                grounded_iter.remove();
                continue;
            }

            // Move platform along
            Matrix4x4 t_copy = transform.clone();
            t_copy.translate(p.getX(), 0.0, p.getZ());
            g.update(this.getPreviousTransform(), transform, t_copy, player_y);

            // Particles yay!
            Color color = p.getY() > 0.1 ? Color.GREEN : Color.RED;
            Vector p1 = new Vector(-half_width, 0.0, -half_length);
            Vector p2 = new Vector(-half_width, 0.0, half_length);
            Vector p3 = new Vector(half_width, 0.0, half_length);
            Vector p4 = new Vector(half_width, 0.0, -half_length);
            transform.transformPoint(p1);
            transform.transformPoint(p2);
            transform.transformPoint(p3);
            transform.transformPoint(p4);
            DebugToolUtil.showLineParticles(g.player, color, p1, p2);
            DebugToolUtil.showLineParticles(g.player, color, p2, p3);
            DebugToolUtil.showLineParticles(g.player, color, p3, p4);
            DebugToolUtil.showLineParticles(g.player, color, p4, p1);

            Vector p1_mid = new Vector(0.0, 0.0, -half_length);
            Vector p2_mid = new Vector(0.0, 0.0, half_length);
            Vector p3_mid = new Vector(half_width, 0.0, 0.0);
            Vector p4_mid = new Vector(-half_width, 0.0, 0.0);
            transform.transformPoint(p1_mid);
            transform.transformPoint(p2_mid);
            transform.transformPoint(p3_mid);
            transform.transformPoint(p4_mid);
            DebugToolUtil.showLineParticles(g.player, color, p1_mid, p2_mid);
            DebugToolUtil.showLineParticles(g.player, color, p3_mid, p4_mid);
        }

        // Track when viewers go on the platform
        for (AttachmentViewer viewer : this.getAttachmentViewers()) {
            boolean isGrounded = false;
            for (Grounded g : this.grounded) {
                if (g.viewer.equals(viewer)) {
                    isGrounded = true;
                    break;
                }
            }
            
            if (isGrounded) {
                continue;
            }

            // Check whether the player is within the width/length of the platform,
            // and above (y>0) it. If so, the player can potentially fall/land on the platform.
            Vector p = viewer.getPlayer().getLocation().toVector();
            double player_y = p.getY();
            transform_inv.transformPoint(p);
            if (p.getY() <= 0.0) continue;
            if (p.getX() < -half_width || p.getX() > half_width) continue;
            if (p.getZ() < -half_length || p.getZ() > half_length) continue;

            Matrix4x4 t_copy = transform.clone();
            t_copy.translate(p.getX(), 0.0, p.getZ());

            // New player is grounded, spawn a shulker platform below the player
            Grounded gnew = new Grounded(viewer, this.getManager());
            gnew.update(this.getPreviousTransform(), transform, t_copy, player_y);
            gnew.spawn();
            this.grounded.add(gnew);
        }

        /*
        
        Vector old_p = this.getPreviousTransform().toVector();
        Vector new_p = transform.toVector();

        if (new_p.getY() > old_p.getY()) {
            Collection<Player> viewers = this.getViewers();
            for (Player viewer : viewers) {
                //this.makeHidden(viewer);
                Vector vel = viewer.getVelocity();
                vel.setY(((new_p.getY() + DebugUtil.getDoubleValue("a", 1.2)) - viewer.getLocation().getY()));
                if (vel.getY() >= 0.0) {
                    viewer.setVelocity(vel);
                }
            }
        } else if (new_p.getY() < old_p.getY()) {
            Collection<Player> viewers = this.getViewers();
            for (Player viewer : viewers) {
                //this.makeHidden(viewer);
                Vector vel = viewer.getVelocity();
                vel.setY(((new_p.getY() + 1.0) - viewer.getLocation().getY()));
                if (viewer.getVelocity().getY() < vel.getY()) {
                    viewer.setVelocity(vel);
                }
            }
        }
        
        this.entity.updatePosition(transform);
        this.actual.updatePosition(transform);
        
*/

        // This attempts to move players along as a test
        /*
        Vector new_p = this.transform.toVector();
        double change = DebugUtil.getDoubleValue("a", 1.405) * (new_p.getY() - old_p.getY());
        if (change != 0.0) {
            for (Player player : this.controller.getViewers()) {
                Vector vel = player.getVelocity();
                if (vel.getY() < change) {
                    double sf = DebugUtil.getDoubleValue("b", 1.405);
                    vel.setX(vel.getX() * sf);
                    vel.setZ(vel.getZ() * sf);
                    vel.setY(change);
                    player.setVelocity(vel);
                }
            }
        }
        */
    }

    @Override
    public void onMove(boolean absolute) {
        for (Grounded g : this.grounded) {
            g.syncPosition(absolute);
        }
    }

    @Override
    public void onTick() {
        /*
        updateMeta();
        PacketPlayOutEntityMetadataHandle p = PacketPlayOutEntityMetadataHandle.createNew(this.actual.getEntityId(), this.actual.getMetaData(), false);
        for (Player v : this.controller.getViewers()) {
            PacketUtil.sendPacket(v, p);
        }
        */
    }

    private static class Grounded {

        public Grounded(AttachmentViewer viewer, AttachmentManager manager) {
            this.player = viewer.getPlayer();
            this.viewer = viewer;
            this.manager = manager;

            this.actual = new VirtualEntity(manager);
            this.actual.setEntityType(EntityType.SHULKER);
            this.actual.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);

            // Shulker boxes fail to move, and must be inside a vehicle to move at all
            // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
            this.entity = new VirtualEntity(manager);
            this.entity.setEntityType(EntityType.CHICKEN);
            this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        }

        public void spawn() {
            this.actual.spawn(this.viewer, new Vector());
            this.entity.spawn(this.viewer, new Vector());
            viewer.getVehicleMountController().mount(entity.getEntityId(), actual.getEntityId());
        }

        public void destroy() {
            // Send entity destroy packet
            actual.destroy(this.viewer);
            entity.destroy(this.viewer);
        }

        public void update(Matrix4x4 platform_old_transform, Matrix4x4 platform_transform, Matrix4x4 transform, double player_y) {
            double shulker_offset = DebugUtil.getDoubleValue("a", 0.1);

            Vector pos = transform.toVector();
            Vector ypr = new Vector();
            this.entity.setRelativeOffset(0.0, -1.3499 + shulker_offset, 0.0);
            this.entity.updatePosition(pos, ypr);
            this.actual.updatePosition(pos, ypr);

            if (true) return;
            
            if (this.old_pos == null) {
                this.old_pos = pos.clone();
            }

            // When the player falls through the y-position, then he fell off our platform
            // This can happen because the synchronization of the platform lags behind
            // Correct this by moving the player up, with an extra offset on top of the platform.
            
            double diff = (this.old_pos.getY() + shulker_offset) - player_y;
            if (diff > 0.0) {
                this.did_adjust = true;
                Vector vel = this.player.getVelocity();
                vel.setY(diff + 0.01);
                this.player.setVelocity(vel);
            } else if (this.did_adjust) {
                this.did_adjust = false;
                this.player.setVelocity(new Vector());
            }

            if (player_y < (pos.getY() + 0.5 * shulker_offset)) {
                Vector vel = this.player.getVelocity();
                if (prev_pos != null) {
                    //vel.setX(this.player.getLocation().getX() - prev_pos.getX());
                    //vel.setY(this.player.getLocation().getY() - prev_pos.getY());
                }
                vel.setY(DebugUtil.getDoubleValue("v", 0.2));
                //vel.setY(pos.getY() - player_y + shulker_offset);
                this.player.setVelocity(vel);
            } else {
                
            }

            // Move along with platform
            double old_expected = this.old_pos.getY() + shulker_offset;
            double old_deviation = old_expected - this.player.getLocation().getY();
            if (old_deviation >= DebugUtil.getDoubleValue("m", -0.3) && false) { //PlayerMovementListener.movement.getY() <= 0.59) { //-1e-4)) {
                
                // Find relative position on the platform for the platform
                if (this.on_platform_pos == null) {
                    // What position is the player relative to transform?
                    old_player_pos = this.player.getLocation().toVector();
                    on_platform_pos = this.player.getLocation().toVector();
                    Matrix4x4 tmp = platform_transform.clone();
                    tmp.invert();
                    tmp.transformPoint(on_platform_pos);
                    on_platform_pos.setY(0.0);
                }

                //PlayerConnectionHandle.createHandle(EntityPlayerHandle.fromBukkit(this.player).getPlayerConnection()).sendPos(
                //        this.old_player_pos.getX(), this.old_player_pos.getY(), this.old_player_pos.getZ());
                
                // Find new position on the platform
                Vector new_player_pos;
                {
                    Matrix4x4 tmp = platform_transform.clone();
                    tmp.translate(this.on_platform_pos);
                    new_player_pos = tmp.toVector();
                }

                // See how much the player position differs from the expected old player position
                Vector exp_diff = this.player.getLocation().toVector().subtract(this.old_player_pos);
                if (hasChanges(exp_diff)) {
                    //System.out.println("DIFF: " + exp_diff);
                }

                Vector vel = new_player_pos.clone().subtract(this.player.getLocation().toVector());
                
                // Velocity is limited by protocol, take that into account
                vel.setX((double) ((int) (vel.getX() * 8000.0)) / 8000.0);
                vel.setY((double) ((int) (vel.getY() * 8000.0)) / 8000.0);
                vel.setZ((double) ((int) (vel.getZ() * 8000.0)) / 8000.0);
                
                new_player_pos = this.player.getLocation().toVector().add(vel);
                
                if (vel.length() > 1e-4) {
                    if (hasChanges(vel)) {
                        System.out.println("APPLY " + vel);
                    }
                    
                    this.player.setVelocity(vel);
                    PacketUtil.sendPacket(this.player, PacketType.OUT_ENTITY_VELOCITY.newInstance(this.player.getEntityId(), vel));

                    //PlayerMovementListener.last_set_velocity = vel.clone();
                    
                    this.old_player_pos = new_player_pos;
                } else {
                    this.player.setVelocity(new Vector());
                    //PlayerMovementListener.last_set_velocity = new Vector();
                }

                if (false) { //PlayerMovementListener.last_positions.size() > 10) {
                    //PlayerMovementListener.last_positions.remove(PlayerMovementListener.last_positions.size()-1);
                }
                //PlayerMovementListener.last_positions.add(0, new_player_pos.clone());

                //PlayerMovementListener.last_set_position = this.old_player_pos.clone();
                
                /*
                if (vel.length() > 1e-4) {
                    if (hasChanges(vel)) {
                        System.out.println("APPLY " + vel);
                    }
                    //this.player.setVelocity(vel);
                    this.old_player_pos = new_player_pos;

                    // this.broadcast(new PacketPlayOutEntityVelocity(this.tracker.getId(), this.n, this.o, this.p));
                    PacketUtil.sendPacket(this.player, PacketType.OUT_ENTITY_VELOCITY.newInstance(this.player.getEntityId(), vel));
                }
                */
                

                /*
                Vector old_platform_pos = platform_old_transform.toVector();
                Vector new_platform_pos = platform_transform.toVector();
                if (!old_platform_pos.equals(new_platform_pos)) {
                    Vector vel = new Vector(); //this.player.getVelocity();
                    
                    //vel.setY((vel.getY() / 0.98) + 0.08);
                    //vel.setY((vel.getY() + 0.08) / 0.98);
                    
                    vel.subtract(old_platform_pos);
                    vel.add(new_platform_pos);

                    // Note: gravity function will run, take that into account
                    // The gravity function does:
                    //   vel_y_new = 0.98 * (vel_y - 0.08)
                    // Undo this
                    // vel.setY((vel.getY() / 0.98) + 0.08);
                    PlayerMovementListener.lastenforced = vel.clone();
                    this.player.setVelocity(vel);
                } else {
                    PlayerMovementListener.lastenforced = new Vector();
                }
                */
                
                /*
                
                
                
                
                
                
                if (on_platform_pos == null) {
                    // What position is the player relative to transform?
                    on_platform_pos = this.player.getLocation().toVector();
                    Matrix4x4 tmp = platform_transform.clone();
                    tmp.invert();
                    tmp.transformPoint(on_platform_pos);
                    on_platform_pos.setY(0.0);

                    System.out.println("IS ON PLATFORM RELATIVE " + on_platform_pos);
                }
                
                Vector m = PlayerMovementListener.movement.clone();
                m.setY(0.0);
                                
                if (m.length() > 0.0) {
                    //System.out.println("MOVE " + m);
                    System.out.println("MOVE " + m);
                    
                    // Adjust platform relative position based on the movement
                    Vector v = m.clone();
                    Quaternion q = platform_transform.getRotation();
                    q.invert();
                    q.transformPoint(v);
                    
                    on_platform_pos.add(v);
                }
                
                // Find new desired position on the platform for the player
                Matrix4x4 t = platform_transform.clone();
                t.translate(on_platform_pos);
                Vector new_player_pos = t.toVector();
                
                // Compute velocity to adjust player position
                Vector vel = new_player_pos.clone();
                vel.subtract(this.player.getLocation().toVector());

                vel.setY(vel.getY() - 0.0784000015258789);
                
                //this.player.teleport(new_player_pos.toLocation(this.player.getWorld()));

                if (!this.player.getVelocity().equals(vel)) {
                    this.player.setVelocity(vel);
                }
                */
            } else {
                on_platform_pos = null;
                //PlayerMovementListener.last_set_position = null;
                //PlayerMovementListener.last_set_velocity = new Vector();
                //PlayerMovementListener.last_positions.clear();
            }


            
            /*
            // When player is on the platform and falling down, adjust velocity to match the platform
            // Only do this while the platform is standing still or also moving down
            // For moving up we have special logic
            double y_change = pos.getY() - this.old_y;
            if (true) { //y_change < -0.01) {
                // How close to the platform is the player currently? Is he on it?
                // If so, we can perform adjustment to snap to where the player should be
                double old_expected = this.old_y + shulker_offset;
                double old_deviation = old_expected - this.player.getLocation().getY();
                System.out.println("DEV " + old_deviation);
                if (old_deviation >= -1e-4) {
                    this.is_on_platform = true;
                }
                if (old_deviation < -0.4) {
                    this.is_on_platform = false;
                    System.out.println("JUMP");
                }

                if (is_on_platform) {

                    // Where do we want the player to be so he is on top of the shulker?
                    double desired_y = pos.getY() + shulker_offset;
                    double adj = desired_y - this.player.getLocation().getY();

                    Vector vel = this.player.getVelocity();
                    if ((vel.getY() <= 0.0 || adj > vel.getY()) && (adj > 1e-4 || adj < -1e-4)) {
                        System.out.println("ADJUST " + adj);
                        vel.setY(adj);
                        this.player.setVelocity(vel);
                    } else {
                        System.out.println("DONT!");
                    }
                } else {
                    System.out.println("NOT ON IT " + old_deviation);
                }
                
            }
            */

            prev_pos = this.player.getLocation();
            this.old_pos = pos.clone();

            double delta = pos.getY() - player_y;

            this.entity.syncPosition(true);
            this.actual.syncPosition(true);
            
            // Make sure the player stays above the platform
            /*
            if (delta < 0.0) {
                Vector vel = this.player.getVelocity();
                if (vel.getY() < delta) {
                    vel.setY(delta);
                    this.player.setVelocity(vel);
                }
            }
            */
        }

        public void syncPosition(boolean absolute) {
            //this.entity.syncPosition(absolute);
            //this.actual.syncPosition(absolute);
        }

        public final Player player;
        public final AttachmentViewer viewer;
        public Location prev_pos = null;
        public Vector old_pos = null;
        public Vector on_platform_pos = null;
        public Vector old_player_pos = null;
        private boolean is_on_platform = false;
        private boolean did_adjust = false;

        private final AttachmentManager manager;
        private final VirtualEntity actual;
        private final VirtualEntity entity;
    }
    
    private static boolean hasChanges(Vector vel) {
        return vel.getX() < -1e-3 || vel.getX() > 1e-3 || vel.getZ() < -1e-3 || vel.getZ() > 1e-3;
    }
}
