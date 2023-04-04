/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.constant;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantUtils.validateClassOrInterface;
import static java.lang.constant.ConstantUtils.validateMemberName;
import static java.lang.constant.DirectMethodHandleDesc.Kind.CONSTRUCTOR;
import static java.util.Objects.requireNonNull;

/**
 * A <a href="package-summary.html#nominal">nominal descriptor</a> for a direct
 * {@link MethodHandle}.  A {@linkplain DirectMethodHandleDescImpl} corresponds to
 * a {@code Constant_MethodHandle_info} entry in the constant pool of a classfile.
 */
record DirectMethodHandleDescImpl(Kind kind, ClassDesc owner, String methodName, MethodTypeDesc invocationType)
        implements DirectMethodHandleDesc {

    /**
     * Constructs a {@linkplain DirectMethodHandleDescImpl} for a method or field
     * from a kind, owner, name, and type
     *
     * @param kind the kind of the method handle
     * @param owner the declaring class or interface for the method
     * @param methodName the unqualified name of the method (ignored if {@code kind} is {@code CONSTRUCTOR})
     * @param invocationType the lookup type of the method
     * @throws NullPointerException if any non-ignored argument is null
     * @throws IllegalArgumentException if {@code kind} describes a field accessor,
     * and {@code type} is not consistent with that kind of field accessor, or if
     * {@code kind} describes a constructor, and the return type of {@code type}
     * is not {@code void}
     * @jvms 4.2.2 Unqualified Names
     */
    DirectMethodHandleDescImpl {
        if (kind == CONSTRUCTOR)
            methodName = INIT_NAME;

        requireNonNull(kind);
        validateClassOrInterface(requireNonNull(owner));
        validateMemberName(requireNonNull(methodName), true);
        requireNonNull(invocationType);

        switch (kind) {
            case CONSTRUCTOR   -> validateConstructor(invocationType);
            case GETTER        -> validateFieldType(invocationType, false, true);
            case SETTER        -> validateFieldType(invocationType, true, true);
            case STATIC_GETTER -> validateFieldType(invocationType, false, false);
            case STATIC_SETTER -> validateFieldType(invocationType, true, false);
        }


        if (kind.isVirtualMethod())
            invocationType = invocationType.insertParameterTypes(0, owner);
        else if (kind == CONSTRUCTOR)
            invocationType = invocationType.changeReturnType(owner);
    }

    private static void validateFieldType(MethodTypeDesc type, boolean isSetter, boolean isVirtual) {
        boolean isVoid = type.returnType().descriptorString().equals("V");
        int expectedParams = (isSetter ? 1 : 0) + (isVirtual ? 1 : 0);
        if (isVoid != isSetter
            || type.parameterCount() != expectedParams
            || (isVirtual && type.parameterType(0).isPrimitive())) {
            String expectedType = String.format("(%s%s)%s", (isVirtual ? "R" : ""),
                                                (isSetter ? "T" : ""), (isSetter ? "V" : "T"));
            throw new IllegalArgumentException(String.format("Expected type of %s for getter, found %s", expectedType, type));
        }
    }

    private static void validateConstructor(MethodTypeDesc type) {
        if (!type.returnType().descriptorString().equals("V")) {
            throw new IllegalArgumentException(String.format("Expected type of (T*)V for constructor, found %s", type));
        }
    }

    @Override
    public int refKind() { return kind.refKind; }

    @Override
    public boolean isOwnerInterface() { return kind.isInterface; }

    @Override
    public String lookupDescriptor() {
        return switch (kind) {
            case VIRTUAL,
                 SPECIAL,
                 INTERFACE_VIRTUAL,
                 INTERFACE_SPECIAL        -> invocationType.dropParameterTypes(0, 1).descriptorString();
            case STATIC,
                 INTERFACE_STATIC         -> invocationType.descriptorString();
            case CONSTRUCTOR              -> invocationType.changeReturnType(CD_void).descriptorString();
            case GETTER,
                 STATIC_GETTER            -> invocationType.returnType().descriptorString();
            case SETTER                   -> invocationType.parameterType(1).descriptorString();
            case STATIC_SETTER            -> invocationType.parameterType(0).descriptorString();
            default -> throw new IllegalStateException(kind.toString());
        };
    }

    @Override
    public MethodHandle resolveConstantDesc(MethodHandles.Lookup lookup)
            throws ReflectiveOperationException {
        Class<?> resolvedOwner = (Class<?>) owner.resolveConstantDesc(lookup);
        MethodType invocationType = (MethodType) this.invocationType().resolveConstantDesc(lookup);
        return switch (kind) {
            case STATIC,
                 INTERFACE_STATIC           -> lookup.findStatic(resolvedOwner, methodName, invocationType);
            case VIRTUAL,
                 INTERFACE_VIRTUAL          -> lookup.findVirtual(resolvedOwner, methodName, invocationType.dropParameterTypes(0, 1));
            case SPECIAL,
                 INTERFACE_SPECIAL          -> lookup.findSpecial(resolvedOwner, methodName, invocationType.dropParameterTypes(0, 1), lookup.lookupClass());
            case CONSTRUCTOR                -> lookup.findConstructor(resolvedOwner, invocationType.changeReturnType(void.class));
            case GETTER                     -> lookup.findGetter(resolvedOwner, methodName, invocationType.returnType());
            case STATIC_GETTER              -> lookup.findStaticGetter(resolvedOwner, methodName, invocationType.returnType());
            case SETTER                     -> lookup.findSetter(resolvedOwner, methodName, invocationType.parameterType(1));
            case STATIC_SETTER              -> lookup.findStaticSetter(resolvedOwner, methodName, invocationType.parameterType(0));
            default -> throw new IllegalStateException(kind.name());
        };
    }

    @Override
    public String toString() {
        return String.format("MethodHandleDesc[%s/%s::%s%s]", kind, owner.displayName(), methodName, invocationType.displayDescriptor());
    }
}
