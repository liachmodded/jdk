/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.EnumMap;
import java.util.Map;

/*
 * @test
 * @bug 8170826
 * @summary Tests for default method overrides in EnumMap
 * @run testng DefaultMethodsTest
 */
public class DefaultMethodsTest {

    enum MyEnum {
        FIRST, SECOND, THIRD
    }

    @DataProvider
    public Object[][] maps() {
        var map0 = new EnumMap<MyEnum, String>(MyEnum.class);
        map0.put(MyEnum.FIRST, "apple");
        map0.put(MyEnum.SECOND, null);
        return new Object[][]{{map0}};
    }

    @Test(dataProvider = "maps")
    public void testGetOrDefault(EnumMap<MyEnum, String> map) {
        String fallback = "Pie";
        Assert.assertEquals("apple", map.getOrDefault(MyEnum.FIRST, fallback),
                "Doesn't return non-null mapping value");
        Assert.assertNull(map.getOrDefault(MyEnum.SECOND, fallback),
                "Doesn't return null mapping value");
        Assert.assertEquals(fallback, map.getOrDefault(MyEnum.THIRD, fallback),
                "Doesn't return default for nonexistent mapping");
    }

    @Test(dataProvider = "maps")
    public void testGetOrDefault$NullKey(Map<Object, String> map) {
        String fallback = "Pie";
        Assert.assertEquals(fallback, map.getOrDefault(null, fallback),
                "null key doesn't return default");
    }

    @Test(dataProvider = "maps")
    public void testForEach(EnumMap<MyEnum, String> map) {
        EnumMap<MyEnum, String> compare = new EnumMap<>(map);
        MyEnum[] lastKey = {null};
        map.forEach((k, v) -> {
            if (lastKey[0] != null) {
                Assert.assertTrue(lastKey[0].compareTo(k) < 0, "Iteration not in natural order");
            }
            lastKey[0] = k;

            Assert.assertTrue(compare.remove(k, v));
        });
        Assert.assertEquals(0, compare.size(), "Some entries not iterated");
    }

    @Test(dataProvider = "maps")
    public void testReplaceAll(EnumMap<MyEnum, String> map) {
        Map<MyEnum, String> replaceResult = new EnumMap<>(MyEnum.class);
        map.keySet().forEach(k -> replaceResult.put(k, k == MyEnum.FIRST ? null : k.toString()));

        EnumMap<MyEnum, String> compare = new EnumMap<>(map);
        MyEnum[] lastKey = {null};
        map.replaceAll((k, v) -> {
            if (lastKey[0] != null) {
                Assert.assertTrue(lastKey[0].compareTo(k) < 0, "Iteration not in natural order");
            }
            lastKey[0] = k;
            Assert.assertTrue(compare.remove(k, v));

            return k == MyEnum.FIRST ? null : k.toString();
        });
        Assert.assertEquals(0, compare.size(), "Some entries not iterated");
        Assert.assertEquals(replaceResult, map);
    }

    @Test(dataProvider = "maps")
    public void testPutIfAbsent(EnumMap<MyEnum, String> map) {
        Assert.assertEquals("apple", map.putIfAbsent(MyEnum.FIRST, "pie"),
                "Non-null mapping not returned");
        Assert.assertEquals("apple", map.get(MyEnum.FIRST),
                "Non-null mapping incorrectly replaced");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertNull(map.putIfAbsent(MyEnum.SECOND, "soap"),
                "Mapping to null not replaced");
        Assert.assertEquals("soap", map.get(MyEnum.SECOND),
                "Mapping to null not replaced");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertNull(map.putIfAbsent(MyEnum.THIRD, "tree"),
                "New mapping not added");
        Assert.assertEquals("tree", map.get(MyEnum.THIRD),
                "New mapping not added");
        Assert.assertEquals(3, map.size(), "map size");
    }

    @Test(dataProvider = "maps", expectedExceptions = NullPointerException.class)
    public String testPutIfAbsent$NPE(EnumMap<MyEnum, String> map) {
        return map.putIfAbsent(null, "book");
    }

    @Test(dataProvider = "maps")
    public void testRemoveKeyValue(EnumMap<MyEnum, String> map) {
        Assert.assertFalse(map.remove(MyEnum.FIRST, "nonexistent"), "Removed nonexistent mapping");
        Assert.assertTrue(map.containsKey(MyEnum.FIRST), "Shouldn't erroneously remove");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertTrue(map.remove(MyEnum.FIRST, "apple"), "Failed to remove a mapping");
        Assert.assertFalse(map.containsKey(MyEnum.FIRST), "Not truly removed");
        Assert.assertEquals(1, map.size(), "map size");

        Assert.assertTrue(map.remove(MyEnum.SECOND, null), "Failed to remove mapping to null");
        Assert.assertFalse(map.containsKey(MyEnum.SECOND), "Not truly removed");
        Assert.assertEquals(0, map.size(), "map size");

        Assert.assertFalse(map.remove(MyEnum.THIRD, null), "Removed nonexistent mapping");
        Assert.assertEquals(0, map.size(), "map size");
    }

