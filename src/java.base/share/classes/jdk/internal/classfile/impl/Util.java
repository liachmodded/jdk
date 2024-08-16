/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.FieldBuilder;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.Attributes;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.constant.ModuleDesc;
import java.lang.reflect.AccessFlag;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.components.ClassPrinter;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Helper to create and manipulate type descriptors, where type descriptors are
 * represented as JVM type descriptor strings and symbols are represented as
 * name strings
 */
public class Util {

    private Util() {
    }

    // Early bootstrap utilities
    public static Consumer<FieldBuilder> withFlags(int flags) {
        record WithFlags(int flags) implements Consumer<FieldBuilder> {
            @Override
            public void accept(FieldBuilder fb) {
                fb.withFlags(flags);
            }
        }
        return new WithFlags(flags);
    }

    public static Consumer<MethodBuilder> withCode(Consumer<? super CodeBuilder> handler) {
        record WithCode(Consumer<? super CodeBuilder> handler) implements Consumer<MethodBuilder> {
            @Override
            public void accept(MethodBuilder mb) {
                mb.withCode(handler);
            }
        }
        return new WithCode(handler);
    }

    public static <E> Consumer<Consumer<E>> passingAll(Iterable<E> container) {
        record ForEachConsumer<E>(Iterable<E> container) implements Consumer<Consumer<E>> {
            @Override
            public void accept(Consumer<E> consumer) {
                container.forEach(consumer);
            }
        }
        return new ForEachConsumer<>(container);
    }

    //

    private static final int ATTRIBUTE_STABILITY_COUNT = AttributeMapper.AttributeStability.values().length;

    public static boolean isAttributeAllowed(final Attribute<?> attr,
                                             final ClassFile.AttributesProcessingOption processingOption) {
        return attr instanceof BoundAttribute
                ? ATTRIBUTE_STABILITY_COUNT - attr.attributeMapper().stability().ordinal() > processingOption.ordinal()
                : true;
    }

