package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.generated.org.bukkit.block.SignHandle;
import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;

import com.bergerkiller.bukkit.common.bases.BlockStateBase;
import org.objectweb.asm.*;

import java.lang.reflect.Constructor;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.*;

/**
 * A fake Sign implementation that allows someone to implement the sign properties
 * (facing, lines of text, attached block). No actual sign has to exist at the
 * sign's position for the fake sign to be used by Traincarts.<br>
 * <br>
 * As it fakes all the behavior of a Sign, it should not be used in other Bukkit
 * API's to avoid problems.
 */
public abstract class FakeSign extends BlockStateBase implements Sign {
    private static final Function<Block, FakeSign> constructor = LogicUtil.tryCreate(FakeSign::createFakeSignConstructor,
            err -> block -> {
                throw new IllegalStateException("Failed to create FakeSign implementation", err);
            });

    public Handler handler;

    protected FakeSign(Block block) {
        super(block);
        this.handler = null;
    }

    /**
     * Creates a new FakeSign instance wrapping the Sign Block specified.
     * Caller should {@link #setHandler(Handler) set a handler} afterwards
     * before using the produced Fake Sign BlockState.
     *
     * @param signBlock SignBlock represented
     * @return new FakeSign instance
     */
    public static FakeSign create(Block signBlock) {
        if (signBlock == null) {
            throw new IllegalArgumentException("Sign block is null");
        }
        return constructor.apply(signBlock);
    }

    /**
     * Declares all the methods that must be implemented by a FakeSign
     */
    public interface Handler {
        String getFrontLine(int index);
        void setFrontLine(int index, String text);
        String getBackLine(int index);
        void setBackLine(int index, String text);
        default boolean update(boolean force, boolean applyPhysics) { return true; }
    }

    /**
     * Implements the Handler with a default behavior of reading the actual sign
     * state. Implementers can use the super call as a fallback.
     */
    public static class HandlerSignFallback implements Handler {
        private final Block signBlock;

        public HandlerSignFallback(Block signBlock) {
            this.signBlock = signBlock;
        }

        private SignHandle accessSign() {
            SignHandle sign = SignHandle.createHandle(BlockUtil.getSign(signBlock));
            if (sign == null) {
                throw new IllegalStateException("No sign is set at " + signBlock);
            } else {
                return sign;
            }
        }

        @Override
        public String getFrontLine(int index) {
            return accessSign().getFrontLine(index);
        }

        @Override
        public void setFrontLine(int index, String text) {
            SignHandle sign = accessSign();
            sign.setFrontLine(index, text);
            ((Sign) sign.getRaw()).update(true);
        }

        @Override
        public String getBackLine(int index) {
            return accessSign().getBackLine(index);
        }

        @Override
        public void setBackLine(int index, String text) {
            SignHandle sign = accessSign();
            sign.setBackLine(index, text);
            ((Sign) sign.getRaw()).update(true);
        }
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public Handler getHandler() {
        return this.handler;
    }

    @Override
    @Deprecated
    public String[] getLines() {
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = handler.getFrontLine(i);
        }
        return lines;
    }

    @Override
    @Deprecated
    public String getLine(int index) {
        return handler.getFrontLine(index);
    }

    @Override
    @Deprecated
    public void setLine(int index, String text) {
        handler.setFrontLine(index, text);
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        BlockState state = this.getBlock().getState();
        if (state instanceof PersistentDataHolder) {
            return ((PersistentDataHolder) state).getPersistentDataContainer();
        } else {
            return null;
        }
    }

    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public void setEditable(boolean editable) {
    }

    @Override
    public DyeColor getColor() {
        return null;
    }

    @Override
    public void setColor(DyeColor arg0) {
    }

    @Override
    public boolean isGlowingText() {
        return false;
    }

    @Override
    public void setGlowingText(boolean arg0) {
    }

    @Override
    public boolean update() {
        return handler.update(false, true);
    }

    @Override
    public boolean update(boolean force) {
        return handler.update(force, true);
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        return handler.update(force, applyPhysics);
    }

    private static Function<Block, FakeSign> createFakeSignConstructor() {
        Class<?> frontSideClass, backSideClass;
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            frontSideClass = generateFakeSignSide("Front");
            backSideClass = generateFakeSignSide("Back");
        } else {
            frontSideClass = backSideClass = null;
        }

        // Generate an implementation of BaseFakeSignImpl
        // Add the stuff that's needed depending on MC version run on
        ExtendedClassWriter<FakeSign> classWriter = ExtendedClassWriter.builder(FakeSign.class)
                .setExactName(FakeSign.class.getName() + "$Impl")
                .build();

        final String ctorDesc = "(" + MPLType.getDescriptor(Block.class) + ")V";

        // Legacy stuff is easy! But the class is abstract so we do have to proxy the constructor calls
        MethodVisitor methodVisitor;
        if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
            FieldVisitor fieldVisitor;
            final Class<?> signSideType = CommonUtil.getClass("org.bukkit.block.sign.SignSide");
            final String signSideDesc = MPLType.getDescriptor(signSideType);
            final String fakeSignDesc = MPLType.getDescriptor(FakeSign.class);

