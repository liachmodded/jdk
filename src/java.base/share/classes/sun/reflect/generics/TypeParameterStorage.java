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
package sun.reflect.generics;

import jdk.internal.ValueBased;
import jdk.internal.classfile.Signature;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.List;

@ValueBased
public final class TypeParameterStorage {

    public static final TypeParameterStorage EMPTY = new TypeParameterStorage(Object.class, List.of());
    private final List<TypeVariable<?>> typeVars;

    public TypeParameterStorage(GenericDeclaration declaration, List<Signature.TypeParam> parameters) {
        List<TypeVariable<?>> typeVars;
        if (!parameters.isEmpty()) {
            int i = 0;
            TypeVariable<?>[] tvs = new TypeVariable<?>[parameters.size()];
            for (var tp : parameters) {
                tvs[i] = new TypeFactory.TypeVariableImpl<>(declaration, i, tp);
                i++;
            }
            typeVars = List.of(tvs);
        } else {
            typeVars = List.of();
        }
        this.typeVars = typeVars;
    }

    public TypeVariable<?> resolve(String key) {
        for (var tv : typeVars) {
            if (tv.getName().equals(key)) {
                return tv;
            }
        }

        return null;
    }

    public boolean isEmpty() {
        return typeVars.isEmpty();
    }

    public int count() {
        return typeVars.size();
    }

    @SuppressWarnings("unchecked")
    public <D extends GenericDeclaration> TypeVariable<D>[] toArray() {
        return (TypeVariable<D>[]) (typeVars.isEmpty() ? TypeFactory.EMPTY_TYPE_VARIABLES_ARRAY
                : typeVars.toArray(new TypeVariable<?>[0]));
    }

}
