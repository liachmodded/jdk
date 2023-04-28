/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.NameAndTypeEntry;
import jdk.internal.classfile.java.lang.constant.ModuleDesc;
import java.lang.reflect.AccessFlag;

import static jdk.internal.classfile.Classfile.ACC_STATIC;
import jdk.internal.access.SharedSecrets;

/**
 * Helper to create and manipulate type descriptors, where type descriptors are
 * represented as JVM type descriptor strings and symbols are represented as
 * name strings
 */
public class Util {

    private Util() {
    }

    public static String arrayOf(CharSequence s) {
        return "[" + s;
    }

    public static int parameterSlots(MethodTypeDesc mDesc) {
        int count = 0;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            count += isDoubleSlot(mDesc.parameterType(i)) ? 2 : 1;
        }
        return count;
    }

    public static int[] parseParameterSlots(int flags, MethodTypeDesc mDesc) {
        int[] result = new int[mDesc.parameterCount()];
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < result.length; i++) {
            result[i] = count;
            count += isDoubleSlot(mDesc.parameterType(i)) ? 2 : 1;
        }
        return result;
    }

    public static int maxLocals(int flags, MethodTypeDesc mDesc) {
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            count += isDoubleSlot(mDesc.parameterType(i)) ? 2 : 1;
        }
        return count;
    }

    public static String toClassString(String desc) {
        //TODO: this doesn't look right L ... ;
        return desc.replace('/', '.');
    }

    public static Iterator<String> parameterTypes(String s) {
        //TODO: gracefully non-method types
        return new Iterator<>() {
            int ch = 1;

            @Override
            public boolean hasNext() {
                return s.charAt(ch) != ')';
            }

            @Override
            public String next() {
                char curr = s.charAt(ch);
                switch (curr) {
                    case 'C', 'B', 'S', 'I', 'J', 'F', 'D', 'Z':
                        ch++;
                        return String.valueOf(curr);
                    case '[':
                        ch++;
                        return "[" + next();
                    case 'L': {
                        int start = ch;
                        while (s.charAt(++ch) != ';') { }
                        ++ch;
                        return s.substring(start, ch);
                    }
                    default:
                        throw new AssertionError("cannot parse string: " + s);
                }
            }
        };
    }

    public static String returnDescriptor(String s) {
        return s.substring(s.indexOf(')') + 1);
    }

    public static String toInternalName(ClassDesc cd) {
        var desc = cd.descriptorString();
        return switch (desc.charAt(0)) {
            case 'L' -> desc.substring(1, desc.length() - 1);
            default -> throw new IllegalArgumentException(desc);
        };
    }

    public static String toClassContent(ClassDesc cd) {
        return cd.isArray() ? cd.descriptorString() : toInternalName(cd);
    }

    public static ClassDesc toClassDesc(String classInternalNameOrArrayDesc) {
        return classInternalNameOrArrayDesc.charAt(0) == '['
                ? ClassDesc.ofDescriptor(classInternalNameOrArrayDesc)
                : ClassDesc.ofInternalName(classInternalNameOrArrayDesc);
    }

    private static int pow31(int pow) {
        int cur = 31;
        int res = 1;
        for (; pow > 0; pow /= 2) {
            if (pow % 2 == 1)
                res *= cur;
            cur *= cur;
        }
        return res;
    }

    private static int internalToDescHash(String internal) {
        return 'L' * pow31(internal.length() + 1) + internal.hashCode() * 31 + ';';
    }

    public static int hashClassContent(String classInfo) {
        return classInfo.charAt(0) == '[' ? classInfo.hashCode() : internalToDescHash(classInfo);
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
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArrayNullsAllowed(result);
    }

    public static List<ModuleEntry> moduleEntryList(List<? extends ModuleDesc> list) {
        var result = new Object[list.size()]; // null check
        for (int i = 0; i < result.length; i++) {
            result[i] = TemporaryConstantPool.INSTANCE.moduleEntry(TemporaryConstantPool.INSTANCE.utf8Entry(list.get(i).moduleName()));
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArrayNullsAllowed(result);
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

    public static boolean isDoubleSlot(ClassDesc desc) {
        char ch = desc.descriptorString().charAt(0);
        return ch == 'D' || ch == 'J';
    }
}
