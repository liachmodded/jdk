/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.MethodRefEntry;

import static jdk.internal.constant.ConstantUtils.classDesc;
import static jdk.internal.constant.ConstantUtils.methodTypeDesc;

class TypeConvertingMethodAdapter {

    private static class BoxHolder {
        private static final ConstantPoolBuilder CP = ConstantPoolBuilder.of();

        private static MethodRefEntry box(Class<?> primitive, Class<?> target) {
            return CP.methodRefEntry(classDesc(target), "valueOf", methodTypeDesc(MethodType.methodType(target, primitive)));
        }

        private static final MethodRefEntry BOX_BOOLEAN = box(boolean.class, Boolean.class),
                                            BOX_BYTE    = box(byte.class, Byte.class),
                                            BOX_SHORT   = box(short.class, Short.class),
                                            BOX_CHAR    = box(char.class, Character.class),
                                            BOX_INT     = box(int.class, Integer.class),
                                            BOX_LONG    = box(long.class, Long.class),
                                            BOX_FLOAT   = box(float.class, Float.class),
                                            BOX_DOUBLE  = box(double.class, Double.class);

        private static MethodRefEntry unbox(Class<?> owner, String methodName, Class<?> primitiveTarget) {
            return CP.methodRefEntry(classDesc(owner), methodName, methodTypeDesc(MethodType.methodType(primitiveTarget)));
        }

        private static final MethodRefEntry UNBOX_BOOLEAN = unbox(Boolean.class, "booleanValue", boolean.class),
                                            UNBOX_BYTE    = unbox(Number.class, "byteValue", byte.class),
                                            UNBOX_SHORT   = unbox(Number.class, "shortValue", short.class),
                                            UNBOX_CHAR    = unbox(Character.class, "charValue", char.class),
                                            UNBOX_INT     = unbox(Number.class, "intValue", int.class),
                                            UNBOX_LONG    = unbox(Number.class, "longValue", long.class),
                                            UNBOX_FLOAT   = unbox(Number.class, "floatValue", float.class),
                                            UNBOX_DOUBLE  = unbox(Number.class, "doubleValue", double.class);
    }

    private static TypeKind primitiveTypeKindFromClass(Class<?> type) {
        if (type == Integer.class)   return TypeKind.IntType;
        if (type == Long.class)      return TypeKind.LongType;
        if (type == Boolean.class)   return TypeKind.BooleanType;
        if (type == Short.class)     return TypeKind.ShortType;
        if (type == Byte.class)      return TypeKind.ByteType;
        if (type == Character.class) return TypeKind.CharType;
        if (type == Float.class)     return TypeKind.FloatType;
        if (type == Double.class)    return TypeKind.DoubleType;
        return null;
    }

    static void boxIfTypePrimitive(CodeBuilder cob, TypeKind tk) {
        box(cob, tk);
    }

    static void widen(CodeBuilder cob, TypeKind ws, TypeKind wt) {
        ws = ws.asLoadable();
        wt = wt.asLoadable();
        if (ws != wt) {
            cob.conversion(ws, wt);
        }
    }

    static void box(CodeBuilder cob, TypeKind tk) {
        switch (tk) {
            case BooleanType -> cob.invokestatic(BoxHolder.BOX_BOOLEAN);
            case ByteType    -> cob.invokestatic(BoxHolder.BOX_BYTE);
            case CharType    -> cob.invokestatic(BoxHolder.BOX_CHAR);
            case DoubleType  -> cob.invokestatic(BoxHolder.BOX_DOUBLE);
            case FloatType   -> cob.invokestatic(BoxHolder.BOX_FLOAT);
            case IntType     -> cob.invokestatic(BoxHolder.BOX_INT);
            case LongType    -> cob.invokestatic(BoxHolder.BOX_LONG);
            case ShortType   -> cob.invokestatic(BoxHolder.BOX_SHORT);
        }
    }

    static void unbox(CodeBuilder cob, TypeKind to) {
        switch (to) {
            case BooleanType -> cob.invokevirtual(BoxHolder.UNBOX_BOOLEAN);
            case ByteType    -> cob.invokevirtual(BoxHolder.UNBOX_BYTE);
            case CharType    -> cob.invokevirtual(BoxHolder.UNBOX_CHAR);
            case DoubleType  -> cob.invokevirtual(BoxHolder.UNBOX_DOUBLE);
            case FloatType   -> cob.invokevirtual(BoxHolder.UNBOX_FLOAT);
            case IntType     -> cob.invokevirtual(BoxHolder.UNBOX_INT);
            case LongType    -> cob.invokevirtual(BoxHolder.UNBOX_LONG);
            case ShortType   -> cob.invokevirtual(BoxHolder.UNBOX_SHORT);
        }
    }

    static void cast(CodeBuilder cob, Class<?> dt) {
        if (dt != Object.class) {
            cob.checkcast(classDesc(dt));
        }
    }

    /**
     * Convert an argument of type 'arg' to be passed to 'target' assuring that it is 'functional'.
     * Insert the needed conversion instructions in the method code.
     * @param arg
     * @param target
     * @param functional
     */
    static void convertType(CodeBuilder cob, Class<?> arg, Class<?> target, Class<?> functional) {
        if (arg.equals(target) && arg.equals(functional)) {
            return;
        }
        if (arg == Void.TYPE || target == Void.TYPE) {
            return;
        }
        if (arg.isPrimitive()) {
            if (target.isPrimitive()) {
                // Both primitives: widening
                widen(cob, TypeKind.from(arg), TypeKind.from(target));
            } else {
                // Primitive argument to reference target
                TypeKind wPrimTk = primitiveTypeKindFromClass(target);
                if (wPrimTk != null) {
                    // The target is a boxed primitive type, widen to get there before boxing
                    widen(cob, TypeKind.from(arg), wPrimTk);
                    box(cob, wPrimTk);
                } else {
                    // Otherwise, box and cast
                    box(cob, TypeKind.from(arg));
                    cast(cob, target);
                }
            }
        } else {
            Class<?> src;
            if (arg == functional || functional.isPrimitive()) {
                src = arg;
            } else {
                // Cast to convert to possibly more specific type, and generate CCE for invalid arg
                src = functional;
                cast(cob, functional);
            }
            if (target.isPrimitive()) {
                // Reference argument to primitive target
                TypeKind wps = primitiveTypeKindFromClass(src);
                if (wps != null) {
                    if (src != Character.class && src != Boolean.class) {
                        // Boxed number to primitive
                        unbox(cob, TypeKind.from(target));
                    } else {
                        // Character or Boolean
                        unbox(cob, wps);
                        widen(cob, wps, TypeKind.from(target));
                    }
                } else {
                    // Source type is reference type, but not boxed type,
                    // assume it is super type of target type
                    if (target == char.class) {
                        cast(cob, Character.class);
                    } else if (target == boolean.class) {
                        cast(cob, Boolean.class);
                    } else {
                        // Boxed number to primitive
                        cast(cob, Number.class);
                    }
                    unbox(cob, TypeKind.from(target));
                }
            } else {
                // Both reference types: just case to target type
                if (src != target) {
                    cast(cob, target);
                }
            }
        }
    }
}
