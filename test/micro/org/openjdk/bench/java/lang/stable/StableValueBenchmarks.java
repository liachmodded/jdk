package org.openjdk.bench.java.lang.stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.*;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "--enable-preview" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StableValueBenchmarks {
  private static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
      ValueLayout.JAVA_INT.withName(""),
      ValueLayout.JAVA_INT.withName("y")
  );

  private static final long SIZEOF = LAYOUT.byteSize();
  private static final long OFFSET_X = LAYOUT.byteOffset(groupElement(""));
  private static final long OFFSET_Y = LAYOUT.byteOffset(groupElement("y"));

  private static final VarHandle VH_X = LAYOUT.arrayElementVarHandle(groupElement(""))
      .withInvokeExactBehavior();
  private static final VarHandle VH_Y = LAYOUT.arrayElementVarHandle(groupElement("y"))
      .withInvokeExactBehavior();

  private static final Supplier<VarHandle> SV_X = StableValue.supplier(
      () -> LAYOUT.arrayElementVarHandle(groupElement("")).withInvokeExactBehavior());
  private static final Supplier<VarHandle> SV_Y = StableValue.supplier(
      () -> LAYOUT.arrayElementVarHandle(groupElement("y")).withInvokeExactBehavior());

  private static final Map<String, VarHandle> SMAP = StableValue.map(
      Set.of("", "y"),
      name -> LAYOUT.arrayElementVarHandle(groupElement(name)).withInvokeExactBehavior());

  private final MemorySegment confined;
  {
    var array = new int[512 * (int) SIZEOF / (int) ValueLayout.JAVA_INT.byteSize()];
    var heap = MemorySegment.ofArray(array);
    for(var i = 0; i < 512; i++) {
      heap.set(ValueLayout.JAVA_INT, i * SIZEOF + OFFSET_X, i);
      heap.set(ValueLayout.JAVA_INT, i * SIZEOF + OFFSET_Y, i);
    }
    confined = Arena.ofConfined().allocate(LAYOUT, 512);
    confined.copyFrom(heap);
  }

  @Benchmark
  public int confinedVarHandleLoop() {
    var sum = 0;
    for(var i = 0; i < 512; i++) {
      var x = (int) VH_X.get(confined, 0L, (long) i);
      var y = (int) VH_Y.get(confined, 0L, (long) i);
      sum += x +y;
    }
    return sum;
  }

  @Benchmark
  public int confinedStableValueLoop() {
    var sum = 0;
    for(var i = 0; i < 512; i++) {
      var x = (int) SV_X.get().get(confined, 0L, (long) i);
      var y = (int) SV_Y.get().get(confined, 0L, (long) i);
      sum += x +y;
    }
    return sum;
  }

  @Benchmark
  public int confinedStableMapLoop() {
    var sum = 0;
    for(var i = 0; i < 512; i++) {
      var x = (int) SMAP.get("").get(confined, 0L, (long) i);
      var y = (int) SMAP.get("y").get(confined, 0L, (long) i);
      sum += x +y;
    }
    return sum;
  }
}