    public static int parameterSlots(MethodTypeDesc mDesc) {
        int count = 0;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            count += slotSize(mDesc.parameterType(i));
        }
        return count;
    }

    public static int[] parseParameterSlots(int flags, MethodTypeDesc mDesc) {
        int[] result = new int[mDesc.parameterCount()];
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < result.length; i++) {
            result[i] = count;
            count += slotSize(mDesc.parameterType(i));
        }
        return result;
    }

    public static int maxLocals(int flags, MethodTypeDesc mDesc) {
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            count += slotSize(mDesc.parameterType(i));
        }
        return count;
    }

    /**
     * Converts a descriptor of classes or interfaces into
     * a binary name. Rejects primitive types or arrays.
     * This is an inverse of {@link ClassDesc#of(String)}.
     */
    public static String toBinaryName(ClassDesc cd) {
        return toInternalName(cd).replace('/', '.');
    }

    public static String toInternalName(ClassDesc cd) {
        var desc = cd.descriptorString();
        if (desc.charAt(0) == 'L')
            return desc.substring(1, desc.length() - 1);
        throw new IllegalArgumentException(desc);
    }

    public static ClassDesc toClassDesc(String classInternalNameOrArrayDesc) {
        return classInternalNameOrArrayDesc.charAt(0) == '['
                ? ClassDesc.ofDescriptor(classInternalNameOrArrayDesc)
                : ClassDesc.ofInternalName(classInternalNameOrArrayDesc);
    }

    public static<T, U> List<U> mappedList(List<? extends T> list, Function<T, U> mapper) {
        return new AbstractList<>() {
            @Override
            public U get(int index) {
                return mapper.apply(list.get(index));
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    public static List<ClassEntry> entryList(List<? extends ClassDesc> list) {
        var result = new Object[list.size()]; // null check
        for (int i = 0; i < result.length; i++) {
            result[i] = TemporaryConstantPool.INSTANCE.classEntry(list.get(i));
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(result);
    }

    public static List<ModuleEntry> moduleEntryList(List<? extends ModuleDesc> list) {
        var result = new Object[list.size()]; // null check
        for (int i = 0; i < result.length; i++) {
            result[i] = TemporaryConstantPool.INSTANCE.moduleEntry(TemporaryConstantPool.INSTANCE.utf8Entry(list.get(i).name()));
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(result);
    }

    public static void checkKind(Opcode op, Opcode.Kind k) {
        if (op.kind() != k)
            throw new IllegalArgumentException(
                    String.format("Wrong opcode kind specified; found %s(%s), expected %s", op, op.kind(), k));
    }

    public static int flagsToBits(AccessFlag.Location location, Collection<AccessFlag> flags) {
        int i = 0;
        for (AccessFlag f : flags) {
            if (!f.locations().contains(location)) {
                throw new IllegalArgumentException("unexpected flag: " + f + " use in target location: " + location);
            }
            i |= f.mask();
        }
        return i;
    }

    public static int flagsToBits(AccessFlag.Location location, AccessFlag... flags) {
        int i = 0;
        for (AccessFlag f : flags) {
            if (!f.locations().contains(location)) {
                throw new IllegalArgumentException("unexpected flag: " + f + " use in target location: " + location);
            }
            i |= f.mask();
        }
        return i;
    }

    public static boolean has(AccessFlag.Location location, int flagsMask, AccessFlag flag) {
        return (flag.mask() & flagsMask) == flag.mask() && flag.locations().contains(location);
    }

    public static ClassDesc fieldTypeSymbol(NameAndTypeEntry nat) {
        return ((AbstractPoolEntry.NameAndTypeEntryImpl)nat).fieldTypeSymbol();
    }

    public static MethodTypeDesc methodTypeSymbol(NameAndTypeEntry nat) {
        return ((AbstractPoolEntry.NameAndTypeEntryImpl)nat).methodTypeSymbol();
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeAttribute(BufWriterImpl writer, Attribute<?> attr) {
        if (attr instanceof CustomAttribute<?> ca) {
            var mapper = (AttributeMapper<T>) ca.attributeMapper();
            mapper.writeAttribute(writer, (T) ca);
        } else {
            assert attr instanceof BoundAttribute || attr instanceof UnboundAttribute;
            ((Writable) attr).writeTo(writer);
        }
    }

    public static void writeAttributes(BufWriterImpl buf, List<? extends Attribute<?>> list) {
        buf.writeU2(list.size());
        for (var e : list) {
            writeAttribute(buf, e);
        }
    }

    static void writeList(BufWriterImpl buf, List<Writable> list) {
        buf.writeU2(list.size());
        for (var e : list) {
            e.writeTo(buf);
        }
    }

    public static int slotSize(ClassDesc desc) {
        return switch (desc.descriptorString().charAt(0)) {
            case 'V' -> 0;
            case 'D','J' -> 2;
            default -> 1;
        };
    }

    public static boolean isDoubleSlot(ClassDesc desc) {
        char ch = desc.descriptorString().charAt(0);
        return ch == 'D' || ch == 'J';
    }

    public static void dumpMethod(SplitConstantPool cp,
                                  ClassDesc cls,
                                  String methodName,
                                  MethodTypeDesc methodDesc,
                                  int acc,
                                  ByteBuffer bytecode,
                                  Consumer<String> dump) {

        // try to dump debug info about corrupted bytecode
        try {
            var cc = ClassFile.of();
            var clm = cc.parse(cc.build(cp.classEntry(cls), cp, clb ->
                    clb.withMethod(methodName, methodDesc, acc, mb ->
                            ((DirectMethodBuilder)mb).writeAttribute(new UnboundAttribute.AdHocAttribute<CodeAttribute>(Attributes.code()) {
                                @Override
                                public void writeBody(BufWriterImpl b) {
                                    b.writeU2(-1);//max stack
                                    b.writeU2(-1);//max locals
                                    b.writeInt(bytecode.limit());
                                    b.writeBytes(bytecode.array(), 0, bytecode.limit());
                                    b.writeU2(0);//exception handlers
                                    b.writeU2(0);//attributes
                                }
                    }))));
            ClassPrinter.toYaml(clm.methods().get(0).code().get(), ClassPrinter.Verbosity.TRACE_ALL, dump);
        } catch (Error | Exception _) {
            // fallback to bytecode hex dump
            bytecode.rewind();
            while (bytecode.position() < bytecode.limit()) {
                dump.accept("%n%04x:".formatted(bytecode.position()));
                for (int i = 0; i < 16 && bytecode.position() < bytecode.limit(); i++) {
                    dump.accept(" %02x".formatted(bytecode.get()));
                }
            }
        }
    }

    public static void writeListIndices(BufWriter writer, List<? extends PoolEntry> list) {
        writer.writeU2(list.size());
        for (PoolEntry info : list) {
            writer.writeIndex(info);
        }
    }

    public static boolean writeLocalVariable(BufWriterImpl buf, PseudoInstruction lvOrLvt) {
        return ((WritableLocalVariable) lvOrLvt).writeLocalTo(buf);
    }

    /**
     * A generic interface for objects to write to a
     * buf writer. Do not implement unless necessary,
     * as this writeTo is public, which can be troublesome.
     */
    interface Writable {
        void writeTo(BufWriterImpl writer);
    }

    interface WritableLocalVariable {
        boolean writeLocalTo(BufWriterImpl buf);
    }

    public static int utf8EntryHash(String desc) {
        return (desc.hashCode() - pow31(desc.length() - 1) * 'L' - ';') * INVERSE_31;
    }

    // k is at most 65536, length of Utf8 entry + 1
    public static int pow31(int k) {
        int r = 1;
        // calculate the power contribution from index-th octal digit
        // from least to most significant (right to left)
        // e.g. decimal 26=octal 32, power(26)=powerOctal(2,0)*powerOctal(3,1)
        for (int i = 0; i < SIGNIFICANT_OCTAL_DIGITS; i++) {
            r *= powerOctal(k & 7, i);
            k >>= 3;
        }
        return r;
    }

    // The inverse of 31 in Z/2^32Z* modulo group, a * INVERSE_31 * 31 = a
    static final int INVERSE_31 = 0xbdef7bdf;

    // k is at most 65536 = octal 200000, only consider 6 octal digits
    // Note: 31 powers repeat beyond 1 << 27, only 9 octal digits matter
    static final int SIGNIFICANT_OCTAL_DIGITS = 6;

    // for base k, storage is k * log_k(N)=k/ln(k) * ln(N)
    // k = 2 or 4 is better for space at the cost of more multiplications
    /**
     * The code below is as if:
     * {@snippet lang=java :
     * int[] powers = new int[7 * SIGNIFICANT_OCTAL_DIGITS];
     *
     * for (int i = 1, k = 31; i <= 7; i++, k *= 31) {
     *    int t = powers[powersIndex(i, 0)] = k;
     *    for (int j = 1; j < SIGNIFICANT_OCTAL_DIGITS; j++) {
     *        t *= t;
     *        t *= t;
     *        t *= t;
     *        powers[powersIndex(i, j)] = t;
     *    }
     * }
     * }
     * This is converted to explicit initialization to avoid bootstrap overhead.
     * Validated in UtilTest.
     */
    static final @Stable int[] powers = new int[] {
              0x1f,      0x3c1,     0x745f,    0xe1781,  0x1b4d89f, 0x34e63b41, 0x67e12cdf,
        0x94446f01, 0x50a9de01, 0x84304d01, 0x7dd7bc01, 0x8ca02b01, 0xff899a01, 0x25940901,
        0x4dbf7801, 0xe3bef001, 0xc1fe6801, 0xe87de001, 0x573d5801,  0xe3cd001,  0xd7c4801,
        0x54fbc001, 0xb9f78001, 0x2ef34001, 0xb3ef0001, 0x48eac001, 0xede68001, 0xa2e24001,
        0x67de0001, 0xcfbc0001, 0x379a0001, 0x9f780001,  0x7560001, 0x6f340001, 0xd7120001,
        0x3ef00001, 0x7de00001, 0xbcd00001, 0xfbc00001, 0x3ab00001, 0x79a00001, 0xb8900001,
    };

    static int powersIndex(int digit, int index) {
        return (digit - 1) + index * 7;
    }

    // (31 ^ digit) ^ (8 * index) = 31 ^ (digit * (8 ^ index))
    // digit: 0 - 7
    // index: 0 - SIGNIFICANT_OCTAL_DIGITS - 1
    private static int powerOctal(int digit, int index) {
        return digit == 0 ? 1 : powers[powersIndex(digit, index)];
    }
}
