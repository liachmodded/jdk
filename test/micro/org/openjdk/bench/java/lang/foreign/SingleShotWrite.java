/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.SingleShotTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgs = { "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "-XX:TieredStopAtLevel=1" })
public class SingleShotWrite extends JavaLayouts {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    Arena arena;
    MemorySegment segment;
    long unsafe_addr;

    ByteBuffer byteBuffer;

    @Setup
    public void setup() {
        unsafe_addr = unsafe.allocateMemory(ALLOC_SIZE);
        arena = Arena.ofConfined();
        segment = arena.allocate(ALLOC_SIZE, 1);
        //VH_INT.set(segment, 0L, 0);
        byteBuffer = ByteBuffer.allocateDirect(ALLOC_SIZE).order(ByteOrder.nativeOrder());
    }

    @TearDown
    public void tearDown() {
        arena.close();
        unsafe.invokeCleaner(byteBuffer);
        unsafe.freeMemory(unsafe_addr);
    }

    @Benchmark
    public int unsafe() {
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(unsafe_addr + (i * CARRIER_SIZE) , i);
        }
        return unsafe.getInt(unsafe_addr);
    }

    @Benchmark
    public int segment() {
        for (int i = 0; i < ELEM_SIZE; i++) {
            VH_INT.set(segment, (long) i, i);
        }
        return (int) VH_INT.get(segment, 0L);
    }

    @Benchmark
    public int BB() {
        for (int i = 0; i < ELEM_SIZE; i++) {
            byteBuffer.putInt(i * CARRIER_SIZE , i);
        }
        return byteBuffer.getInt(0);
    }

}
