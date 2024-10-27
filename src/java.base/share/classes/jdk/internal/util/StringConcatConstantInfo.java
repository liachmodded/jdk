/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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


package jdk.internal.util;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

/**
 * Preprocessing result of string concatenation. The position of the arguments are
 * identified, and all independent constants are compiled into the "exploded" recipe
 * string. Note that a recipe string is a valid exploded string if there's no external
 * constants.
 * <p>
 * This class lives in {@code jdk.internal} because both java.lang and java.lang.invoke
 * need to access this class.
 *
 * @param argTags position of arguments
 * @param explodedRecipe the "exploded" recipe string with all constants inlined
 */
public record StringConcatConstantInfo(@Stable int[] argTags, String explodedRecipe) {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /**
     * Returns whether the constant at an index is empty.
     */
    public boolean isConstantEmpty(int i) {
        return endOf(i) == startOf(i);
    }

    /**
     * Returns the start index, inclusive, of a constant.
     * Useful for batch copying.
     */
    public int startOf(int i) {
        return i == 0 ? 0 : argTags[i - 1] + 1;
    }

    /**
     * Returns the end index, exclusive, of a constant.
     * Useful for batch copying.
     */
    public int endOf(int i) {
        return i == argTags.length ? explodedRecipe.length() : argTags[i];
    }

    /**
     * Extract a constant into a new string. Avoid if possible.
     */
    public String extractAt(int i) {
        return explodedRecipe.substring(startOf(i), endOf(i));
    }

    public int length() {
        return explodedRecipe.length() - argTags.length;
    }

    public byte coder() {
        return JLA.stringCoder(explodedRecipe);
    }
}
