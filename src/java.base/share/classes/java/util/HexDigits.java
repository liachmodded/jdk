/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.lang.invoke.MethodHandle;

import jdk.internal.vm.annotation.Stable;

/**
 * Digits class for hexadecimal digits.
 *
 * @since 21
 */
final class HexDigits implements Digits {
    /**
     * Each element of the array represents the ascii encoded
     * hex relative to its index, for example:<p>
     * <pre>
     *       0 -> '00' -> ('0' << 8) | '0' -> 0x3030
     *       1 -> '01' -> ('0' << 8) | '1' -> 0x3130
     *       2 -> '02' -> ('0' << 8) | '2' -> 0x3230
     *
     *     ...
     *
     *      10 -> '0a' -> ('0' << 8) | 'a' -> 0x3061
     *      11 -> '0b' -> ('0' << 8) | 'b' -> 0x3062
     *      12 -> '0c' -> ('0' << 8) | 'c' -> 0x3063
     *
     *     ...
     *
     *      26 -> '1a' -> ('1' << 8) | 'a' -> 0x3161
     *      27 -> '1b' -> ('1' << 8) | 'b' -> 0x3162
     *      28 -> '1c' -> ('1' << 8) | 'c' -> 0x3163
     *
     *     ...
     *
     *     253 -> 'fd' -> ('f' << 8) | 'd' -> 0x6664
     *     254 -> 'fe' -> ('f' << 8) | 'e' -> 0x6665
     *     255 -> 'ff' -> ('f' << 8) | 'f' -> 0x6666
     * </pre>
     * <p>use like this:
     * <pre>
     *     int v = 254;
     *
     *     char[] chars = new char[2];
     *
     *     short i = DIGITS[v]; // 26213
     *
     *     chars[0] = (char) (byte) (i >> 8); // 'f'
     *     chars[1] = (char) (byte) i;        // 'e'
     * </pre>
     */
    @Stable
    private static final short[] DIGITS;

    /**
     * Singleton instance of HexDigits.
     */
    static final Digits INSTANCE = new HexDigits();

    static {
        short[] digits = new short[16 * 16];

        for (int i = 0; i < 16; i++) {
            short hi = (short) ((i < 10 ? i + '0' : i - 10 + 'a') << 8);

            for (int j = 0; j < 16; j++) {
                short lo = (short) (j < 10 ? j + '0' : j - 10 + 'a');
                digits[(i << 4) + j] = (short) (hi | lo);
            }
        }

        DIGITS = digits;
    }

    /**
     * Constructor.
     */
    private HexDigits() {
    }

    /**
     * Return a big-endian packed integer for the 4 ASCII bytes for an input unsigned short value.
     */
    static int packDigits16(long b, int shift) {
        return (DIGITS[(int) (b >> (8 + shift)) & 0xff] << 16)
                | DIGITS[(int) (b >> shift) & 0xff];
    }

    /**
     * Return a big-endian packed long for the 8 ASCII bytes for an input unsigned int value.
     */
    static long packDigits32(long b, int shift) {
        return (((long) DIGITS[(int) (b >> (24 + shift)) & 0xff]) << 48)
                | (((long) DIGITS[(int) (b >> (16 + shift)) & 0xff]) << 32)
                | (DIGITS[(int) (b >> (8 + shift)) & 0xff] << 16)
                | DIGITS[(int) (b >> shift) & 0xff];
    }

    @Override
    public int digits(long value, byte[] buffer, int index,
                      MethodHandle putCharMH) throws Throwable {
        while ((value & ~0xFF) != 0) {
            int digits = DIGITS[(int) (value & 0xFF)];
            value >>>= 8;
            putCharMH.invokeExact(buffer, --index, digits & 0xFF);
            putCharMH.invokeExact(buffer, --index, digits >> 8);
        }

        int digits = DIGITS[(int) (value & 0xFF)];
        putCharMH.invokeExact(buffer, --index, digits & 0xFF);

        if (0xF < value) {
            putCharMH.invokeExact(buffer, --index, digits >> 8);
        }

        return index;
    }

    @Override
    public int size(long value) {
        return value == 0 ? 1 :
                67 - Long.numberOfLeadingZeros(value) >> 2;
    }
}
