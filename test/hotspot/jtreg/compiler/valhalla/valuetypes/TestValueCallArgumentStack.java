/*
 * @test
 * @summary Verify that C2 reads pre-popped value arguments from their original stack slots
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @modules jdk.incubator.vector
 * @run driver compiler.valhalla.valuetypes.TestValueCallArgumentStack
 */

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.IntVector;

public class TestValueCallArgumentStack {
    private static final int[] INPUT = new int[16];
    private static long expected;
    private static final String CALL_CONSUME = "(?m)^ *[0-9]+  CallStaticJava  ===[^\\r\\n]*::consume ";

    static {
        INPUT[0] = 23;
        INPUT[4] = 29;
    }

    static value class Inner {
        final int second;

        Inner(int second) {
            this.second = second;
        }
    }

    static value class Pair {
        final long first;
        final Inner inner;

        Pair(long first, Inner inner) {
            this.first = first;
            this.inner = inner;
        }
    }

    @DontInline
    private static long consume(double leading, Pair pair, IntVector vector, long trailing) {
        return (long) leading + pair.first * 7 + pair.inner.second * 11L
                + vector.lane(0) * 17L + trailing * 13;
    }

    @Setup
    private static Object[] setup(SetupInfo info) {
        int invocation = info.invocationCounter();
        boolean chooseFirst = (invocation & 1) == 0;
        expected = 3 + invocation * 7L + (invocation + 1) * 11L
                + (chooseFirst ? 23 : 29) * 17L + 65;
        return new Object[]{chooseFirst, (long) invocation, invocation + 1};
    }

    @Test
    @Arguments(setup = "setup")
    @IR(phase = CompilePhase.AFTER_PARSING, counts = {CALL_CONSUME, "= 1"})
    private static long test(boolean chooseFirst, long first, int second) {
        IntVector left = IntVector.fromArray(IntVector.SPECIES_128, INPUT, 0);
        IntVector right = IntVector.fromArray(IntVector.SPECIES_128, INPUT, 4);
        IntVector selected = chooseFirst ? left : right;
        return consume(3.0, new Pair(first, new Inner(second)), selected, 5);
    }

    @Check(test = "test")
    private static void check(long result) {
        if (result != expected) {
            throw new AssertionError("Unexpected value argument: " + result + " != " + expected);
        }
    }

    public static void main(String[] args) {
        new TestFramework().addFlags("--enable-preview", "--add-modules=jdk.incubator.vector",
                "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableVectorSupport", "-XX:+EnableVectorReboxing",
                "-XX:-TieredCompilation", "-Xbatch", "-XX:+ValueTypePassFieldsAsArgs",
                "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+DeoptimizeALot",
                "-XX:DeoptimizeALotInterval=1", "-XX:DeoptimizeOnlyAt=61").start();
    }
}
