package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.yaml.YamlPath;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig.ChangeType;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfigListener;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfigTracker;

import static org.junit.Assert.*;

import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfigTrackerBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Verifies the various ways in which an attachment tree configuration can
 * be modified (by the editor) and that all of them fire the correct events.
 */
public class AttachmentConfigTrackerTest {

    @Test
    public void testConfigurationEmptyToggle() {
        ConfigurationNode root = new ConfigurationNode();
        TestTracker tracker = new TestTracker(new AttachmentConfigTracker(root));

        tracker.start().assertEmptyConfig();

        // Make it non-empty. Should go from empty to not empty. (Removal + Add)
        root.setTo(createAttachment("EMPTY"));
        addAttachment(root, "ITEM");
        tracker.sync();
        tracker.assertRemoved("EMPTY").assertNoMoreChildren();
        tracker.assertAdded("EMPTY").assertNonEmptyConfig().assertChild("ITEM", a1 -> {
            a1.assertNonEmptyConfig().assertNoMoreChildren();
        }).assertNoMoreChildren();
        tracker.assertSynchronized("EMPTY");
        tracker.assertNone();

        // Make it empty again. Should go from non-empty to empty. (Removal + Add)
        root.clear();
        tracker.sync();
        tracker.assertRemoved("EMPTY").assertChild("ITEM", a1 -> {
            a1.assertNonEmptyConfig().assertNoMoreChildren();
        }).assertNoMoreChildren();
        tracker.assertAdded("EMPTY").assertEmptyConfig();
        tracker.assertSynchronized("EMPTY");
        tracker.assertNone();
    }

    @Test
    public void testFullAttachmentChildrenSwap() {
        final ConfigurationNode configA;
        {
            configA = createAttachment("EMPTY");
            addAttachment(configA, "SEAT");
            ConfigurationNode mid = addAttachment(configA, "ITEM");
            mid.set("itemstack", "miditem");
            addAttachment(mid, "ITEM").set("itemstack", "item1");
            addAttachment(mid, "ITEM").set("itemstack", "item2");
            addAttachment(mid, "ITEM").set("itemstack", "item3");
        }

        final ConfigurationNode configB;
        {
            configB = createAttachment("EMPTY");
            addAttachment(configB, "ITEM").set("itemstack", "item4");
            addAttachment(configB, "ITEM").set("itemstack", "item5");
            addAttachment(configB, "ITEM").set("itemstack", "item6");
        }

        ConfigurationNode root = configA.clone();

        TestTracker tracker = new TestTracker(new AttachmentConfigTracker(root));

        tracker.start().assertPath().assertTypeId("EMPTY")
                .assertChild("SEAT", a2 -> a2.assertNoMoreChildren())
                .assertChild("ITEM", a2 -> {
                    a2.assertConfig(cfg -> assertEquals("miditem", cfg.get("itemstack", "")));
                    a2.assertChild("ITEM", a3 -> {
                        a3.assertConfig(cfg -> assertEquals("item1", cfg.get("itemstack", "")));
                        a3.assertNoMoreChildren();
                    });
                    a2.assertChild("ITEM", a3 -> {
                        a3.assertConfig(cfg -> assertEquals("item2", cfg.get("itemstack", "")));
                        a3.assertNoMoreChildren();
                    });
                    a2.assertChild("ITEM", a3 -> {
                        a3.assertConfig(cfg -> assertEquals("item3", cfg.get("itemstack", "")));
                        a3.assertNoMoreChildren();
                    });
                })
                .assertNoMoreChildren();

        // Swap out for a completely new configuration with the sqme root EMPTY attachment, but a new
        // set of child attachments.
        root.setTo(configB);
        {
            tracker.sync();

            // New configuration has one extra ITEM child of the root EMPTY, so that one is added first
            tracker.assertAdded("ITEM", 2)
                    .assertConfig(cfg -> assertEquals("item6", cfg.get("itemstack", "")))
                    .assertNoMoreChildren();

            // It should then see that the first attachment changed type (SEAT -> ITEM), so that one is removed and re-added
            tracker.assertRemoved("SEAT", 0);
            tracker.assertAdded("ITEM", 0)
                    .assertConfig(cfg -> assertEquals("item4", cfg.get("itemstack", "")))
                    .assertNoMoreChildren();

            // Second item had 3 children, which are now removed. These removals should happen now.
            tracker.assertRemoved("ITEM", 1, 0)
                    .assertNoMoreChildren();
            tracker.assertRemoved("ITEM", 1, 0)
                    .assertNoMoreChildren();
            tracker.assertRemoved("ITEM", 1, 0)
                    .assertNoMoreChildren();

            // Second item changed itemstack value
            tracker.assertChanged("ITEM", 1)
                    .assertConfig(cfg -> assertEquals("item5", cfg.get("itemstack", "")));

            // Done
            tracker.assertSynchronized("EMPTY");
            tracker.assertNone();
        }

        // Switch back to the previous configuration, which should re-add the three child attachments
        root.setTo(configA);
        {
            tracker.sync();

            // Older configuration has one less child attachment, so the third one is now removed
            tracker.assertRemoved("ITEM", 2)
                    .assertConfig(cfg -> assertEquals("item6", cfg.get("itemstack", "")))
                    .assertNoMoreChildren();

            // First attachment changed type (ITEM -> SEAT), so is removed and re-added
            tracker.assertRemoved("ITEM", 0);
            tracker.assertAdded("SEAT", 0)
                    .assertNoMoreChildren();

            // Now the three items are re-added
            tracker.assertAdded("ITEM", 1, 0)
                    .assertConfig(cfg -> assertEquals("item1", cfg.get("itemstack", "")))
                    .assertNoMoreChildren();
            tracker.assertAdded("ITEM", 1, 1)
                    .assertConfig(cfg -> assertEquals("item2", cfg.get("itemstack", "")))
                    .assertNoMoreChildren();
            tracker.assertAdded("ITEM", 1, 2)
                    .assertConfig(cfg -> assertEquals("item3", cfg.get("itemstack", "")))
                    .assertNoMoreChildren();

            // Second attachment changed itemstack (now mid item)
            tracker.assertChanged("ITEM", 1)
                    .assertConfig(cfg -> assertEquals("miditem", cfg.get("itemstack", "")));

            // Done
            tracker.assertSynchronized("EMPTY");
            tracker.assertNone();
        }
    }

