/*
 * Copyright (c) 2016 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * 	http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * 	http://www.eclipse.org/org/documents/edl-v10.html.
 */

package org.eclipse.milo.opcua.sdk.core;

import java.lang.reflect.Array;
import java.util.Arrays;

import com.google.common.base.MoreObjects;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

public final class NumericRange {

    private final String range;
    private final Bounds[] bounds;

    public NumericRange(String range, Bounds[] bounds) {
        this.range = range;
        this.bounds = bounds;
    }

    public String getRange() {
        return range;
    }

    public Bounds[] getBounds() {
        return bounds;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("range", range)
            .toString();
    }

    private int getDimensionCount() {
        return bounds.length;
    }

    private Bounds getBounds(int dimension) {
        return bounds[dimension - 1];
    }

    public static final class Bounds {
        private final int low;
        private final int high;

        private Bounds(int low, int high) throws UaException {
            if (low < 0 || high < 0 || low > high) throw new UaException(StatusCodes.Bad_IndexRangeInvalid);

            this.low = low;
            this.high = high;
        }
    }

    public static NumericRange parse(String range) throws UaException {
        try {
            String[] ss = range.split(",");
            Bounds[] bounds = new Bounds[ss.length];

            for (int i = 0; i < ss.length; i++) {
                String s = ss[i];
                String[] bs = s.split(":");

                if (bs.length == 1) {
                    int index = Integer.parseInt(bs[0]);
                    bounds[i] = new Bounds(index, index);
                } else if (bs.length == 2) {
                    int low = Integer.parseInt(bs[0]);
                    int high = Integer.parseInt(bs[1]);

                    if (low == high) throw new UaException(StatusCodes.Bad_IndexRangeInvalid);

                    bounds[i] = new Bounds(low, high);
                } else {
                    throw new UaException(StatusCodes.Bad_IndexRangeInvalid);
                }
            }

            return new NumericRange(range, bounds);
        } catch (Throwable ex) {
            throw new UaException(StatusCodes.Bad_IndexRangeInvalid, ex);
        }
    }

    public static Object readFromValueAtRange(Variant value, NumericRange range) throws UaException {
        Object array = value.getValue();

        if (array == null) {
            throw new UaException(StatusCodes.Bad_IndexRangeNoData);
        }

        if (!array.getClass().isArray()) {
            if (!(array instanceof String) && !(array instanceof ByteString)) {
                throw new UaException(StatusCodes.Bad_IndexRangeInvalid);
            }
        }

        try {
            return readFromValueAtRange(array, range, 1);
        } catch (Throwable ex) {
            throw new UaException(StatusCodes.Bad_IndexRangeNoData, ex);
        }
    }

    private static Object readFromValueAtRange(Object array, NumericRange range, int dimension) throws UaException {
        int dimensionCount = range.getDimensionCount();
        Bounds bounds = range.getBounds(dimension);
        int low = bounds.low, high = bounds.high;
        int len = high - low + 1;

        if (dimension == dimensionCount) {
            if (array.getClass().isArray()) {
                Class<?> type = array.getClass().getComponentType();
                Object a = Array.newInstance(type, len);

                for (int i = 0; i < len; i++) {
                    Object element = Array.get(array, low + i);
                    Array.set(a, i, element);
                }

                return a;
            } else if (array instanceof String) {
                String s = (String) array;

                return s.substring(low, high + 1);
            } else if (array instanceof ByteString) {
                ByteString bs = (ByteString) array;
                byte[] copy = Arrays.copyOfRange(bs.bytes(), low, high + 1);
                return new ByteString(copy);
            } else {
                throw new UaException(StatusCodes.Bad_IndexRangeNoData);
            }
        } else {
            Class<?> type = array.getClass().getComponentType();
            Object a = Array.newInstance(type, len);

            for (int i = 0; i < len; i++) {
                Object na = Array.get(array, low + i);
                Object element = readFromValueAtRange(na, range, dimension + 1);
                Array.set(a, i, element);
            }

            return a;
        }
    }

    public static Object writeToValueAtRange(Variant currentVariant, Variant updateVariant, NumericRange range) throws UaException {
        Object current = currentVariant.getValue();
        Object update = updateVariant.getValue();

        if (current == null || update == null) {
            throw new UaException(StatusCodes.Bad_IndexRangeNoData);
        }

        try {
            return writeToValueAtRange(current, update, range, 1);
        } catch (Throwable ex) {
            throw new UaException(StatusCodes.Bad_IndexRangeNoData, ex);
        }
    }

    private static Object writeToValueAtRange(Object current, Object update, NumericRange range, int dimension) throws UaException {
        int dimensionCount = range.getDimensionCount();
        Bounds bounds = range.getBounds(dimension);
        int low = bounds.low, high = bounds.high;

        if (dimension == dimensionCount) {
            if (current.getClass().isArray()) {
                Class<?> type = current.getClass().getComponentType();
                int length = Array.getLength(current);
                Object copy = Array.newInstance(type, length);

                if (low >= length || high >= length) {
                    throw new UaException(StatusCodes.Bad_IndexRangeNoData);
                }

                for (int i = 0; i < length; i++) {
                    if (i < low || i > high) {
                        Object element = Array.get(current, i);
                        Array.set(copy, i, element);
                    } else {
                        Object element = Array.get(update, i - low);
                        Array.set(copy, i, element);
                    }
                }

                return copy;
            } else if (current instanceof String) {
                String cs = (String) current;
                String us = (String) update;
                int length = cs.length();
                StringBuilder copy = new StringBuilder();

                if (low >= length || high >= length) {
                    throw new UaException(StatusCodes.Bad_IndexRangeNoData);
                }

                for (int i = 0; i < length; i++) {
                    if (i < low || i > high) {
                        copy.append(cs.charAt(i));
                    } else {
                        copy.append(us.charAt(i - low));
                    }
                }

                return copy.toString();
            } else if (current instanceof ByteString) {
                ByteString bs = (ByteString) current;
                ByteString us = (ByteString) update;
                int length = bs.length();
                byte[] copy = new byte[length];

                if (low >= length || high >= length) {
                    throw new UaException(StatusCodes.Bad_IndexRangeNoData);
                }

                for (int i = 0; i < length; i++) {
                    if (i < low || i > high) {
                        copy[i] = bs.byteAt(i);
                    } else {
                        copy[i] = us.byteAt(i - low);
                    }
                }

                return new ByteString(copy);
            } else {
                throw new UaException(StatusCodes.Bad_IndexRangeNoData);
            }
        } else {
            Class<?> type = current.getClass().getComponentType();
            int length = Array.getLength(current);
            Object copy = Array.newInstance(type, length);

            if (low >= length || high >= length) {
                throw new UaException(StatusCodes.Bad_IndexRangeNoData);
            }

            for (int i = 0; i < length; i++) {
                if (i < low || i > high) {
                    Object element = Array.get(current, i);
                    Array.set(copy, i, element);
                } else {
                    Object c = Array.get(current, i);
                    Object u = Array.get(update, i - low);
                    Object element = writeToValueAtRange(c, u, range, dimension + 1);
                    Array.set(copy, i, element);
                }
            }

            return copy;
        }
    }


}
