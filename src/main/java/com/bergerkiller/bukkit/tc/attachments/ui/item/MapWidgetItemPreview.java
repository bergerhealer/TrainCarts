package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.List;

import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Util;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.map.util.Model;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.TCConfig;

/**
 * Shows a large preview of an item's 3D model
 */
public class MapWidgetItemPreview extends MapWidget {
    private final Object renderLock = new Object();
    private Thread renderThread = null;
    private volatile boolean renderThreadStopping = false;
    private volatile RenderOptions lastRenderOptions = new RenderOptions();
    private volatile MapTexture lastRenderResult = null;

    public void setItem(ItemStack item) {
        lastRenderOptions = new RenderOptions(lastRenderOptions, item);
        synchronized (renderLock) {
            renderLock.notifyAll();
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.updateRenderRotation();
        renderThreadStopping = false;
        if (renderThread == null) {
            renderThread = new Thread(this::asyncRender);
            renderThread.setDaemon(true);
            renderThread.start();
        }
    }

    @Override
    public void onDetached() {
        super.onDetached();
        renderThreadStopping = true; // Make sure thread stops
        renderThread = null;
        synchronized (renderLock) {
            renderLock.notifyAll();
        }
    }

    private void asyncRender() {
        RenderOptions opt = null;
        while (!renderThreadStopping && getDisplay() != null) {
            // Wait until the render options are different
            synchronized (renderLock) {
                if (opt == lastRenderOptions) {
                    try {
                        renderLock.wait(1000);
                    } catch (InterruptedException e) {}
                    continue;
                } else {
                    opt = lastRenderOptions;
                }
            }

            // Render using these (new) options
            lastRenderResult = opt.render(view.getWidth(), view.getHeight());
            invalidate(); // Only sets a flag so I guess async is okay
        }
    }

    @Override
    public void onTick() {
        updateRenderRotation();
    }

    private void updateRenderRotation() {
        List<Player> viewers = display.getViewers();
        if (!viewers.isEmpty()) {
            Location loc = Util.getRealEyeLocation(viewers.get(0));
            RenderOptions opt = new RenderOptions(lastRenderOptions, loc);
            if (RenderOptions.isDifferent(lastRenderOptions, opt)) {
                synchronized (renderLock) {
                    lastRenderOptions = opt;
                    renderLock.notifyAll();
                }
            }
        }
    }

    @Override
    public void onDraw() {
        MapTexture result = lastRenderResult;
        if (result != null) {
            view.setBlendMode(MapBlendMode.NONE);
            view.draw(result, 0, 0);
        }
    }

    private static class RenderOptions {
        public final ItemStack item;
        public final Model model;
        public final float yaw, pitch;

        public RenderOptions() {
            this.item = null;
            this.model = null;
            this.yaw = 0.0f;
            this.pitch = 0.0f;
        }

        public RenderOptions(RenderOptions orig, ItemStack item) {
            this.item = item;
            this.model = TCConfig.resourcePack.getItemModel(item);
            this.yaw = orig.yaw;
            this.pitch = orig.pitch;
        }

        public RenderOptions(RenderOptions orig, Location eyeLocation) {
            this.item = orig.item;
            this.model = orig.model;
            this.yaw = eyeLocation.getYaw();
            this.pitch = eyeLocation.getPitch() - 90.0f;
        }

        public MapTexture render(int width, int height) {
            MapTexture texture = MapTexture.createEmpty(width, height);
            if (model != null) {
                double scale = width / 64.0;
                Matrix4x4 transform = new Matrix4x4();
                transform.translate(width / 2.0, 0.0, height / 2.0);
                transform.scale(scale);

                transform.rotateX(pitch);
                transform.rotateY(yaw);
                transform.translate(-8.0 / scale, -8.0 / scale, -8.0 / scale);

                texture.setLightOptions(0.0f, 1.0f, new Vector3(-1, 1, -1));
                texture.drawModel(model, transform);
            }
            return texture;
        }

        public static boolean isDifferent(RenderOptions opt1, RenderOptions opt2) {
            return opt1.model != opt2.model ||
                   MathUtil.getAngleDifference(opt1.yaw, opt2.yaw) > 2.0f ||
                   MathUtil.getAngleDifference(opt1.pitch, opt2.pitch) > 2.0f;
        }
    }
}
