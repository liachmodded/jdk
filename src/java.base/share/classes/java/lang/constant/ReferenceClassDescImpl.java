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

import java.lang.invoke.MethodHandles;

import static java.lang.constant.ConstantUtils.*;
import static java.util.Objects.requireNonNull;

/**
 * A <a href="package-summary.html#nominal">nominal descriptor</a> for a class,
 * interface, or array type.  A {@linkplain ReferenceClassDescImpl} corresponds to a
 * {@code Constant_Class_info} entry in the constant pool of a classfile.
 */
record ReferenceClassDescImpl(String descriptorString) implements ClassDesc {

    /**
     * Creates a {@linkplain ClassDesc} from a descriptor string for a class or
     * interface type or an array type.
     *
     * @throws IllegalArgumentException if the descriptor string is not a valid
     * field descriptor string, or does not describe a class or interface type
     * @jvms 4.3.2 Field Descriptors
     */
    ReferenceClassDescImpl {
        requireNonNull(descriptorString);
        int len = ConstantUtils.skipOverFieldSignature(descriptorString, 0, descriptorString.length(), false);
        if (len == 0 || len == 1
            || len != descriptorString.length())
            throw new IllegalArgumentException(String.format("not a valid reference type descriptor: %s",
                    descriptorString));
    }

    @Override
    public Class<?> resolveConstantDesc(MethodHandles.Lookup lookup)
            throws ReflectiveOperationException {
        if (isArray()) {
            if (isPrimitiveArray()) {
                return lookup.findClass(descriptorString);
            }
            // Class.forName is slow on class or interface arrays
            int depth = ConstantUtils.arrayDepth(descriptorString);
            Class<?> clazz = lookup.findClass(internalToBinary(descriptorString.substring(depth + 1,
                    descriptorString.length() - 1)));
            for (int i = 0; i < depth; i++)
                clazz = clazz.arrayType();
            return clazz;
        }
        return lookup.findClass(internalToBinary(dropFirstAndLastChar(descriptorString)));
    }

    /**
     * Whether the descriptor is one of a primitive array, given this is
     * already a valid reference type descriptor.
     */
    private boolean isPrimitiveArray() {
        // All L-type descriptors must end with a semicolon; same for reference
        // arrays, leaving primitive arrays the only ones without a final semicolon
        return descriptorString.charAt(descriptorString.length() - 1) != ';';
    }

    @Override
    public String toString() {
        return String.format("ClassDesc[%s]", displayName());
    }
}
