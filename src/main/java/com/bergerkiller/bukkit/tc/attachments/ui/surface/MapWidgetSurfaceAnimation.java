package com.bergerkiller.bukkit.tc.attachments.ui.surface;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentPlatform;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.EnumMap;

/**
 * Displays the configuration of a surface by showing an animation of
 * how it behaves. Shows a player standing on a simple plane moving with it.
 * Head turns with surface if view lock is enabled. Surface is shown using the block
 * material assigned to it. Pauses at each end-point to show what happens there, such
 * as turning into a shulker grid players can walk on if that is enabled.
 */
public class MapWidgetSurfaceAnimation extends MapWidget {
    private static final double MOVE_START_X = -18.0;
    private static final double MOVE_END_X = 18.0;
    private static final double SURFACE_Y = 0.0;
    private static final double SURFACE_Z = 0.0;
    private static final double MOVE_START_YAW = -40.0 + 180.0;
    private static final double MOVE_END_YAW = 40.0 + 180.0;
    private static final double MOVE_SPEED = 0.05;
    private static final int PAUSE_TICKS = 20;
    private static final double PLAYER_FALL_ACCELERATION = 1.4;
    private static final double PLAYER_FALL_TERMINAL_SLOWDOWN = 0.87;
    private static final double RENDER_SCALE = 1.6;

    private double surfacePositionX = MOVE_START_X;
    private double surfaceYaw = MOVE_START_YAW;
    private double animationT = 0.0;
    private boolean animationForward = true;
    private int pauseTicks = PAUSE_TICKS;
    private BlockData surfaceBlockData = BlockData.fromMaterial(MaterialUtil.getFirst("STONE", "LEGACY_STONE"));
    private Model surfaceBlockModel = MapResourcePack.VANILLA.getBlockModel(this.surfaceBlockData);
    private CartAttachmentPlatform.Color shulkerColor = CartAttachmentPlatform.Color.DEFAULT;
    private CartAttachmentPlatform.PlatformMode platformMode = CartAttachmentPlatform.PlatformMode.SIMULATED_WITH_SHULKER_GRID;
    private boolean viewLock = true;
    private double playerLockedYaw = MOVE_START_YAW;
    private boolean wasSurfaceMoving = false;
    private double shulkerModePlayerX = MOVE_START_X;
    private double shulkerModePlayerY = 0.0;
    private double shulkerModePlayerFallSpeed = 0.0;
    private boolean shulkerModePlayerFalling = false;
    private boolean shulkerModePlayerRespawnPending = false;

    @Override
    public void onTick() {
        if (this.pauseTicks > 0) {
            this.pauseTicks--;
        } else {
            this.animationT += this.animationForward ? MOVE_SPEED : -MOVE_SPEED;
            if (this.animationT >= 1.0) {
                this.animationT = 1.0;
                this.animationForward = false;
                this.pauseTicks = PAUSE_TICKS;
            } else if (this.animationT <= 0.0) {
                this.animationT = 0.0;
                this.animationForward = true;
                this.pauseTicks = PAUSE_TICKS;
            }

            this.surfacePositionX = lerp(MOVE_START_X, MOVE_END_X, this.animationT);
            this.surfaceYaw = lerp(MOVE_START_YAW, MOVE_END_YAW, this.animationT);
        }

        if (usesShulkerFallAnimation()) {
            boolean surfaceMoving = isSurfaceMoving();

            if (!this.wasSurfaceMoving && surfaceMoving) {
                this.shulkerModePlayerX = this.surfacePositionX;
                this.shulkerModePlayerY = 0.0;
                this.shulkerModePlayerFallSpeed = 0.0;
                this.shulkerModePlayerFalling = true;
                this.shulkerModePlayerRespawnPending = true;
            } else if (this.wasSurfaceMoving && !surfaceMoving) {
                if (this.shulkerModePlayerRespawnPending) {
                    this.shulkerModePlayerX = this.surfacePositionX;
                    this.shulkerModePlayerY = 0.0;
                    this.shulkerModePlayerFallSpeed = 0.0;
                    this.shulkerModePlayerFalling = false;
                    this.shulkerModePlayerRespawnPending = false;
                }
            }

            // Can happen when switching into a shulker-based mode while already moving
            if (surfaceMoving && this.shulkerModePlayerRespawnPending && !this.shulkerModePlayerFalling) {
                this.shulkerModePlayerX = this.surfacePositionX;
                this.shulkerModePlayerY = 0.0;
                this.shulkerModePlayerFallSpeed = 0.0;
                this.shulkerModePlayerFalling = true;
            }

            if (surfaceMoving && this.shulkerModePlayerFalling) {
                this.shulkerModePlayerFallSpeed = (this.shulkerModePlayerFallSpeed + PLAYER_FALL_ACCELERATION) * PLAYER_FALL_TERMINAL_SLOWDOWN;
                this.shulkerModePlayerY -= this.shulkerModePlayerFallSpeed;
            }
        } else {
            this.shulkerModePlayerY = 0.0;
            this.shulkerModePlayerFallSpeed = 0.0;
            this.shulkerModePlayerFalling = false;
            this.shulkerModePlayerRespawnPending = false;
        }

        this.wasSurfaceMoving = isSurfaceMoving();
        this.invalidate();
    }

