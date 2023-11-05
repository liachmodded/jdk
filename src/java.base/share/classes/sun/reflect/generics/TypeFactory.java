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
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.classfile.Signature;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.TypeAnnotationParser;
import sun.reflect.misc.ReflectUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;

public final class TypeFactory {
    private TypeFactory() {}

    public static final Type[] EMPTY_TYPE_ARRAY = new Type[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    public static final TypeVariable<?>[] EMPTY_TYPE_VARIABLES_ARRAY = new TypeVariable<?>[0];

    /**
     * Resolves a signature for a generic declaration.
     */
    public static Type resolve(GenericDeclaration decl, Signature signature) {
        return resolve(new ResolutionContext(decl), signature);
    }

    /**
     * Parameterizes the given type as a receiver "this" type.
     */
    public static Type parameterizeThis(Class<?> c) {
        var outerClass = c.getDeclaringClass();
        var typeParams = typeParameters(c);

        if (outerClass == null || c.accessFlags().contains(AccessFlag.STATIC)) {
            // Final level
            return typeParams.isEmpty() ? c : new ParameterizedTypeImpl(typeParams.toArray(), c, null);
        }

        Type outerType = parameterizeThis(outerClass);
        if (outerType instanceof Class && typeParams.isEmpty())
            return c;

        return new ParameterizedTypeImpl(typeParams.toArray(), c,
                // we don't care about plain outer classes, even if c is non-static
                outerType instanceof ParameterizedType pt ? pt : null);
    }

    // Additional APIs for GenericDeclaration

    /**
     * Resolves the outer declaration for this generic declaration; the type variables
     * in the outer declaration can appear in types in this declaration.
     */
    public static GenericDeclaration getOuterDeclaration(GenericDeclaration current) {
        if (current instanceof Class<?> cl) {
            var em = cl.getEnclosingMethod();
            if (em != null)
                return em;

            var ec = cl.getEnclosingConstructor();
            if (ec != null)
                return ec;

            return cl.getEnclosingClass();
        }

        if (current instanceof Executable exec) {
            return exec.getDeclaringClass();
        }

        throw unknownGenericDeclaration(current);
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaLangReflectAccess JLFA = SharedSecrets.getJavaLangReflectAccess();

    /**
     * Internal API for retrieving the type parameters in a generic declaration.
     */
    public static TypeParameterStorage typeParameters(GenericDeclaration decl) {
        if (decl instanceof Class<?> cl)
            return JLA.typeParameters(cl);
        return JLFA.typeParameters((Executable) decl);
    }

    // Type implementations

    @ValueBased
    record ResolutionContext(GenericDeclaration decl) {
        ResolutionContext {
            if (!(decl instanceof Class) && !(decl instanceof Executable))
                throw unknownGenericDeclaration(decl);
        }

        /**
         * Resolves a class in the current context by its binary name or "array name".
         * @throws TypeNotPresentException if a class is missing
         */
        public Class<?> resolveClass(String name) {
            try {
                return Class.forName(name, false, contextClass().getClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new TypeNotPresentException(name, ex);
            }
        }

        /**
         * Resolves a class nested in an outer class in the current context.
         * @throws TypeNotPresentException if a class is missing
         */
        public Class<?> resolveClass(Class<?> outer, String innerName) {
            return resolveClass(outer.getName() + "$" + innerName);
        }

        private Class<?> contextClass() {
            return decl instanceof Class<?> c ? c : ((Executable) decl).getDeclaringClass();
        }

        /**
         * Resolves a type variable in the current context. Also searches in
         * parent contexts.
         */
        public TypeVariable<?> resolveTypeVariable(String sig) {
            for (var decl = this.decl; decl != null; decl = getOuterDeclaration(decl)) {
                var found = typeParameters(decl).resolve(sig);
                if (found != null)
                    return found;
            }
            return null;
        }
    }

    static Type resolve(ResolutionContext context, Signature signature) {
        if (signature instanceof Signature.BaseTypeSig base) {
            return Wrapper.forPrimitiveType(base.baseType()).primitiveType();
        }
        if (signature instanceof Signature.ClassTypeSig cts) {
            return resolve(context, cts);
        }
        if (signature instanceof Signature.TypeVarSig tvs) {
            return context.resolveTypeVariable(tvs.identifier());
        }
        if (signature instanceof Signature.ArrayTypeSig ats) {
            var res = resolve(context, ats.componentSignature());
            return res instanceof Class<?> cl ? cl.arrayType() : new GenericArrayTypeImpl(res);
        }
        throw new IllegalArgumentException("Unknown signature " + signature);
    }

    private static Type resolve(ResolutionContext context, Signature.ClassTypeSig cts) {
        var parent = cts.outerType();
        Class<?> rawType;
        Type parentType;
        if (parent.isPresent()) {
            parentType = resolve(context, parent.get());
            var baseClass = parentType instanceof Class<?> c ? c : ((ParameterizedTypeImpl) parentType).rawType;
            rawType = context.resolveClass(baseClass, cts.className());
        } else {
            parentType = null;
            rawType = context.resolveClass(cts.className().replace('/', '.'));
        }

        var typeArgs = cts.typeArgs();
        // Prevent unnecessarily parameterizing a class
        if (!(parentType instanceof ParameterizedTypeImpl) && typeArgs.isEmpty())
            return rawType;

        Type[] arguments = new Type[typeArgs.size()];
        int i = 0;
        for (var typeArg : typeArgs) {
            arguments[i++] = resolve(context, typeArg);
        }

        return new ParameterizedTypeImpl(arguments, rawType, parentType);
    }

    static Type resolve(ResolutionContext context, Signature.TypeArg arg) {
        var wi = arg.wildcardIndicator();
        var innerType = wi == Signature.TypeArg.WildcardIndicator.UNBOUNDED ? Object.class
                : resolve(context, arg.boundType().orElseThrow());
        if (wi == Signature.TypeArg.WildcardIndicator.DEFAULT) {
            return innerType;
        }
        return new WildcardTypeImpl(innerType, wi == Signature.TypeArg.WildcardIndicator.SUPER);
    }

    private static <T> T[] defensiveCopy(T[] array) {
        return array.length == 0 ? array : array.clone();
    }

    private static IllegalArgumentException unknownGenericDeclaration(GenericDeclaration gd) {
        return new IllegalArgumentException("Unknown GenericDeclaration: " + gd);
    }

    static final class TypeVariableImpl<D extends GenericDeclaration> implements TypeVariable<D> {
        // TypeVariable's bounds must be resolved lazily: a TV may be used
        // to represent part of the signature, such as in <E extends Enum<E>>
        private volatile Signature.TypeParam rawLowerBounds; // accessed only in ctor and synchronized
        private @Stable List<Type> resolvedLowerBounds; // immutable list
        private final D declaration;
        private final int index;
        private final String name;

        public TypeVariableImpl(D declaration, int index,
                                Signature.TypeParam raw) {
            this.rawLowerBounds = raw;
            this.declaration = declaration;
            this.index = index;
            this.name = raw.identifier();
        }

        @Override
        public D getGenericDeclaration() {
            @SuppressWarnings("removal")
            var sm = System.getSecurityManager();
            if (sm != null) {
                if (declaration instanceof Class<?> c) {
                    ReflectUtil.checkPackageAccess(c);
                } else {
                    ReflectUtil.conservativeCheckMemberAccess((Executable) declaration);
                }
            }
            return declaration;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Type[] getBounds() {
            return lowerBounds().toArray(new Type[0]);
        }

        private List<Type> lowerBounds() {
            var lb = resolvedLowerBounds;
            if (lb != null) {
                return lb;
            }

            synchronized (this) {
                lb = resolvedLowerBounds;
                if (lb != null) {
                    return lb;
                }

                resolvedLowerBounds = lb = resolve(declaration, rawLowerBounds);
                rawLowerBounds = null;
            }

            return lb;
        }

        private static List<Type> resolve(GenericDeclaration decl, Signature.TypeParam raw) {
            var cb = raw.classBound();
            var ibs = raw.interfaceBounds();
            int size = ibs.size() + (cb.isEmpty() ? 0 : 1);
            Type[] resolved = new Type[size];
            int i = 0;
            var context = new ResolutionContext(decl);
            if (cb.isPresent()) {
                resolved[i++] = TypeFactory.resolve(context, cb.get());
            }
            for (var bound : ibs) {
                resolved[i++] = TypeFactory.resolve(context, bound);
            }
            return List.of(resolved);
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return TypeAnnotationParser.parseAnnotatedBounds(getBounds(), getGenericDeclaration(), index);
        }

        // Annotations
        private volatile Map<Class<? extends Annotation>, Annotation> computedDeclaredAnnotations;

        private Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
            Map<Class<? extends Annotation>, Annotation> declAnnos;
            if ((declAnnos = computedDeclaredAnnotations) == null) {
                synchronized (this) {
                    if ((declAnnos = computedDeclaredAnnotations) == null) {
                        computedDeclaredAnnotations = declAnnos = TypeAnnotationParser
                                .parseTypeVariableAnnotations(getGenericDeclaration(), index);
                    }
                }
            }
            return declAnnos;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            Objects.requireNonNull(annotationClass);
            return (T) declaredAnnotations().get(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return AnnotationParser.toArray(declaredAnnotations());
        }

        @Override
        public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
            Objects.requireNonNull(annotationClass);
            return AnnotationSupport.getDirectlyAndIndirectlyPresent(declaredAnnotations(), annotationClass);
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return getAnnotations();
        }

        @Override
        public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
            return getAnnotation(annotationClass);
        }

        @Override
        public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
            return getAnnotationsByType(annotationClass);
        }

        // Object methods

        @Override
        public int hashCode() {
            return Objects.hash(declaration, name);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            return obj instanceof TypeVariable<?> that
                    && Objects.equals(declaration, that.getGenericDeclaration())
                    && Objects.equals(name, that.getName());
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    record GenericArrayTypeImpl(Type component) implements GenericArrayType {
        GenericArrayTypeImpl {
            if (component instanceof Class || component instanceof WildcardType)
                throw new IllegalArgumentException("Invalid component type: " + component);
        }

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            return obj instanceof GenericArrayType that && component.equals(that.getGenericComponentType());
        }

        @Override
        public String toString() {
            return component.getTypeName() + "[]";
        }
    }

    static final class ParameterizedTypeImpl implements ParameterizedType {
        private final @Stable Type[] arguments;
        private final Class<?> rawType;
        private final Type ownerType;
        private @Stable String cachedToString;

        ParameterizedTypeImpl(Type[] arguments, Class<?> rawType, Type ownerType) {
            var formals = typeParameters(rawType);
            if (formals.count() != arguments.length) {
                throw new MalformedParameterizedTypeException(String.format("Mismatch of count of " +
                                "formal and actual type " +
                                "arguments in constructor " +
                                "of %s: %d formal argument(s) "+
                                "%d actual argument(s)",
                        rawType.getName(),
                        formals.count(),
                        arguments.length));
            }

            this.arguments = arguments;
            this.rawType = rawType;
            // note: we report that owner type exist for static nested classes like Map.Entry
            this.ownerType = ownerType == null ? rawType.getDeclaringClass() : ownerType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return defensiveCopy(arguments);
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            return o instanceof ParameterizedType that &&
                    Objects.equals(ownerType, that.getOwnerType()) &&
                    Objects.equals(rawType, that.getRawType()) &&
                    Arrays.equals(arguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(arguments), ownerType, rawType);
        }

        @Override
        public String toString() {
            var str = cachedToString;
            if (str != null)
                return str;
            return cachedToString = this.computeToString();
        }

        private String computeToString() {
            StringBuilder sb = new StringBuilder();

            if (ownerType instanceof ParameterizedTypeImpl pt) {
                // needs inserting
                sb.append(pt.getTypeName());
                var rawName = rawType.getName();
                // Copy our suffix, including $
                sb.append(rawName, pt.rawType.getName().length(), rawName.length());
            } else
                sb.append(rawType.getName());

            if (arguments.length > 0) {
                StringJoiner sj = new StringJoiner(", ", "<", ">");
                for (var t : arguments) {
                    sj.add(t.getTypeName());
                }
                sb.append(sj);
            }

            return sb.toString();
        }
    }

    static final class WildcardTypeImpl implements WildcardType {
        private final Type bound;
        private final boolean lower;

        WildcardTypeImpl(Type bound, boolean lower) {
            this.bound = bound;
            this.lower = lower;
        }

        @Override
        public Type[] getUpperBounds() {
            return new Type[] { lower ? Object.class : bound };
        }

        @Override
        public Type[] getLowerBounds() {
            return lower ? new Type[] { bound } : EMPTY_TYPE_ARRAY;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WildcardType that
                    && Arrays.equals(this.getLowerBounds(), that.getLowerBounds())
                    && Arrays.equals(this.getUpperBounds(), that.getUpperBounds());
        }

        @Override
        public int hashCode() {
            return Objects.hash(bound, lower);
        }

        @Override
        public String toString() {
            return lower ? "? super " + bound.getTypeName() :
                    bound == Object.class ? "?" : "? extends " + bound.getTypeName();
        }
    }

    // Lazy resolution support

    /**
     * A supplier that lazily resolves a type.
     */
    public static Supplier<Type> lazyType(GenericDeclaration context, Signature signature) {
        return new LazyResolution<>(context, signature) {
            @Override
            Type compute(Signature model) {
                return resolve(new ResolutionContext(gd), model);
            }
        };
    }

    /**
     * A supplier that lazily resolves a list of types.
     */
    public static Supplier<List<Type>> lazyTypeList(GenericDeclaration context, List<? extends Signature> signatures) {
        return new LazyResolution<List<? extends Signature>, List<Type>>(context, signatures) {
            @Override
            List<Type> compute(List<? extends Signature> model) {
                Type[] results = new Type[model.size()];
                int i = 0;
                var context = new ResolutionContext(gd);
                for (var s : model) {
                    results[i++] = resolve(context, s);
                }
                return List.of(results);
            }
        };
    }

    private static abstract class LazyResolution<T, R> implements Supplier<R> {
        final GenericDeclaration gd;
        private volatile T model; // Immutable object
        private @Stable R resolved; // Immutable object

        LazyResolution(GenericDeclaration gd, T model) {
            this.gd = gd;
            this.model = model;
        }

        @Override
        public final R get() {
            var resolved = this.resolved;
            if (resolved != null)
                return resolved;

            synchronized (this) {
                resolved = this.resolved;
                if (resolved != null)
                    return resolved;

                resolved = compute(model);
                this.resolved = resolved;
                model = null;
            }
            return resolved;
        }

        abstract R compute(T model);
    }
}
