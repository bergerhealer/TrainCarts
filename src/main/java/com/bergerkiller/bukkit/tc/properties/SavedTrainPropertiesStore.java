package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.ItemTransformType;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;

/**
 * Stores the train and cart properties for trains that have been saved using /train save.
 * These properties can also be used on spawner signs, or fused into spawning items.
 */
public class SavedTrainPropertiesStore {
    private final FileConfiguration savedTrainsConfig;
    private final List<String> names = new ArrayList<String>();
    private boolean changed = false;

    public SavedTrainPropertiesStore(String filename) {
        this.savedTrainsConfig = new FileConfiguration(filename);
        this.savedTrainsConfig.load();
        this.names.addAll(this.savedTrainsConfig.getKeys());

        // Rename trains starting with a number, as this breaks things
        ListIterator<String> iter = this.names.listIterator();
        while (iter.hasNext()) {
            String name = iter.next();
            if (!this.savedTrainsConfig.isNode(name)) {
                iter.remove();
                continue;
            }
            if (!name.isEmpty() && !Character.isDigit(name.charAt(0))) {
                continue;
            }

            String new_name = "t" + name;
            for (int i = 1; names.contains(new_name); i++) {
                new_name = "t" + name + i;
            }

            TrainCarts.plugin.log(Level.WARNING, "Train name '"  + name + "' starts with a digit, renamed to " + new_name);
            iter.set(new_name);
            this.savedTrainsConfig.set(new_name, this.savedTrainsConfig.getNode(name));
            this.savedTrainsConfig.remove(name);
            this.changed = true;
        }

    }

    public void save(boolean autosave) {
        if (autosave && !this.changed) {
            return;
        }
        this.savedTrainsConfig.save();
        this.changed = false;
    }

    /**
     * Saves the train information under a name
     * 
     * @param group to save
     * @param name to save as
     */
    public void save(MinecartGroup group, String name) throws IllegalNameException {
        if (name == null || name.isEmpty()) {
            throw new IllegalNameException("Name is empty");
        }
        if (Character.isDigit(name.charAt(0))) {
            throw new IllegalNameException("Name starts with a digit");
        }

        this.changed = true;
        ConfigurationNode config = this.savedTrainsConfig.getNode(name);
        config.clear();

        group.getProperties().save(config);
        config.remove("carts");

        List<ConfigurationNode> cartConfigList = new ArrayList<ConfigurationNode>();
        for (MinecartMember<?> member : group) {
            ConfigurationNode cartConfig = new ConfigurationNode();
            member.getProperties().save(cartConfig);
            cartConfig.set("entityType", member.getEntity().getType());
            cartConfig.set("flipped", member.getOrientationForward().dot(FaceUtil.faceToVector(member.getDirection())) < 0.0);
            cartConfig.remove("owners");

            ConfigurationNode data = new ConfigurationNode();
            member.onTrainSaved(data);
            if (!data.isEmpty()) {
                cartConfig.set("data", data);
            }

            cartConfigList.add(cartConfig);
        }
        config.setNodeList("carts", cartConfigList);

        this.names.remove(name);
        this.names.add(name);
    }

    /**
     * Gets the configuration for a saved train
     * 
     * @param name of the saved train
     * @return configuration
     */
    public ConfigurationNode getConfig(String name) {
        if (!this.savedTrainsConfig.isNode(name)) {
            return null;
        }
        return this.savedTrainsConfig.getNode(name);
    }

    /**
     * Attempts to find a String token that starts with the name of a saved train
     * 
     * @param text to find a name in
     * @return name found, null if none found
     */
    public String findName(String text) {
        String foundName = null;
        for (String name : this.names) {
            if (text.startsWith(name) && (foundName == null || name.length() > foundName.length())) {
                foundName = name;
            }
        }
        return foundName;
    }

    /**
     * Performs an upgrade on all saved train properties, to turn position configuration
     * of TC 1.12.2-v2 into that of TC 1.12.2-v3. No longer needed some time in the future.
     */
    public void upgradeSavedTrains(boolean undo) {
        changed = true;
        for (ConfigurationNode node : savedTrainsConfig.getNodes()) {
            List<ConfigurationNode> carts = node.getNodeList("carts");
            for (ConfigurationNode cart : carts) {
                if (cart.isNode("model")) {
                    upgradeSavedTrains(cart.getNode("model"), undo);
                }
            }
        }
    }