    @Override
    public void onDraw() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        MapTexture texture = MapTexture.createEmpty(width, height);
        texture.setLightOptions(0.0f, 1.0f, new Vector3(-1.0, 1.0, -1.0));

        Matrix4x4 cameraBase = new Matrix4x4();
        cameraBase.translate(width / 2.0, height * -0.13, height * 0.5);
        cameraBase.rotateYawPitchRoll(-30.0, 0.0, 0.0);
        cameraBase.scale(RENDER_SCALE);

        drawAnimatedSurface(texture, cameraBase);
        view.draw(texture, 0, 0);
    }

    private void drawAnimatedSurface(MapTexture texture, Matrix4x4 cameraTransform) {
        Matrix4x4 surfaceBaseTransform = cameraTransform.clone();
        surfaceBaseTransform.translate(this.surfacePositionX, SURFACE_Y, SURFACE_Z);

        Matrix4x4 surfaceTransform = surfaceBaseTransform.clone();
        surfaceTransform.rotateYawPitchRoll(0.0, this.surfaceYaw, 0.0);

        boolean drawShulkers = !isSurfaceMoving() && this.platformMode.isSpawningShulkerGrid();
        boolean drawSingleShulker = this.platformMode == CartAttachmentPlatform.PlatformMode.SINGLE_SHULKER;
        if (drawShulkers) {
            drawShulkerGrid(texture, cameraTransform);
        }
        if (drawSingleShulker) {
            drawShulker(texture, cameraTransform, -8.0, -8.0);
        }

        if (!drawSingleShulker) {
            Matrix4x4 surfacePlaneTransform = surfaceTransform.clone();
            surfacePlaneTransform.scale(1.5, 0.05, 1.5);
            surfacePlaneTransform.translate(-8.0, 0.0, -8.0);
            if (this.platformMode != CartAttachmentPlatform.PlatformMode.SHULKER_GRID && !drawShulkers) {
                texture.drawModel(this.surfaceBlockModel, surfacePlaneTransform);
            } else {
                drawSurfaceOutline(texture, surfacePlaneTransform, 16.0, 16.0, MapColorPalette.COLOR_BLACK);
            }
        }

        Matrix4x4 playerTransform;
        if (usesShulkerFallAnimation()) {
            playerTransform = cameraTransform.clone();
            playerTransform.translate(this.shulkerModePlayerX, this.shulkerModePlayerY, SURFACE_Z);
            playerTransform.rotateY(180.0);
            playerTransform.translate(-8.0, 0.0, -8.0);
        } else {
            double playerYaw = this.viewLock ? this.surfaceYaw : this.playerLockedYaw;
            playerTransform = surfaceBaseTransform.clone();
            playerTransform.rotateYawPitchRoll(0.0, playerYaw, 0.0);
            playerTransform.translate(-8.0, 0.0, -8.0);
        }
        texture.drawModel(PlayerModel.STEVE, playerTransform);
    }

    private void drawShulkerGrid(MapTexture texture, Matrix4x4 cameraTransform) {
        drawShulker(texture, cameraTransform, -16.0, -16.0);
        drawShulker(texture, cameraTransform, 0.0, -16.0);
        drawShulker(texture, cameraTransform, -16.0, 0.0);
        drawShulker(texture, cameraTransform, 0.0, 0.0);
    }

    private void drawShulker(MapTexture texture, Matrix4x4 cameraTransform, double dx, double dz) {
        Model shulkerModel = BlockModel.shulker(this.shulkerColor);
        Matrix4x4 shulkerTransform = cameraTransform.clone();
        shulkerTransform.translate(this.surfacePositionX + dx, SURFACE_Y - 16.0, SURFACE_Z + dz);
        texture.drawModel(shulkerModel, shulkerTransform);
    }

    private void drawSurfaceOutline(MapTexture texture, Matrix4x4 transform, double width, double depth, byte color) {
        Vector p0 = new Vector(0.0, 0.0, 0.0);
        Vector p1 = new Vector(width, 0.0, 0.0);
        Vector p2 = new Vector(width, 0.0, depth);
        Vector p3 = new Vector(0.0, 0.0, depth);

        transform.transformPoint(p0);
        transform.transformPoint(p1);
        transform.transformPoint(p2);
        transform.transformPoint(p3);

        draw3DLine(texture, p0, p1, color);
        draw3DLine(texture, p1, p2, color);
        draw3DLine(texture, p2, p3, color);
        draw3DLine(texture, p3, p0, color);
    }

    private void draw3DLine(MapTexture texture, Vector p0, Vector p1, byte color) {
        texture.drawLine(
                (int) Math.round(p0.getX()),
                (int) Math.round(p0.getZ()),
                (int) Math.round(p1.getX()),
                (int) Math.round(p1.getZ()),
                color);
    }

    private boolean isSurfaceMoving() {
        return this.pauseTicks == 0;
    }

    private boolean usesShulkerFallAnimation() {
        return this.platformMode == CartAttachmentPlatform.PlatformMode.SHULKER_GRID
                || this.platformMode == CartAttachmentPlatform.PlatformMode.SINGLE_SHULKER;
    }

    public CartAttachmentPlatform.Color getShulkerColor() {
        return this.shulkerColor;
    }

    public BlockData getSurfaceBlockData() {
        return this.surfaceBlockData;
    }

    public void setSurfaceBlockData(BlockData blockData) {
        this.surfaceBlockData = (blockData == null)
                ? BlockData.fromMaterial(MaterialUtil.getFirst("STONE", "LEGACY_STONE"))
                : blockData;
        this.surfaceBlockModel = MapResourcePack.VANILLA.getBlockModel(this.surfaceBlockData);
        this.invalidate();
    }

    public void setShulkerColor(CartAttachmentPlatform.Color shulkerColor) {
        this.shulkerColor = (shulkerColor == null) ? CartAttachmentPlatform.Color.DEFAULT : shulkerColor;
        this.invalidate();
    }

    public CartAttachmentPlatform.PlatformMode getPlatformMode() {
        return this.platformMode;
    }

    public void setPlatformMode(CartAttachmentPlatform.PlatformMode platformMode) {
        this.platformMode = (platformMode == null)
                ? CartAttachmentPlatform.PlatformMode.SIMULATED_WITH_SHULKER_GRID
                : platformMode;

        if (usesShulkerFallAnimation()) {
            boolean surfaceMoving = isSurfaceMoving();
            this.shulkerModePlayerX = this.surfacePositionX;
            this.shulkerModePlayerY = 0.0;
            this.shulkerModePlayerFallSpeed = 0.0;
            this.shulkerModePlayerFalling = surfaceMoving;
            this.shulkerModePlayerRespawnPending = surfaceMoving;
            this.wasSurfaceMoving = surfaceMoving;
        }
        this.invalidate();
    }

    public boolean isViewLock() {
        return this.viewLock;
    }

    public void setViewLock(boolean viewLock) {
        if (this.viewLock != viewLock && !viewLock) {
            this.playerLockedYaw = this.surfaceYaw;
        }
        this.viewLock = viewLock;
        this.invalidate();
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    /**
     * Builds a simple cube block model from a single texture.
     */
    public static class BlockModel {
        private static final EnumMap<CartAttachmentPlatform.Color, Model> SHULKER_MODELS = createShulkerModels();
        private static final Model DEFAULT_SHULKER_MODE = SHULKER_MODELS.get(CartAttachmentPlatform.Color.DEFAULT);

        private final Model model;

        public BlockModel(MapTexture texture) {
            this.model = new Model();

            Model.Element element = new Model.Element();
            element.from.x = 0.0;
            element.from.y = 0.0;
            element.from.z = 0.0;
            element.to.x = 16.0;
            element.to.y = 16.0;
            element.to.z = 16.0;

            element.faces.put(BlockFace.NORTH, face(texture));
            element.faces.put(BlockFace.SOUTH, face(texture));
            element.faces.put(BlockFace.EAST, face(texture));
            element.faces.put(BlockFace.WEST, face(texture));
            element.faces.put(BlockFace.UP, face(texture));
            element.faces.put(BlockFace.DOWN, face(texture));

            model.elements.add(element);
            model.buildQuads();
        }

        public Model getModel() {
            return this.model;
        }

        public static Model shulker(CartAttachmentPlatform.Color color) {
            return SHULKER_MODELS.getOrDefault(color, DEFAULT_SHULKER_MODE);
        }

        private static EnumMap<CartAttachmentPlatform.Color, Model> createShulkerModels() {
            final MapTexture shulkerTextures =  MapTexture.loadResource(CartAttachmentPlatform.class,
                    "/com/bergerkiller/bukkit/tc/textures/surface/shulker_box_atlas.png");

            EnumMap<CartAttachmentPlatform.Color, Model> models = new EnumMap<>(CartAttachmentPlatform.Color.class);
            for (CartAttachmentPlatform.Color color : CartAttachmentPlatform.Color.values()) {
                MapTexture shulkerTexture = shulkerTextures.getView(color.ordinal() * 16, 0, 16, 16).clone();
                models.put(color, new BlockModel(shulkerTexture).getModel());
            }
            return models;
        }

        private static MapTexture loadTexture(String name) {
            return MapTexture.loadResource(MapWidgetSurfaceAnimation.class,
                    "/com/bergerkiller/bukkit/tc/textures/surface/blocks/" + name);
        }

        private static Model.Element.Face face(MapTexture texture) {
            Model.Element.Face face = new Model.Element.Face();
            face.texture = texture.clone();
            return face;
        }
    }

    /**
     * Builds a Model for rendering a player (steve) model onto a map.
     */
    public static class PlayerModel {
        public static final Model STEVE = new PlayerModel(MapTexture.loadResource(MapWidgetSurfaceAnimation.class,
                "/com/bergerkiller/bukkit/tc/textures/surface/steve.png")).getModel();

        private final MapTexture skin;
        private final Model model;

        public PlayerModel(MapTexture skin) {
            this.skin = skin;
            this.model = new Model();

            // Head
            addCuboid(4.0, 24.0, 4.0, 12.0, 32.0, 12.0, 0, 0);

            // Body
            addCuboid(4.0, 12.0, 6.0, 12.0, 24.0, 10.0, 16, 16);

            // Right arm / left arm
            addCuboid(0.0, 12.0, 6.0, 4.0, 24.0, 10.0, 40, 16);
            addCuboid(12.0, 12.0, 6.0, 16.0, 24.0, 10.0, 32, 48);

            // Right leg / left leg
            addCuboid(4.0, 0.0, 6.0, 8.0, 12.0, 10.0, 0, 16);
            addCuboid(8.0, 0.0, 6.0, 12.0, 12.0, 10.0, 16, 48);

            model.buildQuads();

        }

        public Model getModel() {
            return model;
        }

        private void addCuboid(
                double x1, double y1, double z1,
                double x2, double y2, double z2,
                int texX, int texY
        ) {
            int width = (int) Math.round(x2 - x1);
            int height = (int) Math.round(y2 - y1);
            int depth = (int) Math.round(z2 - z1);

            Model.Element element = new Model.Element();
            element.from.x = x1;
            element.from.y = y1;
            element.from.z = z1;
            element.to.x = x2;
            element.to.y = y2;
            element.to.z = z2;

            final MapTexture skin = this.skin;
            element.faces.put(BlockFace.UP, face(skin, texX + depth, texY, width, depth));
            element.faces.put(BlockFace.DOWN, face(skin, texX + depth + width, texY, width, depth));
            element.faces.put(BlockFace.NORTH, face(skin, texX + depth, texY + depth, width, height));
            element.faces.put(BlockFace.SOUTH, face(skin, texX + (2 * depth) + width, texY + depth, width, height));
            element.faces.put(BlockFace.EAST, face(skin, texX, texY + depth, depth, height));
            element.faces.put(BlockFace.WEST, face(skin, texX + depth + width, texY + depth, depth, height));

            model.elements.add(element);
        }

        private static Model.Element.Face face(MapTexture texture, int x, int y, int w, int h) {
            Model.Element.Face face = new Model.Element.Face();
            face.texture = texture.getView(x, y, w, h).clone();
            return face;
        }
    }
}
