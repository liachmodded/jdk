/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.io.UTFDataFormatException;

/**
 * Helper to JDK UTF putChar and Calculate length
 *
 * @since 24
 */
public abstract class ModifiedUtf {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private ModifiedUtf() {
    }

    @ForceInline
    public static int putChar(byte[] buf, int offset, char c) {
        if (c != 0 && c < 0x80) {
            buf[offset++] = (byte) c;
        } else if (c >= 0x800) {
            buf[offset    ] = (byte) (0xE0 | c >> 12 & 0x0F);
            buf[offset + 1] = (byte) (0x80 | c >> 6  & 0x3F);
            buf[offset + 2] = (byte) (0x80 | c       & 0x3F);
            offset += 3;
        } else {
            buf[offset    ] = (byte) (0xC0 | c >> 6 & 0x1F);
            buf[offset + 1] = (byte) (0x80 | c      & 0x3F);
            offset += 2;
        }
        return offset;
    }

    /**
     * Calculate the utf length of a string
     * @param str input string
     * @param countNonZeroAscii the number of non-zero ascii characters in the prefix calculated by JLA.countNonZeroAscii(str)
     */
    @ForceInline
    public static int utfLen(String str, int countNonZeroAscii) {
        int utflen = str.length();
        for (int i = utflen - 1; i >= countNonZeroAscii; i--) {
            int c = str.charAt(i);
            if (c >= 0x80 || c == 0)
                utflen += (c >= 0x800) ? 2 : 1;
        }
        return utflen;
    }

    /**
     * Checks if this is the 1st unsigned byte of a 1-byte value.
     */
    public static boolean is1Byte(int b0) {
        return (b0 >> 7) == 0;
    }

    /**
     * Checks if this is the 1st unsigned byte of a 2-byte value.
     */
    public static boolean is2Byte(int b0) {
        return (b0 >> 5) == 0b110;
    }

    /**
     * Reads a 2-byte value, which encodes 11 bits.
     *
     * @param b0 the first discriminator unsigned byte
     * @param buf the data array
     * @param offset the offset of discriminator byte
     * @param len the read limit of the array
     * @return the read char
     * @throws UTFDataFormatException if the format is invalid or if data ends abruptly
     */
    public static char read2Byte(int b0, byte[] buf, int offset, int len) throws UTFDataFormatException {
        /* 110x xxxx   10xx xxxx */
        if (offset + 1 >= len)
            throw partialAtEnd();
        int t = b0 << Byte.SIZE | (buf[offset + 1] & 0xFF);
        if ((t & 0b1110_0000_1100_0000) != 0b1100_0000_1000_0000)
            throw malformedAround(offset);

        return (char) Integer.compress(t, 0b0001_1111_0011_1111);
    }

    /**
     * Checks if this is the 1st unsigned byte of a 3-byte value.
     */
    public static boolean is3Byte(int b0) {
        return (b0 >> 4) == 0b1110;
    }

    /**
     * Reads a 3-byte value, which encodes 16 bits.
     *
     * @param b0 the first discriminator unsigned byte
     * @param buf the data array
     * @param offset the offset of discriminator byte
     * @param len the read limit of the array
     * @return the read char
     * @throws UTFDataFormatException if the format is invalid or if data ends abruptly
     */
    public static char read3Byte(int b0, byte[] buf, int offset, int len) throws UTFDataFormatException {
        /* 1110 xxxx  10xx xxxx  10xx xxxx */
        if (offset + 2 >= len)
            throw partialAtEnd();

        int t = b0 << Short.SIZE | (getU2Fast(buf, offset + 1) & 0xFFFF);
        if ((t & 0b1111_0000_1100_0000_1100_0000) != 0b1110_0000_1000_0000_1000_0000)
            throw malformedAround(offset);

        return (char) Integer.compress(t, 0b0000_1111_0011_1111_0011_1111);
    }

    public static UTFDataFormatException partialAtEnd() {
        return new UTFDataFormatException("malformed input: partial character at end");
    }

    public static UTFDataFormatException malformedAround(int offset) {
        return new UTFDataFormatException("malformed input around byte " + offset);
    }

    private static int getU2Fast(byte[] buf, int offset) {
        return UNSAFE.getCharUnaligned(buf, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, true);
    }
}