    @Test
    public void testAddAttachmentConfigChild() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = new TestTracker(new AttachmentConfigTracker(root));

        // Start tracking, then add a new child after SEAT before ITEM
        ConfigurationNode newChild = createAttachment("NEWCHILD");
        AttachmentConfig addedChild = tracker.start().attachment.child(0).addChild(1, newChild);
        assertEquals("NEWCHILD", addedChild.typeId());
        assertTrue(addedChild.config() == newChild);

        // Sync changes, this new child should be notified added
        tracker.sync();
        tracker.assertAdded("NEWCHILD", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();

        // Modifying the original configuration node we passed in should update the attachment, too
        newChild.set("position.z", 234);
        tracker.sync();
        tracker.assertChanged("NEWCHILD", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testRemoveAttachmentConfigChild() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = new TestTracker(new AttachmentConfigTracker(root));

        // Start tracking, then remove the ITEM attachment
        tracker.start().attachment.child(0).child(1).remove();

        // Sync, we should see a removal notification of the child
        tracker.sync();
        tracker.assertRemoved("ITEM", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();

        // The original configuration should have changed so the item is gone
        assertFalse(item.hasParent());
        assertEquals(1, mid.getNodeList("attachments").size());
        assertEquals(seat, mid.getNodeList("attachments").get(0));

        // The new root attachment config tree should no longer have the item
        List<AttachmentConfig> midChildren = tracker.tracker.getRoot().get().child(0).children();
        assertEquals(1, midChildren.size());
        assertEquals("SEAT", midChildren.get(0).typeId());
    }

    @Test
    public void testRootReferenceInvalidationConfigSwap() {
        final AtomicReference<ConfigurationNode> rootRef = new AtomicReference<>(createAttachment("ENTITY"));

        AttachmentConfigTracker tracker = new AttachmentConfigTracker(rootRef::get);

        // Create a root reference and verify its representation is correct
        AttachmentConfig.RootReference refRoot = tracker.getRoot();
        assertTrue(refRoot.valid());
        assertConfig(refRoot.get()).assertPath().assertTypeId("ENTITY")
                .assertNoMoreChildren();

        // Change the reference value
        rootRef.set(createAttachment("ITEM"));

        // Even without calling sync(), this should have invalidated the root
        assertFalse(refRoot.valid());
        try {
            refRoot.get();
            fail("No exception was thrown");
        } catch (IllegalStateException ex) { /* ok */ }
    }

    @Test
    public void testRootReferenceInvalidation() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        AttachmentConfigTracker tracker = new AttachmentConfigTracker(root);

        // Create a root reference and verify its representation is correct
        AttachmentConfig.RootReference refRoot = tracker.getRoot();
        assertTrue(refRoot.valid());
        assertConfig(refRoot.get()).assertPath().assertTypeId("ENTITY")
                .assertChild("EMPTY", a1 -> {
                    a1.assertChild("SEAT")
                      .assertChild("ITEM")
                      .assertNoMoreChildren();
                })
                .assertChild("TEXT")
                .assertNoMoreChildren();

        // Modify the configuration
        seat.set("position.y", 50.0);

        // Even without calling sync(), this should have invalidated the root
        assertFalse(refRoot.valid());
        try {
            refRoot.get();
            fail("No exception was thrown");
        } catch (IllegalStateException ex) { /* ok */ }
    }

    @Test
    public void testDeepBlockRemove() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        mid.remove();
        tracker.sync();
        tracker.assertRemoved("EMPTY", 0)
                .assertChild("SEAT")
                .assertChild("ITEM")
                .assertNoMoreChildren();
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testModelRename() {
        // Checks that renaming a MODEL attachment causes a REMOVE-ADD, rather than
        // a CHANGED notification.
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode model = addAttachment(root, AttachmentType.MODEL_TYPE_ID);
        model.set("modelName", "");

        TestTracker tracker = track(root);

        model.set("modelName", "testmodel");
        tracker.sync();
        tracker.assertRemoved(AttachmentType.MODEL_TYPE_ID, 0);
        tracker.assertAdded(AttachmentType.MODEL_TYPE_ID, 0)
                .assertModelName("testmodel");
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();

        model.set("modelName", "othermodel");
        tracker.sync();
        tracker.assertRemoved(AttachmentType.MODEL_TYPE_ID, 0);
        tracker.assertAdded(AttachmentType.MODEL_TYPE_ID, 0)
                .assertModelName("othermodel");
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();

        model.set("modelName", "");
        tracker.sync();
        tracker.assertRemoved(AttachmentType.MODEL_TYPE_ID, 0);
        tracker.assertAdded(AttachmentType.MODEL_TYPE_ID, 0)
                .assertNotAModel();
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testModifyThenRemove() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.set("position.y", 200.0);
        mid.remove();

        tracker.sync();
        tracker.assertRemoved("EMPTY", 0);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testClearChildren() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        root.getNodeList("attachments").clear();

        tracker.sync();
        tracker.assertRemoved("EMPTY", 0);
        tracker.assertRemoved("TEXT", 0); // TEXT moved from [1] to [0]
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testCloneAddAttachmentTree() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode text = addAttachment(root, "TEXT");
        ConfigurationNode woo = addAttachment(root, "WOO");

        ConfigurationNode mid = createAttachment("EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");

        TestTracker tracker = track(root);

        woo.getNodeList("attachments").add(mid);

        tracker.sync();

        tracker.assertAdded("EMPTY", 1, 0)
               .assertChild("SEAT")
               .assertChild("ITEM")
               .assertNoMoreChildren();
        tracker.assertChanged("WOO", 1); // Changed because we added the "attachments" field
                                                      // TODO: Do we try to fix this or nah?
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testCloneAddAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        mid.getNodeList("attachments").add(item.clone());

        tracker.sync();
        tracker.assertAdded("ITEM", 0, 2);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testMoveAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.remove();
        mid.getNodeList("attachments").add(0, item);

        tracker.sync();
        tracker.assertRemoved("ITEM", 0, 1);
        tracker.assertAdded("ITEM", 0, 0);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testRemoveAndInsertAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.remove();
        mid.getNodeList("attachments").add(0, createAttachment("NEAT"));

        tracker.sync();
        tracker.assertRemoved("ITEM", 0, 1);
        tracker.assertAdded("NEAT", 0, 0);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testRemoveAndAddAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.remove();
        addAttachment(mid, "NEAT");

        tracker.sync();
        tracker.assertRemoved("ITEM", 0, 1);
        tracker.assertAdded("NEAT", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testChangeMultipleAttachments() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        root.set("position.x", 1.0);
        root.set("position.y", 2.0);
        root.set("position.z", 3.0);

        item.set("position.x", 1.0);
        item.set("position.y", 2.0);
        item.set("position.z", 3.0);

        tracker.sync();
        tracker.assertChanged("ENTITY")
                .assertChild("EMPTY", e -> {
                    e.assertChild("SEAT")
                     .assertChild("ITEM");
                })
                .assertChild("TEXT")
                .assertNoMoreChildren();
        tracker.assertChanged("ITEM", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testChangeSingleAttachmentChangeType() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.set("type", "NOT_AN_ITEM");

        tracker.sync();
        tracker.assertRemoved("ITEM", 0, 1);
        tracker.assertAdded("NOT_AN_ITEM", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testChangeSingleAttachmentAddField() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.set("newfield", "value");

        tracker.sync();
        tracker.assertChanged("ITEM", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    @Test
    public void testChangeSingleAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = track(root);

        item.set("position.x", 1.0);
        item.set("position.y", 2.0);
        item.set("position.z", 3.0);

        tracker.sync();
        tracker.assertChanged("ITEM", 0, 1);
        tracker.assertSynchronized("ENTITY");
        tracker.assertNone();
    }

    private static ConfigurationNode addAttachment(ConfigurationNode parent, String type) {
        ConfigurationNode attachment = createAttachment(type);
        parent.getNodeList("attachments").add(attachment);
        return attachment;
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
        TestTracker t = new TestTracker(new AttachmentConfigTracker(config));
        t.start();
        return t;
    }

    public static AttachmentAssertion assertConfig(AttachmentConfig config) {
        return new AttachmentAssertion(config);
    }

    private static String pathStr(int[] path) {
        if (path.length == 0) {
            return "ROOT";
        }
        StringBuilder str = new StringBuilder();
        for (int p : path) {
            str.append('[').append(p).append(']');
        }
        return str.toString();
    }

    private static YamlPath makeYamlPath(int... path) {
        YamlPath yamlPath = YamlPath.ROOT;
        for (int childIndex : path) {
            yamlPath = yamlPath.child("attachments").child(Integer.toString(childIndex));
        }
        return yamlPath;
    }

    public static class AttachmentAssertion {
        private final AttachmentConfig attachment;
        private int childIndex = 0;

        public AttachmentAssertion(AttachmentConfig attachment) {
            this.attachment = attachment;
        }

        public AttachmentAssertion assertChild(String typeId) {
            return assertChild(typeId, unused -> {});
        }

        public AttachmentAssertion assertChild(String typeId, Consumer<AttachmentAssertion> childAssertions) {
            if (childIndex >= attachment.children().size()) {
                fail("Expected a child, but no more children");
            }

            // Check child has a valid path, too
            int[] expectedChildPath = attachment.childPath();
            expectedChildPath = Arrays.copyOf(expectedChildPath, expectedChildPath.length + 1);
            expectedChildPath[expectedChildPath.length - 1] = childIndex;

            // Check
            AttachmentAssertion child = new AttachmentAssertion(attachment.children().get(childIndex++));
            child.assertTypeId(typeId);
            child.assertPath(expectedChildPath);
            childAssertions.accept(child);
            return this;
        }

        public AttachmentAssertion assertConfig(Consumer<ConfigurationNode> assertFunction) {
            assertFunction.accept(attachment.config());
            return this;
        }

        public AttachmentAssertion assertNoMoreChildren() {
            if (childIndex < attachment.children().size()) {
                System.err.println("Unexpected child with Type Id " + attachment.children().get(childIndex).typeId());
                fail("Expected no more children, but there's more");
            }
            return this;
        }

        public AttachmentAssertion assertTypeId(String typeId) {
            assertEquals(typeId, attachment.typeId());
            return this;
        }

        public AttachmentAssertion assertEmptyConfig() {
            assertTrue(attachment.isEmptyConfig());
            return this;
        }

        public AttachmentAssertion assertNonEmptyConfig() {
            assertFalse(attachment.isEmptyConfig());
            return this;
        }

        public AttachmentAssertion assertPath(int... path) {
            if (!Arrays.equals(attachment.childPath(), path)) {
                System.err.println("Expected: " + pathStr(path));
                System.err.println("But got: " + pathStr(attachment.childPath()));
                fail("Expected child path " + pathStr(path) + ", but got " +
                        pathStr(attachment.childPath()));
            }
            assertEquals(makeYamlPath(path), attachment.path());
            return this;
        }

        public AttachmentAssertion assertNotAModel() {
            if (attachment instanceof AttachmentConfig.Model) {
                fail("Was a model attachment config, but it shouldn't be");
            }
            return this;
        }

        public AttachmentAssertion assertModelName(String modelName) {
            if (!(attachment instanceof AttachmentConfig.Model)) {
                fail("Expected a model attachment config, but wasn't");
            }
            assertEquals(modelName, ((AttachmentConfig.Model) attachment).modelName());
            return this;
        }
    }

    /**
     * Implements the tracker so the reported changes can be asserted against
     */
    public static class TestTracker implements AttachmentConfigListener {
        private final AttachmentConfigTrackerBase tracker;
        private final List<ChangeResult> changes = new ArrayList<>();

        public TestTracker(AttachmentConfigTrackerBase tracker) {
            this.tracker = tracker;
        }

        public AttachmentAssertion start() {
            return new AttachmentAssertion(this.tracker.startTracking(this));
        }

        public void sync() {
            tracker.sync();
        }

        @Override
        public void onChange(AttachmentConfig.Change change) {
            changes.add(new ChangeResult(change.changeType(), change.attachment()));
        }

        public AttachmentAssertion assertAdded(String typeId, int... path) {
            return assertOne(ChangeType.ADDED, typeId, path);
        }

        public AttachmentAssertion assertRemoved(String typeId, int... path) {
            return assertOne(ChangeType.REMOVED, typeId, path);
        }

        public AttachmentAssertion assertChanged(String typeId, int... path) {
            return assertOne(ChangeType.CHANGED, typeId, path);
        }

        public AttachmentAssertion assertSynchronized(String typeId) {
            return assertOne(ChangeType.SYNCHRONIZED, typeId);
        }

        public AttachmentAssertion assertOne(ChangeType change, String typeId, int... path) {
            if (changes.isEmpty()) {
                fail("Expected " + change + " "  + pathStr(path) + " but no changes happened");
                return null; // not reached
            }
            ChangeResult c = changes.remove(0);
            if (c.change != change || !Arrays.equals(c.path, path)) {
                System.err.println("Expected: " + change + " " + pathStr(path));
                System.err.println("But got: " + c.change + " " + pathStr(c.path));
                fail("Expected " + change + " "  + pathStr(path) + ", but got " +
                        c.change + " " + pathStr(c.path));
                return null; // not reached
            }

            assertEquals(typeId, c.attachment.typeId());

            // No use checking the path when handling REMOVED. Configuration already shouldn't
            // be used while handling it.
            if (change != ChangeType.REMOVED) {
                // Attachment yaml path should match what the child is
                // We assume only format 'attachments.1.attachments.5' is used
                assertEquals(makeYamlPath(path), c.attachment.path());
            }

            return new AttachmentAssertion(c.attachment);
        }

        public void assertNone() {
            if (!changes.isEmpty()) {
                ChangeResult c = changes.remove(0);
                fail("Expected no changes, but got " +
                        c.change + " " + pathStr(c.path));
            }
        }

        public void log() {
            System.err.println("Changes:");
            for (ChangeResult c : changes) {
                System.err.println("- " + c.change + " " + pathStr(c.path));
            }
        }

        private static class ChangeResult {
            private final ChangeType change;
            private final AttachmentConfig attachment;
            private final int[] path;

            public ChangeResult(ChangeType change, AttachmentConfig attachment) {
                this.change = change;
                this.attachment = attachment;
                this.path = attachment.childPath();
            }
        }
    }
}