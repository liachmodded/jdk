/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

package asmlib;

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static jdk.internal.classfile.Classfile.ACC_NATIVE;

/*
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.constantpool
 */
public class Instrumentor {

    public static Instrumentor instrFor(byte[] classData) {
        return new Instrumentor(classData);
    }

    private final ClassModel model;
    private ClassTransform transform = ClassTransform.ACCEPT_ALL;
    private final AtomicInteger matches = new AtomicInteger(0);

    private Instrumentor(byte[] classData) {
        model = Classfile.parse(classData);
    }

    public synchronized Instrumentor addMethodEntryInjection(String methodName, CodeTransform injector) {
        transform = transform.andThen(ClassTransform.transformingMethodBodies(mm -> {
            if (mm.methodName().equalsString(methodName)) {
                matches.getAndIncrement();
                return true;
            }
            return false;
        }, injector.andThen(CodeTransform.ACCEPT_ALL)));
        return this;
    }

    private ClassDesc className() {
        return model.thisClass().asSymbol();
    }

    public String name() {
        return model.thisClass().asInternalName();
    }

    public synchronized Instrumentor addNativeMethodTrackingInjection(String prefix, CodeTransform injector) {
        transform = transform.andThen(new ClassTransform() {
            private final Set<Consumer<ClassBuilder>> wmGenerators = new HashSet<>();

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                if (element instanceof MethodModel mm && mm.flags().has(AccessFlag.NATIVE)) {
                    matches.getAndIncrement();

                    String newName = prefix + name();
                    MethodTypeDesc mt = mm.methodTypeSymbol();
                    wmGenerators.add(clb -> clb.transformMethod(mm, (mb, me) -> {
                        switch (me) {
                            case AccessFlags flags -> mb.withFlags(flags.flagsMask() & ~ACC_NATIVE);
                            case CodeModel code ->
                                    mb.transformCode(code, injector.andThen(CodeTransform.endHandler(cb -> {
                                        int slot;
                                        boolean isStatic = mm.flags().has(AccessFlag.STATIC);
                                        if (!isStatic) {
                                            cb.aload(0);
                                            slot = 1;
                                        } else {
                                            slot = 0;
                                        }

                                        // load method parameters
                                        for (int i = 0; i < mt.parameterCount(); i++) {
                                            TypeKind kind = TypeKind.fromDescriptor(mt.parameterType(i).descriptorString());
                                            cb.loadInstruction(kind, slot);
                                            slot += kind.slotSize();
                                        }

                                        cb.invokeInstruction(isStatic ? Opcode.INVOKESTATIC : Opcode.INVOKESPECIAL,
                                                model.thisClass().asSymbol(), newName, mt, false);
                                        cb.returnInstruction(TypeKind.fromDescriptor(mt.returnType().descriptorString()));
                                    })));
                            default -> mb.with(me);
                        }
                    }));

                    builder.withMethod(newName, mt, mm.flags().flagsMask(), mm::forEachElement);
                } else {
                    builder.accept(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                wmGenerators.forEach(e -> e.accept(builder));
            }
        });
        return this;
    }

    public synchronized byte[] apply() {
        var bytes = model.transform(transform);

        return matches.get() == 0 ? null : bytes;
    }
}