    @Test(dataProvider = "maps")
    public void testRemoveKeyValue$NullKey(Map<Object, String> map) {
        Assert.assertFalse(map.remove(null, "apple"),
                "Should return false for null key");
    }

    @Test(dataProvider = "maps")
    public void testReplaceMatchingValue(EnumMap<MyEnum, String> map) {
        Assert.assertFalse(map.replace(MyEnum.FIRST, "nonexistent", "fine"),
                "Removed nonexistent mapping");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertTrue(map.replace(MyEnum.FIRST, "apple", null),
                "Failed to update a mapping to null value");
        Assert.assertTrue(map.containsKey(MyEnum.FIRST), "Mapping was removed than replaced");
        Assert.assertNull(map.get(MyEnum.FIRST), "Mapping does not have null value");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertTrue(map.replace(MyEnum.SECOND, null, "spark"),
                "Failed to update mapping value from null to others");
        Assert.assertEquals("spark", map.get(MyEnum.SECOND),
                "Bad mapping value after replacement");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertFalse(map.replace(MyEnum.THIRD, null, "stuff"),
                "Replaced nonexistent mapping");
        Assert.assertFalse(map.containsKey(MyEnum.THIRD),
                "Replace should never add new mappings");
        Assert.assertEquals(2, map.size(), "map size");
    }

    @Test(dataProvider = "maps")
    public void testReplaceMatchingValue$NullKey(Map<Object, String> map) {
        Assert.assertFalse(map.replace(null, "apple", "pie"),
                "Should return false for null key");
    }

    @Test(dataProvider = "maps")
    public void testReplaceExisting(EnumMap<MyEnum, String> map) {
        Assert.assertEquals("apple", map.replace(MyEnum.FIRST, null),
                "Failed to update a mapping to null value");
        Assert.assertTrue(map.containsKey(MyEnum.FIRST), "Mapping was removed than replaced");
        Assert.assertNull(map.get(MyEnum.FIRST), "Mapping does not have null value");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertNull(map.replace(MyEnum.SECOND, "spark"),
                "Failed to update mapping value from null to others");
        Assert.assertEquals("spark", map.get(MyEnum.SECOND),
                "Bad mapping value after replacement");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertNull(map.replace(MyEnum.THIRD, "star"),
                "Replaced nonexistent mapping");
        Assert.assertFalse(map.containsKey(MyEnum.THIRD),
                "Replace should never add new mappings");
        Assert.assertEquals(2, map.size(), "map size");
    }

    @Test(dataProvider = "maps")
    public void testReplaceExisting$NullKey(EnumMap<MyEnum, String> map) {
        Assert.assertNull(map.replace(null, "pie"), "Replace null key should return null");
    }

    @Test(dataProvider = "maps")
    public void testComputeIfAbsent(EnumMap<MyEnum, String> map) {
        Assert.assertEquals("apple", map.computeIfAbsent(MyEnum.FIRST, k -> {
            throw new IllegalStateException("Non-null mapping not returned");
        }), "Non-null mapping not returned");
        Assert.assertEquals("apple", map.get(MyEnum.FIRST),
                "Non-null mapping incorrectly replaced");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertEquals("soap", map.computeIfAbsent(MyEnum.SECOND, k -> "soap"),
                "Mapping to null not replaced");
        Assert.assertEquals("soap", map.get(MyEnum.SECOND),
                "Mapping to null not replaced");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertEquals("tree", map.computeIfAbsent(MyEnum.THIRD, k -> "tree"),
                "New mapping not added");
        Assert.assertEquals("tree", map.get(MyEnum.THIRD),
                "New mapping not added");
        Assert.assertEquals(3, map.size(), "map size");
    }

    @Test(dataProvider = "maps", expectedExceptions = NullPointerException.class)
    public String testComputeIfAbsent$NPE(EnumMap<MyEnum, String> map) {
        return map.computeIfAbsent(null, k -> "book");
    }

    @Test(dataProvider = "maps")
    public void testComputeIfPresent(EnumMap<MyEnum, String> map) {
        Assert.assertEquals("spline", map.computeIfPresent(MyEnum.FIRST, (k, v) -> "spline"),
                "Replacing existing mapping");
        Assert.assertTrue(map.containsKey(MyEnum.FIRST),
                "Replaced key should be still in the map");
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertNull(map.computeIfPresent(MyEnum.FIRST, (k, v) -> null),
                "Failed to replace an existing mapping with removal");
        Assert.assertFalse(map.containsKey(MyEnum.FIRST), "Mapping was not removed");
        Assert.assertEquals(1, map.size(), "map size");

        Assert.assertNull(map.computeIfPresent(MyEnum.SECOND, (k, v) -> {
            throw new IllegalStateException("Mapping to null should be ignored");
        }), "Mapping to null should be ignored");
        Assert.assertTrue(map.containsKey(MyEnum.SECOND),
                "Null mapping should be still in the map");
        Assert.assertNull(map.get(MyEnum.SECOND),
                "Null mapping should be still in the map");
        Assert.assertEquals(1, map.size(), "map size");

        Assert.assertNull(map.computeIfPresent(MyEnum.THIRD, (k, v) -> {
            throw new IllegalStateException("Nonexistent mapping should be ignored");
        }), "Nonexistent mapping should be ignored");
        Assert.assertFalse(map.containsKey(MyEnum.THIRD),
                "Nonexistent key should be still absent in the map");
        Assert.assertEquals(1, map.size(), "map size");
    }

