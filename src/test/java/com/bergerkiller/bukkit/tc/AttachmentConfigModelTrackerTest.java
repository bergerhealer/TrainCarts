package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfigModelTracker;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfigTracker;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests specifically that the MODEL attachment combining logic works
 * as expected. Assumes that the AttachmentConfigTracker works as expected,
 * which is tested by {@link AttachmentConfigTrackerTest}.
 */
public class AttachmentConfigModelTrackerTest {

    @Test
    public void testInitializeInfiniteRecursion() {
        // Adds a model to the model store that references model2, that references model1 again
        // It should not add model1's details as child to model2 (and should also not hang)

        ConfigurationNode root = createAttachment("EMPTY");
        addModelAttachment(root, "model1");

        TestTracker tracker = track(root);
        ConfigurationNode model1Root = tracker.addModel("model1", AttachmentType.MODEL_TYPE_ID);
        model1Root.set("modelName", "model2");
        ConfigurationNode model2Root = tracker.addModel("model2", AttachmentType.MODEL_TYPE_ID);
        model2Root.set("modelName", "model1"); // infinite!

        tracker.start().assertPath().assertTypeId("EMPTY")
                .assertChild(AttachmentType.MODEL_TYPE_ID, a -> {
                    a.assertModelName("model1")
                            .assertChild(AttachmentType.MODEL_TYPE_ID, a2 -> {
                                a2.assertModelName("model2")
                                        .assertChild(AttachmentType.MODEL_TYPE_ID, a3 -> {
                                            a3.assertModelName("model1")
                                                    .assertNoMoreChildren(); // Recursion is aborted here
                                        })
                                        .assertNoMoreChildren();
                            })
                            .assertNoMoreChildren();
                })
                .assertNoMoreChildren();
    }

    @Test
    public void testInitializeTwoModelsLate() {
        // Initializes a root that has a model, and that model also has a model
        // Double recursion! But late.

        ConfigurationNode root = createAttachment("EMPTY");
        ConfigurationNode rootModel = addModelAttachment(root, "");

        TestTracker tracker = track(root);
        ConfigurationNode model1Root = tracker.addModel("model1", AttachmentType.MODEL_TYPE_ID);
        model1Root.set("modelName", "model2");
        ConfigurationNode model2Root = tracker.addModel("model2", "ITEM");

        tracker.start().assertPath().assertTypeId("EMPTY")
                .assertChild(AttachmentType.MODEL_TYPE_ID, a -> {
                    a.assertNotAModel()
                            .assertNoMoreChildren();
                })
                .assertNoMoreChildren();

        // Switch model to model1, which should also add model2
        rootModel.set("modelName", "model1");
        tracker.sync();
        tracker.assertRemoved(AttachmentType.MODEL_TYPE_ID, 0);
        tracker.assertAdded(AttachmentType.MODEL_TYPE_ID, 0)
                .assertModelName("model1")
                .assertChild(AttachmentType.MODEL_TYPE_ID, a2 -> {
                    a2.assertModelName("model2")
                            .assertChild("ITEM");
                })
                .assertNoMoreChildren();
    }

    @Test
    public void testInitializeTwoModels() {
        // Initializes a root that has a model, and that model also has a model
        // Double recursion!

        ConfigurationNode root = createAttachment("EMPTY");
        addModelAttachment(root, "model1");

        TestTracker tracker = track(root);
        ConfigurationNode model1Root = tracker.addModel("model1", AttachmentType.MODEL_TYPE_ID);
        model1Root.set("modelName", "model2");
        ConfigurationNode model2Root = tracker.addModel("model2", "ITEM");

        tracker.start().assertPath().assertTypeId("EMPTY")
                .assertChild(AttachmentType.MODEL_TYPE_ID, a -> {
                    a.assertModelName("model1")
                            .assertChild(AttachmentType.MODEL_TYPE_ID, a2 -> {
                                a2.assertModelName("model2")
                                        .assertChild("ITEM");
                            })
                            .assertNoMoreChildren();
                })
                .assertNoMoreChildren();
    }