    /*
     * The following transformation is performed v2 -> v3
     * HEAD:
     * Y += 0.24
     *
     * LEFT_ARM:
     * YAW += 180.0
     * X += -0.4
     * Y += 0.48
     * Z += 0.625
     *
     * RIGHT_ARM:
     * X += -0.4
     * Y += 0.48
     * Z += -0.4
     */
    private static void upgradeSavedTrains(ConfigurationNode node, boolean undo) {
        // Perform modifications to the position node
        if (node.get("type", CartAttachmentType.EMPTY) == CartAttachmentType.ITEM) {
            // Init defaults if needed
            if (!node.isNode("position")) {
                ConfigurationNode position = node.getNode("position");
                position.set("transform", ItemTransformType.HEAD);
                position.set("posX", 0.0);
                position.set("posY", 0.0);
                position.set("posZ", 0.0);
                position.set("rotX", 0.0);
                position.set("rotY", 0.0);
                position.set("rotZ", 0.0);
            }

            ConfigurationNode position = node.getNode("position");
            ItemTransformType transform = position.get("transform", ItemTransformType.HEAD);

            Matrix4x4 oldTransform = getAttTransform(position);
            double f = undo ? -1.0 : 1.0;
            if (transform == ItemTransformType.HEAD) {
                position.set("posY", position.get("posY", 0.0) + f*0.24);
            } else if (transform == ItemTransformType.LEFT_HAND) {
                position.set("rotY", position.get("rotY", 0.0) + f*180.0);
                position.set("posX", position.get("posX", 0.0) + f*-0.4);
                position.set("posY", position.get("posY", 0.0) + f*0.48);
                position.set("posZ", position.get("posZ", 0.0) + f*0.625);
            } else if (transform == ItemTransformType.RIGHT_HAND) {
                position.set("posX", position.get("posX", 0.0) + f*-0.4);
                position.set("posY", position.get("posY", 0.0) + f*0.48);
                position.set("posZ", position.get("posZ", 0.0) + f*-0.4);
            }
            Matrix4x4 newTransform = getAttTransform(position);

            // Compute correction transformation matrix
            Matrix4x4 correction = newTransform;
            correction.invert();
            correction.multiply(oldTransform);

            // Apply correction to children. Ignore seat attachments without position (default to parent pos.)
            for (ConfigurationNode attNode : node.getNodeList("attachments")) {
                if (attNode.isNode("position") || attNode.get("type", CartAttachmentType.EMPTY) != CartAttachmentType.SEAT) {
                    ConfigurationNode attPosition = attNode.getNode("position");
                    Matrix4x4 attTransform = getAttTransform(attPosition);
                    attTransform.multiply(correction);
                    Vector pos = attTransform.toVector();
                    Vector rot = attTransform.getYawPitchRoll();
                    attPosition.set("posX", pos.getX());
                    attPosition.set("posY", pos.getY());
                    attPosition.set("posZ", pos.getZ());
                    attPosition.set("rotX", rot.getX());
                    attPosition.set("rotY", rot.getY());
                    attPosition.set("rotZ", rot.getZ());
                }
            }
        }

        // Recursively operate on child attachments
        for (ConfigurationNode attNode : node.getNodeList("attachments")) {
            upgradeSavedTrains(attNode, undo);
        }
    }

    private static Matrix4x4 getAttTransform(ConfigurationNode positionNode) {
        double posX = positionNode.get("posX", 0.0);
        double posY = positionNode.get("posY", 0.0);
        double posZ = positionNode.get("posZ", 0.0);
        double rotX = positionNode.get("rotX", 0.0);
        double rotY = positionNode.get("rotY", 0.0);
        double rotZ = positionNode.get("rotZ", 0.0);
        Matrix4x4 transform = new Matrix4x4();
        transform.translate(posX, posY, posZ);
        transform.rotateYawPitchRoll(new Vector(rotX, rotY, rotZ));
        return transform;
    }

}
