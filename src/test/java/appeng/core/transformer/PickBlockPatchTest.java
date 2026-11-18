package appeng.core.transformer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.junit.jupiter.api.Test;

import com.google.common.io.ByteStreams;

/**
 * L-190: the pick block hook at the head of ForgeHooks.onPickBlock left a stray boolean on the operand stack for the
 * whole original body, and the class was rewritten with every frame recomputed through class loading. The
 * transform must work without a class loader and leave the original body starting with an empty stack.
 */
class PickBlockPatchTest {

    @Test
    void originalBodyStartsWithAnEmptyStack() throws Exception {
        final byte[] original;
        try (InputStream in = PickBlockPatchTest.class.getClassLoader()
                .getResourceAsStream("net/minecraftforge/common/ForgeHooks.class")) {
            assertNotNull(in);
            original = ByteStreams.toByteArray(in);
        }

        final byte[] patched = new AE2UDTransformer().transform("net.minecraftforge.common.ForgeHooks",
                "net.minecraftforge.common.ForgeHooks", original);

        final ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        MethodNode pick = null;
        for (final MethodNode m : node.methods) {
            if ("onPickBlock".equals(m.name)) {
                pick = m;
            }
        }
        assertNotNull(pick);

        final Frame<BasicValue>[] frames = new Analyzer<>(new BasicInterpreter()).analyze(node.name, pick);
        int returns = 0;
        for (int i = 0; i < pick.instructions.size(); i++) {
            final AbstractInsnNode insn = pick.instructions.get(i);
            if (insn.getOpcode() == Opcodes.IRETURN && ++returns == 1) {
                // The hook's own return; the original body starts at the next real instruction.
                for (int j = i + 1; j < pick.instructions.size(); j++) {
                    if (pick.instructions.get(j).getOpcode() >= 0) {
                        assertEquals(0, frames[j].getStackSize(), "stack height where the original body starts");
                        return;
                    }
                }
            }
        }
        throw new AssertionError("no hook return found in onPickBlock");
    }
}
