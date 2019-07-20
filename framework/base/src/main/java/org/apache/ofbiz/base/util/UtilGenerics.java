/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.base.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UtilGenerics {

    public static final String module = UtilMisc.class.getName();

    private UtilGenerics() {}

    @SuppressWarnings("unchecked")
    public static <V> V cast(Object object) {
        return (V) object;
    }

    public static <T> Collection<T> checkCollection(Object object) {
        return cast(object);
    }

    public static <E, C extends Collection<E>> C checkCollection(Object object, Class<E> type) {
        if (object != null) {
            if (!(Collection.class.isInstance(object))) {
                throw new ClassCastException("Not a " + Collection.class.getName());
            }
            int i = 0;
            for (Object value: (Collection<?>) object) {
                if (value != null && !type.isInstance(value)) {
                    throw new IllegalArgumentException("Value(" + i + "), with value(" + value + ") is not a " + type.getName());
                }
                i++;
            }
        }
        return cast(object);
    }

    public static <T> List<T> checkList(Object object) {
        return cast(object);
    }

    public static <K, V> Map<K, V> checkMap(Object object) {
        return cast(object);
    }

    public static <K, V> Map<K, V> checkMap(Object object, Class<K> keyType, Class<V> valueType) {
        if (object != null) {
            if (!(object instanceof Map<?, ?>)) {
                throw new ClassCastException("Not a map");
            }
            Map<?, ?> map = (Map<?,?>) object;
            int i = 0;
            for (Map.Entry<?, ?> entry: map.entrySet()) {
                if (entry.getKey() != null && !keyType.isInstance(entry.getKey())) {
                    throw new IllegalArgumentException("Key(" + i + "), with value(" + entry.getKey() + ") is not a " + keyType);
                }
                if (entry.getValue() != null && !valueType.isInstance(entry.getValue())) {
                    throw new IllegalArgumentException("Value(" + i + "), with value(" + entry.getValue() + ") is not a " + valueType);
                }
                i++;
            }
        }
        return checkMap(object);
    }

    public static <T> Set<T> checkSet(Object object) {
        return cast(object);
    }

    /** Returns the Object argument as a parameterized List if the Object argument
     * is an instance of List. Otherwise returns null.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> toList(Object object) {
        if (object != null && !(object instanceof List)) {
            return null;
        }
        return (List<T>) object;
    }

    /** Returns the Object argument as a parameterized Map if the Object argument
     * is an instance of Map. Otherwise returns null.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> toMap(Object object) {
        if (object != null && !(object instanceof Map)) {
            return null;
        }
        return (Map<K, V>) object;
    }

    public static <K, V> Map<K, V> toMap(Class<K> keyType, Class<V> valueType, Object... data) {
        if (data == null) {
            return null;
        }
        if (data.length % 2 == 1) {
            throw new IllegalArgumentException("You must pass an even sized array to the toMap method");
        }
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < data.length;) {
            Object key = data[i];
            if (key != null && !(keyType.isInstance(key))) {
                throw new IllegalArgumentException("Key(" + i + ") is not a " + keyType.getName() + ", was(" + key.getClass().getName() + ")");
            }
            i++;
            Object value = data[i];
            if (value != null && !(valueType.isInstance(value))) {
                throw new IllegalArgumentException("Value(" + i + ") is not a " + keyType.getName() + ", was(" + key.getClass().getName() + ")");
            }
            i++;
            map.put(keyType.cast(key), valueType.cast(value));
        }
        return map;
    }

    @SafeVarargs
    @SuppressWarnings("hiding")
    public static <K, Object> Map<K, Object> toMap(Class<K> keyType, Object... data) {
        if (data == null) {
            return null;
        }
        if (data.length % 2 == 1) {
            throw new IllegalArgumentException("You must pass an even sized array to the toMap method");
        }
        Map<K, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < data.length;) {
            Object key = data[i];
            if (key != null && !(keyType.isInstance(key))) {
                throw new IllegalArgumentException("Key(" + i + ") is not a " + keyType.getName() + ", was(" + key.getClass().getName() + ")");
            }
            i++;
            Object value = data[i];
            map.put(keyType.cast(key), value);
        }
        return map;
    }
}
