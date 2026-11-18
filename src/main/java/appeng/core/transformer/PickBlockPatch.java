package appeng.core.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Puts {@code appeng.hooks.ColorApplicatorPickBlock} at the head of {@code ForgeHooks.onPickBlock}:
 * {@code if (hook(target, player, world)) return true;}. Only that method is touched, and the stack and frame at
 * the jump target are the method's own entry state, so the rest of ForgeHooks keeps the frames it was read with.
 */
public class PickBlockPatch extends ClassVisitor {

    public PickBlockPatch(ClassVisitor cv) {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if ("onPickBlock".equals(name)) {
            return new OnPickBlockVisitor(mv);
        }
        return mv;
    }

    private static class OnPickBlockVisitor extends MethodVisitor implements Opcodes {

        public OnPickBlockVisitor(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "appeng/hooks/ColorApplicatorPickBlock",
                    "testColorApplicatorPickBlock",
                    "(Lnet/minecraft/util/math/RayTraceResult;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;)Z",
                    false);
            Label exitLabel = new Label();
            mv.visitJumpInsn(IFEQ, exitLabel);
            mv.visitInsn(ICONST_1);
            mv.visitInsn(IRETURN);
            mv.visitLabel(exitLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
    }
}
