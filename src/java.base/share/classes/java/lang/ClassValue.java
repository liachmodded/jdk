/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import jdk.internal.util.ReferencedKeyMap;

/**
 * Lazily associate a computed value with any {@code Class} object.
 * For example, if a dynamic language needs to construct a message dispatch
 * table for each class encountered at a message send call site,
 * it can use a {@code ClassValue} to cache information needed to
 * perform the message send quickly, for each class encountered.
 * <p>
 * The basic operation of a {@code ClassValue} is {@link #get get}, which
 * returns the associated value, initially created by an invocation to {@link
 * #computeValue computeValue}; multiple invocations may happen under race, but
 * exactly one value is associated to a {@code Class} and returned.
 * <p>
 * Another operation is {@link #remove remove}: it clears the associated value
 * (if it exists), and ensures the next associated value is computed with input
 * states up-to-date with the removal.
 * <p>
 * For a particular association, there is a total order for accesses to the
 * associated value.  Accesses are atomic; they include:
 * <ul>
 * <li>A read-only access by {@code get}</li>
 * <li>An attempt to associate the return value of a {@code computeValue} by
 * {@code get}</li>
 * <li>Clearing of an association by {@code remove}</li>
 * </ul>
 * A {@code get} call always include at least one access; a {@code remove} call
 * always has exactly one access; a {@code computeValue} call always happens
 * between two accesses.  This establishes the order of {@code computeValue}
 * calls with respect to {@code remove} calls and determines whether the
 * results of a {@code computeValue} can be successfully associated by a {@code
 * get}.
 *
 * @param <T> the type of the associated value
 * @author John Rose, JSR 292 EG
 * @since 1.7
 */
public abstract class ClassValue<T> {
    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected ClassValue() {
    }

    /**
     * Computes the value to associate to the given {@code Class}.
     * <p>
     * This method is invoked when the initial read-only access by {@link #get
     * get} finds no associated value.
     * <p>
     * If this method throws an exception, the initiating {@code get} call will
     * not attempt to associate a value, and may terminate by returning the
     * associated value if it exists, or by propagating that exception otherwise.
     * <p>
     * Otherwise, the value is computed and returned.  An attempt to associate
     * the return value happens, with one of the following outcomes:
     * <ul>
     * <li>The associated value is present; it is returned and no association
     * is done.</li>
     * <li>The most recent {@link #remove remove} call, if it exists, does not
     * happen-before (JLS {@jls 17.4.5}) the finish of the {@code computeValue}
     * that computed the value to associate.  A new invocation to {@code
     * computeValue}, which that {@code remove} call happens-before, will
     * re-establish this happens-before relationship.</li>
     * <li>Otherwise, this value is successfully associated and returned.</li>
     * </ul>
     *
     * @apiNote
     * A {@code computeValue} call may, due to class loading or other
     * circumstances, recursively call {@code get} or {@code remove} for the
     * same {@code type}.  The recursive {@code get}, if the recursion stops,
     * successfully finishes and this initiating {@code get} observes the
     * associated value from recursion.  The recursive {@code remove} is no-op,
     * since being on the same thread, the {@code remove} already happens-before
     * the finish of this {@code computeValue}; the result from this {@code
     * computeValue} still may be associated.
     *
     * @param type the {@code Class} to associate a value to
     * @return the newly computed value to associate
     * @see #get
     * @see #remove
     */
    protected abstract T computeValue(Class<?> type);

    /**
     * {@return the value associated to the given {@code Class}}
     * <p>
     * This method first performs a read-only access, and returns the associated
     * value if it exists.  Otherwise, this method tries to associate a value
     * from a {@link #computeValue computeValue} invocation until the associated
     * value exists, which could be associated by a competing thread.
     * <p>
     * This method may throw an exception from a {@code computeValue} invocation.
     * In this case, no association happens.
     *
     * @param type the {@code Class} to retrieve the associated value for
     * @throws NullPointerException if the argument is {@code null}
     * @see #remove
     * @see #computeValue
     */
    public T get(Class<?> type) {
        return getFromHashMap(type);
    }

    /**
     * Removes the associated value for the given {@code Class} and invalidates
     * all out-of-date computations.  If this association is subsequently
     * {@linkplain #get accessed}, this removal happens-before (JLS {@jls
     * 17.4.5}) the finish of the {@link #computeValue computeValue} call that
     * returned the associated value.
     *
     * @param type the type whose class value must be removed
     * @throws NullPointerException if the argument is {@code null}
     */
    public void remove(Class<?> type) {
        ClassValueMap map = getMap(type);
        map.removeAccess(this);
    }

    // Possible functionality for JSR 292 MR 1
    /*public*/ void put(Class<?> type, T value) {
        ClassValueMap map = getMap(type);
        map.forcedAssociateAccess(this, value);
    }

    //| --------
    //| Implementation...
    //| --------

    /** Called when the fast path of get fails, and cache reprobe also fails.
     */
    private T getFromHashMap(Class<?> type) {
        // The fail-safe recovery is to fall back to the underlying classValueMap.
        ClassValueMap map = getMap(type);
        var accessed = map.readAccess(this);
        if (isEntry(accessed)) {
            return unpackEntry(accessed);
        }

        RemovalToken token = (RemovalToken) accessed; // nullable
        for (; ; ) {
            T value;
            try {
                value = computeValue(type);
            } catch (Throwable ex) {
                // no value is associated, but there may be already associated
                // value. Return that if it exists.
                accessed = map.readAccess(this);
                if (isEntry(accessed)) {
                    return unpackEntry(accessed);
                }
                // report failure here, but allow other callers to try again
                if (ex instanceof RuntimeException rte) {
                    throw rte;
                } else {
                    throw ex instanceof Error err ? err : new Error(ex);
                }
            }
            // computeValue succeed, proceed to associate
            accessed = map.associateAccess(this, token, value);
            if (isEntry(accessed)) {
                return unpackEntry(accessed);
            }
            token = (RemovalToken) accessed; // retry
        }
    }

