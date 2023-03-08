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
import java.util.function.Consumer;

/**
 * Verifies the various ways in which an attachment tree configuration can
 * be modified (by the editor) and that all of them fire the correct events.
 */
public class AttachmentConfigTrackerTest {

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
        tracker.assertChanged("WOO", 1); // Changed because we added the "attachments" field
                                                      // TODO: Do we try to fix this or nah?
        tracker.assertAdded("EMPTY", 1, 0)
               .assertChild("SEAT")
               .assertChild("ITEM")
               .assertNoMoreChildren();
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

    @Test
    public void testLegacyYamlLogicIsListening() {
        // Not visible by default so we got to do some weird shit
        try {
            // Prepare the reflection crap
            Object logic = Class.forName("com.bergerkiller.bukkit.tc.attachments.config.YamlLogic$YamlLogicLegacy")
                    .getConstructor().newInstance();
            java.lang.reflect.Method isListening = logic.getClass().getMethod("isListening",
                    ConfigurationNode.class, AttachmentConfigTracker.class);

            // Create a new tracker and start/stop listening
            // The isListening should reflect whether or not its still listening
            ConfigurationNode root = createAttachment("ITEM");
            AttachmentConfigTracker tracker = new AttachmentConfigTracker(root);
            AttachmentConfigListener tmpListener = new AttachmentConfigListener() {};
            tracker.startTracking(tmpListener);
            assertTrue((Boolean) isListening.invoke(logic, root, tracker));
            tracker.stopTracking(tmpListener);
            assertFalse((Boolean) isListening.invoke(logic, root, tracker));
            tracker.startTracking(tmpListener);
            assertTrue((Boolean) isListening.invoke(logic, root, tracker));
        } catch (Throwable t) {
            throw new IllegalStateException("Failed", t);
        }
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