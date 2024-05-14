package com.bergerkiller.bukkit.tc.debug.particles;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Uses display entities to show lines and shapes
 */
class DebugParticlesDisplay extends DebugParticles {
    private static final int DURATION = 100;
    private static final int BRIGHTNESS_RANGE = 4;
    private static final int BRIGHTNESS_STEPS = 2;
    private final List<DisplayTask> displayTasks = new ArrayList<>();

    protected DebugParticlesDisplay(Player player) {
        super(player);
    }

    @Override
    public void cube(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        double minSize = 0.02 * Util.absMinAxis(new Vector(x2-x1, y2-y1, z2-z1));
        double lineThickness = Math.min(0.3, minSize);
        cube(color, ConcretePalette.getConcrete(color), x1, y1, z1, x2, y2, z2, lineThickness);
    }

    @Override
    public void face(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dist = MathUtil.distance(x1, y1, z1, x2, y2, z2);
        double minSize = 0.02 * dist;
        double lineThickness = Math.min(0.3, minSize);
        face(color, ConcretePalette.getConcrete(color), x1, y1, z1, x2, y2, z2, lineThickness);
    }

    @Override
    public void line(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dist = MathUtil.distance(x1, y1, z1, x2, y2, z2);
        double minSize = 0.02 * dist;
        double lineThickness = Math.min(0.3, minSize);
        line(color, ConcretePalette.getConcrete(color), x1, y1, z1, x2, y2, z2, lineThickness);
    }

    private void cube(Color color, BlockData concrete, double x1, double y1, double z1, double x2, double y2, double z2, double lineThickness) {
        face(color, concrete, x1, y1, z1, x2, y1, z2, lineThickness);
        face(color, concrete, x1, y2, z1, x2, y2, z2, lineThickness);
        line(color, concrete, x1, y1, z1, x1, y2, z1, lineThickness);
        line(color, concrete, x2, y1, z1, x2, y2, z1, lineThickness);
        line(color, concrete, x1, y1, z2, x1, y2, z2, lineThickness);
        line(color, concrete, x2, y1, z2, x2, y2, z2, lineThickness);
    }

    private void face(Color color, BlockData concrete, double x1, double y1, double z1, double x2, double y2, double z2, double lineThickness) {
        line(color, concrete, x1, y1, z1, x2, y1, z1, lineThickness);
        line(color, concrete, x1, y1, z1, x1, y2, z1, lineThickness);
        line(color, concrete, x1, y1, z1, x1, y1, z2, lineThickness);
        line(color, concrete, x1, y2, z2, x2, y2, z2, lineThickness);
        line(color, concrete, x2, y1, z2, x2, y2, z2, lineThickness);
        line(color, concrete, x2, y2, z1, x2, y2, z2, lineThickness);
    }