            // Add a constructor that calls super & initializes both SignSide instances as fields
            {
                fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "front", signSideDesc, null, null);
                fieldVisitor.visitEnd();
            }
            {
                fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "back", signSideDesc, null, null);
                fieldVisitor.visitEnd();
            }
            {
                methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", ctorDesc, null, null);
                methodVisitor.visitCode();
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(FakeSign.class), "<init>", ctorDesc, false);

                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitTypeInsn(NEW, MPLType.getInternalName(frontSideClass));
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(frontSideClass), "<init>", "(" + fakeSignDesc + ")V", false);
                methodVisitor.visitFieldInsn(PUTFIELD, classWriter.getInternalName(), "front", signSideDesc);

                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitTypeInsn(NEW, MPLType.getInternalName(backSideClass));
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(backSideClass), "<init>", "(" + fakeSignDesc + ")V", false);
                methodVisitor.visitFieldInsn(PUTFIELD, classWriter.getInternalName(), "back", signSideDesc);

                methodVisitor.visitInsn(RETURN);
                methodVisitor.visitMaxs(4, 2);
                methodVisitor.visitEnd();
            }

            // Implement getSide(Side)
            final Class<?> sideType = CommonUtil.getClass("org.bukkit.block.sign.Side");
            final String sideDesc = MPLType.getDescriptor(sideType);
            {
                methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getSide", "(" + sideDesc + ")" + signSideDesc, null, null);
                methodVisitor.visitCode();
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(GETSTATIC, MPLType.getInternalName(sideType), "FRONT", sideDesc);
                Label label0 = new Label();
                methodVisitor.visitJumpInsn(IF_ACMPNE, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, classWriter.getInternalName(), "front", signSideDesc);
                Label label1 = new Label();
                methodVisitor.visitJumpInsn(GOTO, label1);
                methodVisitor.visitLabel(label0);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, classWriter.getInternalName(), "back", signSideDesc);
                methodVisitor.visitLabel(label1);
                methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{MPLType.getInternalName(signSideType)});
                methodVisitor.visitInsn(ARETURN);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        } else {
            // Only add a proxy constructor, nothing more to implement
            {
                methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", ctorDesc, null, null);
                methodVisitor.visitCode();
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(FakeSign.class), "<init>", ctorDesc, false);
                methodVisitor.visitInsn(RETURN);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        }

        // Generate class & get constructor for it
        final Constructor<? extends FakeSign> ctor = classWriter.generateConstructor(Block.class);
        return block -> {
            try {
                return ctor.newInstance(block);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        };
    }

    private static Class<?> generateFakeSignSide(String sideName) {
        final Class<?> signSideType = CommonUtil.getClass("org.bukkit.block.sign.SignSide");
        ExtendedClassWriter<?> classWriter = ExtendedClassWriter.builder(signSideType)
                .setExactName(FakeSign.class.getName() + "$FakeSignSide" + sideName)
                .build();

        final String fakeSignName = MPLType.getInternalName(FakeSign.class);
        final String fakeSignDesc = MPLType.getDescriptor(FakeSign.class);

        FieldVisitor fieldVisitor;
        RecordComponentVisitor recordComponentVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        {
            fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "fakeSign", fakeSignDesc, null, null);
            fieldVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "(" + fakeSignDesc + ")V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitFieldInsn(PUTFIELD, classWriter.getInternalName(), "fakeSign", fakeSignDesc);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getLines", "()[Ljava/lang/String;", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitInsn(ICONST_4);
            methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/String");
            methodVisitor.visitVarInsn(ASTORE, 1);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitVarInsn(ISTORE, 2);
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitFrame(Opcodes.F_APPEND,2, new Object[] {"[Ljava/lang/String;", Opcodes.INTEGER}, 0, null);
            methodVisitor.visitVarInsn(ILOAD, 2);
            methodVisitor.visitInsn(ICONST_4);
            Label label1 = new Label();
            methodVisitor.visitJumpInsn(IF_ICMPGE, label1);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitVarInsn(ILOAD, 2);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ILOAD, 2);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, classWriter.getInternalName(), "getLine", "(I)Ljava/lang/String;", false);
            methodVisitor.visitInsn(AASTORE);
            methodVisitor.visitIincInsn(2, 1);
            methodVisitor.visitJumpInsn(GOTO, label0);
            methodVisitor.visitLabel(label1);
            methodVisitor.visitFrame(Opcodes.F_CHOP,1, null, 0, null);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(4, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getLine", "(I)Ljava/lang/String;", null, new String[] { "java/lang/IndexOutOfBoundsException" });
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, classWriter.getInternalName(), "fakeSign", fakeSignDesc);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, fakeSignName, "getHandler", "()" + MPLType.getDescriptor(Handler.class), false);
            methodVisitor.visitVarInsn(ILOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Handler.class), "get" + sideName + "Line", "(I)Ljava/lang/String;", true);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setLine", "(ILjava/lang/String;)V", null, new String[] { "java/lang/IndexOutOfBoundsException" });
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, classWriter.getInternalName(), "fakeSign", fakeSignDesc);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, fakeSignName, "getHandler", "()" + MPLType.getDescriptor(Handler.class), false);
            methodVisitor.visitVarInsn(ILOAD, 1);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Handler.class), "set" + sideName + "Line", "(ILjava/lang/String;)V", true);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(3, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "isGlowingText", "()Z", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setGlowingText", "(Z)V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 2);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getColor", "()" + MPLType.getDescriptor(DyeColor.class), null, null);
            methodVisitor.visitCode();
            methodVisitor.visitFieldInsn(GETSTATIC, MPLType.getInternalName(DyeColor.class), "BLACK", MPLType.getDescriptor(DyeColor.class));
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "setColor", "(" + MPLType.getDescriptor(DyeColor.class) + ")V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 2);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.generate();
    }
}
