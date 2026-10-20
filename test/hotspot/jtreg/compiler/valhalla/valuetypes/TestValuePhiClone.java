/*
 * @test
 * @summary Exercise C2 value-type phi cloning with value fields referring to the phi
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @run driver compiler.valhalla.valuetypes.TestValuePhiClone
 */

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;

import static compiler.lib.ir_framework.IRNode.PHI;
import static compiler.lib.ir_framework.IRNode.VALUE_TYPE;

public class TestValuePhiClone {
    private static int expected;

    static value class Link {
        final Object previous;
        final int depth;

        Link(Object previous, int depth) {
            this.previous = previous;
            this.depth = depth;
        }
    }

    @Setup
    private static Object[] setup(SetupInfo info) {
        expected = info.invocationCounter() & 7;
        return new Object[]{expected};
    }

    @Test
    @Arguments(setup = "setup")
    @IR(phase = CompilePhase.AFTER_PARSING, counts = {VALUE_TYPE, "> 0", PHI, "> 0"})
    private static int test(int depth) {
        Link link = null;
        for (int index = 0; index < depth; index++) {
            link = new Link(link, index + 1);
        }
        return link == null ? 0 : link.depth;
    }

    @Check(test = "test")
    private static void check(int result) {
        if (result != expected) {
            throw new AssertionError("Unexpected depth: " + result + " != " + expected);
        }
    }

    public static void main(String[] args) {
        new TestFramework().addFlags("--enable-preview", "-XX:-TieredCompilation", "-Xbatch").start();
    }
}
