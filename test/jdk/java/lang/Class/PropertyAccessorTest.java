/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Check exceptional behavior of Class.arrayType
 * @run junit ${test.main.class}
 */

import java.lang.annotation.Repeatable;
import java.lang.classfile.ClassFile;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.asmtools.jasm.Modifiers;

import static org.junit.jupiter.api.Assertions.*;

class PropertyAccessorTest {

    // Current accessors:
    // ACC_PUBLIC
    // ACC_PRIVATE
    // ACC_PROTECTED
    // ACC_STATIC
    // ACC_FINAL
    // ACC_INTERFACE
    // ACC_ABSTRACT
    // ACC_SYNTHETIC
    // ACC_ANNOTATION
    // ACC_ENUM

    // isAnnotation
    // isArray
    // isClassOrInterface
    // isInterface
    // isEnum
    // isPrimitive
    // isRecord

    // isHidden

    // isAnonymousClass
    // isLocalClass
    // isMemberClass

    // isSealed
    // isSynthetic


    @ValueSource(classes = {
            Comparable.class,
            Repeatable.class,
    })
    @ParameterizedTest
    void isInterfacePositive(Class<?> clazz) {
        assertTrue(clazz.isInterface());

        isClassOrInterfacePositive(clazz);
        isClassNegative(clazz);
        assertFalse(Modifiers.isFinal(clazz.getModifiers()));
        assertTrue(Modifiers.isAbstract(clazz.getModifiers()));
        assertTrue(!clazz.isMemberClass() || Modifier.isStatic(clazz.getModifiers()));

        assertNull(clazz.getSuperclass());
    }

    @ParameterizedTest
    void isInterfaceNegative(Class<?> clazz) {
        assertFalse(clazz.isInterface());

        assertFalse(clazz.isAnnotation());
    }

    void isClassPositive(Class<?> clazz) {
        // Only derived properties
        isClassOrInterfacePositive(clazz);
        isInterfaceNegative(clazz);
    }

    void isClassNegative(Class<?> clazz) {
        // Only derived properties
        assertFalse(clazz.isEnum());
        assertFalse(clazz.isRecord());
    }

    @ParameterizedTest
    void isClassOrInterfacePositive(Class<?> clazz) {
        assertTrue(clazz.isClassOrInterface());

        int impossibleMask = ClassFile.ACC_ABSTRACT | ClassFile.ACC_FINAL;
        assertNotEquals(impossibleMask, clazz.getModifiers() & impossibleMask);
        assertFalse(clazz.isPrimitive());
        assertFalse(clazz.isArray());
    }

    void isClassOrInterfaceNegative(Class<?> clazz) {
        assertFalse(clazz.isClassOrInterface());

        isClassNegative(clazz);
        isInterfaceNegative(clazz);
        int impossibleMask = ClassFile.ACC_ABSTRACT | ClassFile.ACC_FINAL;
        assertEquals(impossibleMask, clazz.getModifiers() & impossibleMask);
    }
}