    @Test(dataProvider = "maps")
    public void testComputeIfPresent$NullKey(EnumMap<MyEnum, String> map) {
        Assert.assertNull(map.computeIfPresent(null, (k, v) -> "pie"),
                "Should return null for null key");
    }

    @Test(dataProvider = "maps")
    public void testCompute$Updating(EnumMap<MyEnum, String> map) {
        Assert.assertEquals("fine", map.compute(MyEnum.FIRST, (k, v) -> {
            Assert.assertEquals("apple", v, "Wrong initial value in map");
            return "fine";
        }));
        Assert.assertEquals("fine", map.get(MyEnum.FIRST));
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertEquals("fire", map.compute(MyEnum.SECOND, (k, v) -> {
            Assert.assertNull(v, "Wrong initial value for null values");
            return "fire";
        }));
        Assert.assertEquals("fire", map.get(MyEnum.SECOND));
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertEquals("grass", map.compute(MyEnum.THIRD, (k, v) -> {
            Assert.assertNull(v, "Wrong initial value for nonexistent mapping");
            return "grass";
        }));
        Assert.assertEquals("grass", map.get(MyEnum.THIRD));
        Assert.assertEquals(3, map.size(), "map size");
    }

    @Test(dataProvider = "maps")
    public void testCompute$Removing(EnumMap<MyEnum, String> map) {
        Assert.assertNull(map.compute(MyEnum.FIRST, (k, v) -> {
            Assert.assertEquals("apple", v, "Wrong initial value in map");
            return null;
        }));
        Assert.assertFalse(map.containsKey(MyEnum.FIRST));
        Assert.assertEquals(1, map.size(), "map size");

        Assert.assertNull(map.compute(MyEnum.SECOND, (k, v) -> {
            Assert.assertNull(v, "Wrong initial value for null values");
            return null;
        }));
        Assert.assertFalse(map.containsKey(MyEnum.SECOND));
        Assert.assertEquals(0, map.size(), "map size");

        Assert.assertNull(map.compute(MyEnum.THIRD, (k, v) -> {
            Assert.assertNull(v, "Wrong initial value for nonexistent mapping");
            return null;
        }));
        Assert.assertFalse(map.containsKey(MyEnum.THIRD));
        Assert.assertEquals(0, map.size(), "map size");
    }

    @Test(dataProvider = "maps", expectedExceptions = NullPointerException.class)
    public String testCompute$NPE(EnumMap<MyEnum, String> map) {
        return map.compute(null, (k, v) -> "book");
    }

    @Test(dataProvider = "maps")
    public void testMerge(EnumMap<MyEnum, String> map) {
        Assert.assertEquals("fineapple", map.merge(MyEnum.FIRST, "fine", (v0, v) -> {
            Assert.assertEquals("apple", v0, "Wrong initial value in map");
            Assert.assertEquals("fine", v, "Wrong value passed");
            return v + v0;
        }), "Regular merge");
        Assert.assertEquals("fineapple", map.get(MyEnum.FIRST));
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertEquals("fire", map.merge(MyEnum.SECOND, "fire", (v0, v) -> {
            throw new IllegalStateException("Merge with null value");
        }), "Merge with null value");
        Assert.assertEquals("fire", map.get(MyEnum.SECOND));
        Assert.assertEquals(2, map.size(), "map size");

        Assert.assertEquals("grass", map.merge(MyEnum.THIRD, "grass", (v0, v) -> {
            throw new IllegalStateException("Merge with nonexistent value");
        }), "Merge with nonexistent value");
        Assert.assertEquals("grass", map.get(MyEnum.THIRD));
        Assert.assertEquals(3, map.size(), "map size");

        Assert.assertNull(map.merge(MyEnum.FIRST, "fineapple", (v0, v) -> null),
                "Removal merge");
        Assert.assertFalse(map.containsKey(MyEnum.FIRST));
        Assert.assertEquals(2, map.size(), "map size");
    }

    @Test(dataProvider = "maps", expectedExceptions = NullPointerException.class)
    public String testMerge$NPE(EnumMap<MyEnum, String> map) {
        return map.merge(null, "ape", (ov, v) -> "book");
    }
}
