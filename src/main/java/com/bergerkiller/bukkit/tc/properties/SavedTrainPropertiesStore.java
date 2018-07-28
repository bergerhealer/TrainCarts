package com.bergerkiller.bukkit.tc.properties;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ListIterator;
import java.util.logging.Level;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import org.bukkit.util.FileUtil;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
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
    private String modulesDirectory = "";
    private final List<String> names = new ArrayList<String>();
    private Map<String, SavedTrainPropertiesStore> modules = new HashMap<String, SavedTrainPropertiesStore>();;
    private boolean changed = false;
    private boolean allowModules = true;

    public SavedTrainPropertiesStore(String filename) {
        this.savedTrainsConfig = new FileConfiguration(filename);
        this.savedTrainsConfig.load();
        this.names.addAll(this.savedTrainsConfig.getKeys());

        renameTrainsBeginningWithDigits();

    }

    public SavedTrainPropertiesStore(String filename, boolean allowModules) {
        this(filename);
        this.allowModules = allowModules;
    }

    public void loadModules(String directory) {
        if (this.allowModules) {
            this.modulesDirectory = directory;
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdir();
            }
            for (File file : StreamUtil.listFiles(dir)) {
                String name = file.getName();
                createModule(name);
            }
        } else {
            throw new UnsupportedOperationException("This store is not authorized to load modules");
        }
    }

    /**
     * Create a module from a filename. If it does not exist, it will be created.
     * @param fileName The filename of the desired module, in format `moduleName.yml`
     */
    private void createModule(String fileName) {
        String name = fileName;
        if (fileName.indexOf(".") > 0) {
            name = fileName.substring(0, fileName.lastIndexOf("."));
        }

        modules.put(name, new SavedTrainPropertiesStore(modulesDirectory + File.separator + fileName, false));
    }

    public void save(boolean autosave) {
        for (SavedTrainPropertiesStore module : this.modules.values()) {
            module.save(autosave);
        }

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
     * @param module to save in. null for the default store.
     */
    public void save(MinecartGroup group, String name, String module) throws IllegalNameException {
        if (name == null || name.isEmpty()) {
            throw new IllegalNameException("Name is empty");
        }
        if (Character.isDigit(name.charAt(0))) {
            throw new IllegalNameException("Name starts with a digit");
        }
        if (module != null && this.allowModules) {
            if (!this.modules.containsKey(module)) {
                createModule(module + ".yml");
            }
            this.modules.get(module).save(group, name, module);
            return;
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
            for (SavedTrainPropertiesStore module : this.modules.values()) {
                ConfigurationNode config = module.getConfig(name);
                if (config != null) {
                    return config;
                }
            }
            return null;
        }
        return this.savedTrainsConfig.getNode(name);
    }

    /**
     * Attempts to find a String token that starts with the name of a saved train. First searches
     * modules, then searches the default store.
     * 
     * @param text to find a name in
     * @return name found, null if none found
     */
    public String findName(String text) {
        String foundName = null;

        for (SavedTrainPropertiesStore module : this.modules.values()) {
            String name = module.findName(text);
            if (name != null) {
                foundName = name;
            }
        }


        for (String name : this.names) {
            if (text.startsWith(name) && (foundName == null || name.length() > foundName.length())) {
                foundName = name;
            }
        }

        return foundName;
    }

    /**
     * Get a list of all saved trains in this store (not including modules)
     * @return A List of the names of all saved trains' in this store
     */
    public List<String> getNames() {
        return this.names;
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
                    upgradeSavedTrains(new Matrix4x4(), new Matrix4x4(), cart.getNode("model"), undo);
                }
            }
        }
    }

    private void renameTrainsBeginningWithDigits() {
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
            this.savedTrainsConfig.set(new_name, this.savedTrainsConfig.getNode(name).clone());
            this.savedTrainsConfig.remove(name);
            this.changed = true;
        }
    }

    private static void upgradeSavedTrains(Matrix4x4 old_transform, Matrix4x4 new_transform, ConfigurationNode node, boolean undo) {
        // If node is a seat without position information, proxy the call since no changes occur
        if (node.get("type", CartAttachmentType.EMPTY) == CartAttachmentType.SEAT && !node.isNode("position")) {

            // Recursively operate on child attachments
            for (ConfigurationNode attNode : node.getNodeList("attachments")) {
                upgradeSavedTrains(old_transform, new_transform, attNode, undo);
            }

            return;
        }

        // Restore old position if it exists
        if (node.isNode("position_legacy")) {
            node.set("position", node.getNode("position_legacy").clone());
        }

        // Init position defaults if needed
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

        // Store old position
        node.set("position_legacy", node.getNode("position").clone());

        Matrix4x4 abs_old_transform;
        if (undo || node.get("type",  CartAttachmentType.EMPTY) != CartAttachmentType.ITEM) {
            // Other type of attachment - default update
            abs_old_transform = old_transform.clone();
            abs_old_transform.multiply(getAttTransform(node.getNode("position_legacy")));

        } else {
            // Compute absolute old position of the item, based on legacy item attachment position maths
            ItemTransformType transformType = node.getNode("position").get("transform", ItemTransformType.HEAD);
            Matrix4x4 old_local_transform = getAttTransform(node.getNode("position_legacy"));
            {
                Vector v_pos, v_rot;
                if (transformType == ItemTransformType.LEFT_HAND) {
                    // Left hand
                    Matrix4x4 tmp = old_transform.clone();
                    tmp.translate(-0.4, 0.3, 0.9375);
                    tmp.multiply(old_local_transform);

                    v_pos = tmp.toVector();
                    v_rot = tmp.getYawPitchRoll();
                    v_rot.setY(v_rot.getY() + 180.0);

                    // Arm offset
                    double dx = -0.3125 * Math.sin(Math.toRadians(v_rot.getY()));
                    double dz = 0.3125 * Math.cos(Math.toRadians(v_rot.getY()));
                    v_pos.setX(v_pos.getX() + dx);
                    v_pos.setZ(v_pos.getZ() + dz);

                } else if (transformType == ItemTransformType.RIGHT_HAND) {
                    // Right hand
                    Matrix4x4 tmp = old_transform.clone();
                    tmp.translate(-0.4, 0.3, -0.9375);
                    tmp.multiply(old_local_transform);

                    v_pos = tmp.toVector();
                    v_rot = tmp.getYawPitchRoll();

                    // Arm offset
                    double dx = -0.3125 * Math.sin(Math.toRadians(v_rot.getY()));
                    double dz = 0.3125 * Math.cos(Math.toRadians(v_rot.getY()));
                    v_pos.setX(v_pos.getX() + dx);
                    v_pos.setZ(v_pos.getZ() + dz);

                } else {
                    // Head
                    Matrix4x4 tmp = old_transform.clone();
                    tmp.multiply(old_local_transform);

                    v_pos = tmp.toVector();
                    v_rot = tmp.getYawPitchRoll();
                    v_rot.setY(v_rot.getY() + 180.0);
                }

                v_pos.setY(v_pos.getY() + 0.24);

                abs_old_transform = new Matrix4x4();
                abs_old_transform.translate(v_pos);
                abs_old_transform.rotateYawPitchRoll(v_rot);
            }
        }

        // Turn the original old absolute position into a transformation relative to the parent transform
        Matrix4x4 new_local_transform = new_transform.clone();
        new_local_transform.invert();
        new_local_transform.multiply(abs_old_transform);

        // Store new position
        setAttTransform(node.getNode("position"), new_local_transform);

        // Update transform of node
        old_transform = old_transform.clone();
        new_transform = new_transform.clone();
        if (node.isNode("position_legacy")) {
            old_transform.multiply(getAttTransform(node.getNode("position_legacy")));
            new_transform.multiply(getAttTransform(node.getNode("position")));
        } else {
            old_transform.multiply(getAttTransform(node.getNode("position")));
            new_transform.multiply(getAttTransform(node.getNode("position")));
        }

        // Remove position_legacy when undoing
        if (undo) {
            node.remove("position_legacy");
        }

        // Recursively operate on child attachments
        for (ConfigurationNode attNode : node.getNodeList("attachments")) {
            upgradeSavedTrains(old_transform, new_transform, attNode, undo);
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



    private static void setAttTransform(ConfigurationNode positionNode, Matrix4x4 transform) {
        Vector pos = transform.toVector();
        Vector rot = transform.getYawPitchRoll();
        positionNode.set("posX", MathUtil.round(pos.getX(), 6));
        positionNode.set("posY", MathUtil.round(pos.getY(), 6));
        positionNode.set("posZ", MathUtil.round(pos.getZ(), 6));
        positionNode.set("rotX", MathUtil.round(rot.getX(), 6));
        positionNode.set("rotY", MathUtil.round(rot.getY(), 6));
        positionNode.set("rotZ", MathUtil.round(rot.getZ(), 6));
    }

    private static List<SavedTrainPropertiesStore> loadSavedTrainsModules(String directory) {
        List<SavedTrainPropertiesStore> modules = new ArrayList<>();
        for (File file : StreamUtil.listFiles(new File(directory))) {
            modules.add(new SavedTrainPropertiesStore(directory + File.separator + file.getName()));
        }
        return modules;
    }
}
