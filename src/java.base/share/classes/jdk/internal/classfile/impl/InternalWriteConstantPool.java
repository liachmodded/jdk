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

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.TypeDescriptor;

import static java.util.Objects.requireNonNull;

public final class InternalWriteConstantPool extends SplitConstantPool {

    // Start symbol patches
    // They are unlikely to clash with common strings, so hash live objects

    private AbstractPoolEntry.LiveUtf8EntryImpl findSymbolUtf8(int hash, TypeDescriptor target, boolean internal) {
        EntryMap<PoolEntry> map = map();
        for (int token = map.firstToken(hash); token != -1;
             token = map.nextToken(hash, token)) {
            PoolEntry e = map.getElementByToken(token);
            if (e.tag() == ClassFile.TAG_UTF8
                    && e instanceof AbstractPoolEntry.LiveUtf8EntryImpl ce
                    && ce.hashCode() == hash
                    && ce.sym == target
                    && ce.internalNameLike == internal)
                return ce;
        }
        // No parent for sure
        return null;
    }

    @Override
    public Utf8Entry utf8Entry(ClassDesc desc) {
        return symbolUtf8Entry(desc, false);
    }

    @Override
    public Utf8Entry utf8Entry(MethodTypeDesc desc) {
        return symbolUtf8Entry(desc, false);
    }

    @Override
    public ClassEntry classEntry(ClassDesc desc) {
        if (requireNonNull(desc).isPrimitive()) {
            throw new IllegalArgumentException("Cannot be encoded as ClassEntry: " + desc.displayName());
        }
        var ret = classEntry(symbolUtf8Entry(desc, desc.isClassOrInterface()));
        ret.sym = desc;
        return ret;
    }

    @Override
    public MethodTypeEntry methodTypeEntry(MethodTypeDesc descriptor) {
        var ret = (AbstractPoolEntry.MethodTypeEntryImpl)methodTypeEntry(utf8Entry(descriptor));
        ret.sym = descriptor;
        return ret;
    }

    @Override
    public NameAndTypeEntry nameAndTypeEntry(String name, MethodTypeDesc type) {
        var ret = nameAndTypeEntry(utf8Entry(name), utf8Entry(type));
        ret.typeSym = type;
        return ret;
    }

    @Override
    public NameAndTypeEntry nameAndTypeEntry(String name, ClassDesc type) {
        var ret = nameAndTypeEntry(utf8Entry(name), utf8Entry(type));
        ret.typeSym = type;
        return ret;
    }

    public Utf8Entry symbolUtf8Entry(TypeDescriptor descriptor, boolean internalNameLike) {
        int hash = AbstractPoolEntry.hash2(ClassFile.TAG_UNICODE, System.identityHashCode(descriptor), Boolean.hashCode(internalNameLike));
        var e = findSymbolUtf8(hash, descriptor, internalNameLike);
        return e == null ? internalAdd(new AbstractPoolEntry.LiveUtf8EntryImpl(this, size, descriptor, internalNameLike)) : e;
    }

    // end symbol patches
}