    @Test
    public void testInitializeModelLate() {
        // Initializes the model and root, but does not yet assign the model name (leaves empty)
        // Then, model name is set, which should cause everything to become available

        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode model = addModelAttachment(root, "");
        ConfigurationNode mid = addAttachment(model, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);
        ConfigurationNode testModelRoot = tracker.addModel("testmodel", "MODELROOT");
        addAttachment(testModelRoot, "MODELSUB1");
        addAttachment(testModelRoot, "MODELSUB2");

        tracker.start().assertTypeId("ENTITY").assertPath()
                .assertChild("MODEL", a -> {
                    a.assertChild("EMPTY", a2 -> {
                                a2.assertChild("SEAT")
                                        .assertChild("ITEM")
                                        .assertNoMoreChildren();
                            })
                            .assertNoMoreChildren();
                })
                .assertChild("TEXT")
                .assertNoMoreChildren();

        // Change model name and sync changes
        model.set("modelName", "testmodel");
        tracker.sync();

        // Old MODEL should be removed, new MODEL with added MODELROOT details
        // should be added.
        tracker.assertRemoved("MODEL", 0);
        tracker.assertAdded("MODEL", 0)
                .assertChild("EMPTY", a2 -> {
                    a2.assertChild("SEAT")
                            .assertChild("ITEM")
                            .assertNoMoreChildren();
                })
                .assertChild("MODELROOT", a2 -> {
                    a2.assertChild("MODELSUB1")
                            .assertChild("MODELSUB2")
                            .assertNoMoreChildren();
                })
                .assertNoMoreChildren();
    }

    @Test
    public void testInitializeModel() {
        // Checks that loading in another model attachment works. Doesn't test
        // change tracking

        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode model = addModelAttachment(root, "testmodel");
        ConfigurationNode mid = addAttachment(model, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);
        ConfigurationNode testModelRoot = tracker.addModel("testmodel", "MODELROOT");
        addAttachment(testModelRoot, "MODELSUB1");
        addAttachment(testModelRoot, "MODELSUB2");

        tracker.start().assertTypeId("ENTITY").assertPath()
                .assertChild("MODEL", a -> {
                    a.assertChild("EMPTY", a2 -> {
                                a2.assertChild("SEAT")
                                        .assertChild("ITEM")
                                        .assertNoMoreChildren();
                            })
                            .assertChild("MODELROOT", a2 -> {
                                a2.assertChild("MODELSUB1")
                                        .assertChild("MODELSUB2")
                                        .assertNoMoreChildren();
                            })
                            .assertNoMoreChildren();
                })
                .assertChild("TEXT")
                .assertNoMoreChildren();
    }

    @Test
    public void testBasicModelProxy() {
        // Verifies that the behavior of the AttachmentConfigTracker properly proxies
        // the changes that happen in a linked MODEL that changes. This repeats some of the common
        // tests already tested for the base AttachmentConfigTracker.

        ConfigurationNode real_root = createAttachment("MODEL");
        real_root.set("modelName", "testmodel");
        TestTracker tracker = track(real_root);

        ConfigurationNode root = tracker.addModel("testmodel", "ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        tracker.start()
                .assertPath()
                .assertTypeId("MODEL")
                .assertModelName("testmodel")
                .assertChild("ENTITY", a1 -> {
                    a1.assertChild("EMPTY", a2 -> {
                                a2.assertChild("SEAT")
                                        .assertChild("ITEM")
                                        .assertNoMoreChildren();
                            })
                            .assertChild("TEXT")
                            .assertNoMoreChildren();
                })
                .assertNoMoreChildren();

        // Change an attachment
        {
            mid.set("position.x", 1.0);
            mid.set("position.y", 2.0);
            mid.set("position.z", 3.0);

            tracker.sync();
            tracker.assertChanged("EMPTY", 0, 0)
                    .assertChild("SEAT")
                    .assertChild("ITEM")
                    .assertNoMoreChildren();
            tracker.assertNone();
        }

        // Add two new attachments in the middle
        {
            ConfigurationNode newAttachment = createAttachment("MAINADD");
            addAttachment(newAttachment, "SUBADDONE");
            addAttachment(newAttachment, "SUBADDTWO");
            item.getNodeList("attachments").add(newAttachment);

            tracker.sync();
            tracker.assertChanged("ITEM", 0, 0, 1); // Because of creating the 'attachments' field
            tracker.assertAdded("MAINADD", 0, 0, 1, 0)
                    .assertChild("SUBADDONE")
                    .assertChild("SUBADDTWO")
                    .assertNoMoreChildren();
            tracker.assertNone();
        }

        // Remove two attachments in the middle again
        {
            item.getNodeList("attachments").clear();

            tracker.sync();
            tracker.assertRemoved("MAINADD", 0, 0, 1, 0)
                    .assertChild("SUBADDONE")
                    .assertChild("SUBADDTWO")
                    .assertNoMoreChildren();
            tracker.assertNone();
        }
    }

    @Test
    public void testBasicProxy() {
        // Verifies that the behavior of the AttachmentConfigTracker properly proxies
        // when no MODEL attachments are being used. This repeats some of the common
        // tests already tested for the base AttachmentConfigTracker.

        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);
        tracker.start()
                .assertPath().assertTypeId("ENTITY")
                .assertChild("EMPTY", a -> {
                    a.assertChild("SEAT")
                            .assertChild("ITEM")
                            .assertNoMoreChildren();
                })
                .assertChild("TEXT")
                .assertNoMoreChildren();

        // Change an attachment
        {
            mid.set("position.x", 1.0);
            mid.set("position.y", 2.0);
            mid.set("position.z", 3.0);

            tracker.sync();
            tracker.assertChanged("EMPTY", 0)
                    .assertChild("SEAT")
                    .assertChild("ITEM")
                    .assertNoMoreChildren();
            tracker.assertNone();
        }

        // Add two new attachments in the middle
        {
            ConfigurationNode newAttachment = createAttachment("MAINADD");
            addAttachment(newAttachment, "SUBADDONE");
            addAttachment(newAttachment, "SUBADDTWO");
            item.getNodeList("attachments").add(newAttachment);

            tracker.sync();
            tracker.assertChanged("ITEM", 0, 1); // Because of creating the 'attachments' field
            tracker.assertAdded("MAINADD", 0, 1, 0)
                    .assertChild("SUBADDONE")
                    .assertChild("SUBADDTWO")
                    .assertNoMoreChildren();
            tracker.assertNone();
        }

        // Remove two attachments in the middle again
        {
            item.getNodeList("attachments").clear();

            tracker.sync();
            tracker.assertRemoved("MAINADD", 0, 1, 0)
                    .assertChild("SUBADDONE")
                    .assertChild("SUBADDTWO")
                    .assertNoMoreChildren();
            tracker.assertNone();
        }
    }