    private static final DataWatcher.Prototype LINE_METADATA = VirtualDisplayEntity.BASE_DISPLAY_METADATA.modify()
            .setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING)
            .set(DisplayHandle.DATA_INTERPOLATION_DURATION, 0)
            .set(DisplayHandle.DATA_BRIGHTNESS_OVERRIDE, Brightness.blockLight(15))
            .setClientDefault(DisplayHandle.DATA_GLOW_COLOR_OVERRIDE, -1)
            .setClientDefault(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, BlockData.AIR)
            .create();

    private void line(Color color, BlockData concrete, double x1, double y1, double z1, double x2, double y2, double z2, double lineThickness) {
        double dist = MathUtil.distance(x1, y1, z1, x2, y2, z2);
        if (dist <= 1e-6) {
            return;
        }

        Quaternion rotation = Quaternion.fromLookDirection(new Vector(x2 - x1, y2 - y1, z2 - z1), new Vector(0.0, 1.0, 0.0));

        int entityId = EntityUtil.getUniqueEntityId();
        UUID entityUUID = UUID.randomUUID();
        DataWatcher metadata = LINE_METADATA.create();

        Vector translation = new Vector(-0.5 * lineThickness, -0.5 * lineThickness, -0.5 * dist);
        rotation.transformPoint(translation);

        // Setup metadata to transform the cube into a neat line
        {
            metadata.set(DisplayHandle.DATA_TRANSLATION, translation);
            metadata.set(DisplayHandle.DATA_LEFT_ROTATION, rotation);
            metadata.set(DisplayHandle.DATA_SCALE, new Vector(lineThickness, lineThickness, dist));
            metadata.set(DisplayHandle.DATA_GLOW_COLOR_OVERRIDE, color.asRGB());
            metadata.set(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, concrete);
        }

        DisplayTask task = new DisplayTask(entityId, color, metadata);
        task.applyBrightness();

        // Spawn the display entity itself
        {
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
            spawnPacket.setEntityId(entityId);
            spawnPacket.setEntityUUID(entityUUID);
            spawnPacket.setEntityType(VirtualDisplayEntity.BLOCK_DISPLAY_ENTITY_TYPE);
            spawnPacket.setPosX(0.5 * (x1 + x2));
            spawnPacket.setPosY(0.5 * (y1 + y2));
            spawnPacket.setPosZ(0.5 * (z1 + z2));
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            PacketUtil.sendPacket(player, spawnPacket);
            PacketUtil.sendPacket(player, PacketPlayOutEntityMetadataHandle.createNew(entityId, metadata, true));
        }

        displayTasks.add(task);
        startUpdating();
    }

    private static final DataWatcher.Prototype POINT_METADATA;
    static {
        double scale = 0.1;
        POINT_METADATA = VirtualDisplayEntity.BASE_DISPLAY_METADATA.modify()
                .set(DisplayHandle.DATA_TRANSLATION, new Vector(-0.5 * scale, -0.5 * scale, -0.5 * scale))
                .set(DisplayHandle.DATA_SCALE, new Vector(scale, scale, scale))
                .setByte(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING)
                .set(DisplayHandle.DATA_INTERPOLATION_DURATION, 0)
                .set(DisplayHandle.DATA_BRIGHTNESS_OVERRIDE, Brightness.blockLight(15))
                .setClientDefault(DisplayHandle.DATA_GLOW_COLOR_OVERRIDE, -1)
                .setClientDefault(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, BlockData.AIR)
                .create();
    }

    @Override
    public void point(Color color, double x, double y, double z) {
        int entityId = EntityUtil.getUniqueEntityId();
        UUID entityUUID = UUID.randomUUID();
        DataWatcher metadata = POINT_METADATA.create();

        // Setup metadata to transform the cube into a neat line
        {
            metadata.set(DisplayHandle.DATA_GLOW_COLOR_OVERRIDE, color.asRGB());
            metadata.set(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, ConcretePalette.getConcrete(color));
        }

        DisplayTask task = new DisplayTask(entityId, color, metadata);
        task.applyBrightness();

        // Spawn the display entity itself
        {
            PacketPlayOutSpawnEntityHandle spawnPacket = PacketPlayOutSpawnEntityHandle.T.newHandleNull();
            spawnPacket.setEntityId(entityId);
            spawnPacket.setEntityUUID(entityUUID);
            spawnPacket.setEntityType(VirtualDisplayEntity.BLOCK_DISPLAY_ENTITY_TYPE);
            spawnPacket.setPosX(x);
            spawnPacket.setPosY(y);
            spawnPacket.setPosZ(z);
            spawnPacket.setMotX(0.0);
            spawnPacket.setMotY(0.0);
            spawnPacket.setMotZ(0.0);
            spawnPacket.setYaw(0.0f);
            spawnPacket.setPitch(0.0f);
            PacketUtil.sendPacket(player, spawnPacket);
            PacketUtil.sendPacket(player, PacketPlayOutEntityMetadataHandle.createNew(entityId, metadata, true));
        }

        displayTasks.add(task);
        startUpdating();
    }

    @Override
    protected boolean update() {
        displayTasks.removeIf(d -> d.update(player));
        return displayTasks.isEmpty();
    }

    private static class DisplayTask {
        public final int entityId;
        public final Color color;
        public final DataWatcher metadata;
        public int age;
        public int brightness;

        public DisplayTask(int entityId, Color color, DataWatcher metadata) {
            this.entityId = entityId;
            this.color = color;
            this.metadata = metadata;
            this.age = 0;
        }

        public void applyBrightness() {
            // Brightness goes up to BRIGHTNESS_RANGE and then back down to 0, repeating
            // e.g.: 0 -> 1 -> 2 -> 3 -> 2 -> 1 -> 0 -> 1 (etc) for range=3, 6 values
            int brightness = age % (2 * BRIGHTNESS_RANGE);
            if (brightness > BRIGHTNESS_RANGE) {
                brightness = 2*BRIGHTNESS_RANGE - brightness;
            }
            brightness *= BRIGHTNESS_STEPS;
            brightness += (15 - BRIGHTNESS_STEPS*BRIGHTNESS_RANGE);

            metadata.set(DisplayHandle.DATA_BRIGHTNESS_OVERRIDE, Brightness.blockLight(brightness));
            metadata.set(DisplayHandle.DATA_GLOW_COLOR_OVERRIDE, Color.fromRGB(
                    (color.getRed() * brightness) / 15,
                    (color.getGreen() * brightness) / 15,
                    (color.getBlue() * brightness) / 15).asRGB());
        }

        public boolean update(Player viewer) {
            if (++age >= DURATION) {
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityDestroyHandle.createNewSingle(entityId));
                return true;
            } else {
                applyBrightness();
                PacketUtil.sendPacket(viewer, PacketPlayOutEntityMetadataHandle.createNew(entityId, metadata, false));
                return false;
            }
        }
    }

    /**
     * Stores types of concrete and a way to map from rgb color to the best-fitting
     * color concrete.
     */
    private static class ConcretePalette {
        public static final List<Entry> entries = Arrays.asList(
                new Entry("WHITE_CONCRETE", 255, 255, 255),
                new Entry("ORANGE_CONCRETE", 220, 95, 0),
                new Entry("MAGENTA_CONCRETE", 168, 49, 158),
                new Entry("LIGHT_BLUE_CONCRETE", 35, 134, 196),
                new Entry("YELLOW_CONCRETE", 237, 172, 21),
                new Entry("LIME_CONCRETE", 92, 165, 24),
                new Entry("PINK_CONCRETE", 211, 100, 141),
                new Entry("GRAY_CONCRETE", 53, 56, 60),
                new Entry("LIGHT_GRAY_CONCRETE", 125, 125, 115),
                new Entry("CYAN_CONCRETE", 21, 117, 133),
                new Entry("PURPLE_CONCRETE", 99, 31, 154),
                new Entry("BLUE_CONCRETE", 44, 46, 142),
                new Entry("BROWN_CONCRETE", 96, 59, 32),
                new Entry("GREEN_CONCRETE", 73, 91, 37),
                new Entry("RED_CONCRETE", 141, 35, 35),
                new Entry("BLACK_CONCRETE", 0, 0, 0)
        );

        public static BlockData getConcrete(Color color) {
            BlockData best = entries.get(0).data;
            long bestDistSq = Long.MAX_VALUE;
            for (Entry e : entries) {
                long dist = calcColourDistanceSq(e.color, color);
                if (dist < bestDistSq) {
                    bestDistSq = dist;
                    best = e.data;
                }
            }
            return best;
        }

        private static class Entry {
            public final BlockData data;
            public final Color color;

            public Entry(String name, int r, int g, int b) {
                this.data = BlockData.fromMaterial(MaterialUtil.getMaterial(name));
                this.color = Color.fromRGB(r, g, b);
            }
        }

        // https://stackoverflow.com/a/9085524
        private static long calcColourDistanceSq(Color c1, Color c2) {
            long rmean = ( (long)c1.getRed() + (long)c2.getRed() ) / 2;
            long r = (long)c1.getRed() - (long)c2.getRed();
            long g = (long)c1.getGreen() - (long)c2.getGreen();
            long b = (long)c1.getBlue() - (long)c2.getBlue();
            return (((512+rmean)*r*r)>>8) + 4*g*g + (((767-rmean)*b*b)>>8);
        }
    }
}