    /**
     * Private key for retrieval of this object from ClassValueMap.
     */
    private static final class Identity {
        /** Value stream for hashCode.  See similar structure in ThreadLocal. */
        private static final AtomicInteger nextHashCode = new AtomicInteger();

        /** Good for power-of-two tables.  See similar structure in ThreadLocal. */
        private static final int HASH_INCREMENT = 0x61c88647;

        /** Mask a hash code to be positive but not too large, to prevent wraparound. */
        private static final int HASH_MASK = (-1 >>> 2);

        /** Internal hash code for ConcurrentHashMap. */
        private final int hashCode = nextHashCode.getAndAdd(HASH_INCREMENT) & HASH_MASK;

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    /**
     * This ClassValue's identity, expressed as an opaque object.
     * The main object {@code ClassValue.this} is incorrect since
     * subclasses may override {@code ClassValue.equals}, which
     * could confuse keys in the ClassValueMap.
     */
    private final Identity identity = new Identity();

    /**
     * Besides a value (null masked), a "removal token" object,  including the
     * value {@code null}, can be present at a ClassValue-Class
     * coordinate.  A removal token indicates whether the value from a
     * computation is up-to-date; the value is up-to-date if the token is the
     * same before and after computation (no removal during this period), or if
     * the token is from the same thread (removed during computeValue).
     * {@code null} is the initial state, meaning all computations are valid.
     * Later tokens are always non-null, no matter if they replace existing
     * entries or outdated tokens.
     */
    private static final class RemovalToken {
        // Use thread ID, which presumably don't duplicate and is cheaper than WeakReference
        private final long actorId;

        private RemovalToken() {
            this.actorId = Thread.currentThread().threadId();
        }

        // Arguments are intentionally nullable, to allow initial tokens
        private static boolean allowsAssociation(RemovalToken current, RemovalToken start) {
            // No removal token after the initial can be null
            assert current != null || start == null : current + " : " + start;
            return current == start || current.actorId == Thread.currentThread().threadId();
        }
    }

    // Is this an entry instead of a removal token?
    static boolean isEntry(Object e) {
        return !(e == null || e instanceof RemovalToken);
    }

    // Unpacks masked null from the map.
    @SuppressWarnings("unchecked")
    static <T> T unpackEntry(Object e) {
        assert isEntry(e);
        return e == PRIVATE_OBJECT ? null : (T) e;
    }

    // Pack null to be masked for the map.
    static Object packEntry(Object e) {
        return e == null ? PRIVATE_OBJECT : e;
    }

    /** Return the backing map associated with this type. */
    private static ClassValueMap getMap(Class<?> type) {
        // racing type.classValueMap : null (blank) => unique ClassValueMap
        // if a null is observed, a map is created (lazily, synchronously, uniquely)
        // all further access to that map is synchronized
        ClassValueMap map = type.classValueMap;
        if (map != null)  return map;
        return initializeMap(type);
    }

    // Private object, used as a monitor and a null mask as null means no removal
    private static final Object PRIVATE_OBJECT = new Object();
    private static ClassValueMap initializeMap(Class<?> type) {
        ClassValueMap map;
        synchronized (PRIVATE_OBJECT) {  // private object to avoid deadlocks
            // happens about once per type
            if ((map = type.classValueMap) == null) {
                type.classValueMap = map = new ClassValueMap();
            }
        }
        return map;
    }

    /** A backing map for all ClassValues.
     *  Gives a fully serialized "true state" for each pair (ClassValue cv, Class type).
     *  The state may be assigned value or a removal token.
     */
    static final class ClassValueMap {
        private final ReferencedKeyMap<Identity, Object> map;

        /** Build a backing map for ClassValues.
         */
        ClassValueMap() {
            this.map = ReferencedKeyMap.create(false, ReferencedKeyMap.concurrentHashMapSupplier());
        }

        // A simple read access to this map, for the initial step of get or failure recovery.
        <T> Object readAccess(ClassValue<T> classValue) {
            return map.get(classValue.identity);
        }

        // An association attempt, for when a computeValue returns a value.
        <T> Object associateAccess(ClassValue<T> classValue, RemovalToken startToken, T value) {
            record Attempt(RemovalToken startToken, Object value) implements BiFunction<Identity, Object, Object> {
                @Override
                public Object apply(Identity identity, Object o) {
                    if (isEntry(o) || !RemovalToken.allowsAssociation((RemovalToken) o, startToken))
                        return o;
                    return packEntry(value);
                }
            }
            return map.compute(classValue.identity, new Attempt(startToken, value));
        }

        // A removal, requiring subsequent associations to be up-to-date with it.
        void removeAccess(ClassValue<?> classValue) {
            // Always put in a token to invalidate ongoing computations
            map.put(classValue.identity, new RemovalToken());
        }

        // A forced association.
        <T> void forcedAssociateAccess(ClassValue<T> classValue, T value) {
            map.put(classValue.identity, packEntry(value));
        }
    }
}