    /*
     * =========================================================================================================
     * ======================================== TEST HELPER STUFF ==============================================
     * =========================================================================================================
     */

    private static ConfigurationNode addAttachment(ConfigurationNode parent, String type) {
        ConfigurationNode attachment = createAttachment(type);
        parent.getNodeList("attachments").add(attachment);
        return attachment;
    }

    private static ConfigurationNode addModelAttachment(ConfigurationNode parent, String modelName) {
        ConfigurationNode node = addAttachment(parent, AttachmentType.MODEL_TYPE_ID);
        node.set("modelName", modelName);
        return node;
    }

    private static ConfigurationNode createAttachment(String type) {
        ConfigurationNode node = new ConfigurationNode();
        node.set("type", type);
        ConfigurationNode position = node.getNode("position");
        position.set("x", 0.0);
        position.set("y", 0.0);
        position.set("z", 0.0);
        return node;
    }

    public static TestTracker track(ConfigurationNode config) {
        return new TestTracker(config);
    }

    // Just re-use the normal test tracker. Add logic for tracking multiple model configurations
    // sync() will sync all model configurations, in turn notifying this tracker.
    public static class TestTracker extends com.bergerkiller.bukkit.tc.AttachmentConfigTrackerTest.TestTracker {
        private final DefaultAttachmentConfigModelTracker tracker;

        public TestTracker(ConfigurationNode rootConfig) {
            this(new DefaultAttachmentConfigModelTracker(new AttachmentConfigTracker(rootConfig)));
        }

        public TestTracker(DefaultAttachmentConfigModelTracker tracker) {
            super(tracker);
            this.tracker = tracker;
        }

        public ConfigurationNode root() {
            return tracker.rootTracker.getConfig();
        }

        public ConfigurationNode addModel(String name, String typeId) {
            return tracker.addModel(name, typeId);
        }
    }

    private static class DefaultAttachmentConfigModelTracker extends AttachmentConfigModelTracker {
        private final Map<String, AttachmentConfigTracker> models = new HashMap<>();
        public final AttachmentConfigTracker rootTracker;

        public DefaultAttachmentConfigModelTracker(AttachmentConfigTracker tracker) {
            super(tracker);
            rootTracker = tracker;
        }

        public ConfigurationNode addModel(String name, String typeId) {
            ConfigurationNode model = createAttachment(typeId);
            models.put(name, new AttachmentConfigTracker(model));
            return model;
        }

        @Override
        public void sync() {
            rootTracker.sync();
            for (AttachmentConfigTracker model : models.values()) {
                model.sync();
            }
        }

        @Override
        public AttachmentConfigTracker findModelConfig(String name) {
            return models.computeIfAbsent(name, n -> {
                throw new IllegalStateException("Model not stored: " + n);
            });
        }
    }
}
