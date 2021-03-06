/*
 * Copyright (c) 2014. Vlad Ilyushchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nfsdb.journal.column;

import com.nfsdb.journal.JournalMode;
import com.nfsdb.journal.collections.AbstractImmutableIterator;
import com.nfsdb.journal.exceptions.JournalException;
import com.nfsdb.journal.exceptions.JournalInvalidSymbolValueException;
import com.nfsdb.journal.exceptions.JournalRuntimeException;
import com.nfsdb.journal.index.Cursor;
import com.nfsdb.journal.index.KVIndex;
import com.nfsdb.journal.utils.ByteBuffers;
import com.nfsdb.journal.utils.Checksum;
import com.nfsdb.journal.utils.Lists;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;

public class SymbolTable implements Closeable {

    public static final int VALUE_NOT_FOUND = -2;
    public static final int VALUE_IS_NULL = -1;
    private static final String DATA_FILE_SUFFIX = ".symd";
    private static final String INDEX_FILE_SUFFIX = ".symi";
    private static final String HASH_INDEX_FILE_SUFFIX = ".symr";
    private static final float CACHE_LOAD_FACTOR = 0.2f;
    private final int hashKeyCount;
    private final String column;
    private final TObjectIntHashMap<String> valueCache;
    private final ArrayList<String> keyCache;
    private VariableColumn data;
    private KVIndex index;
    private int size;

    public SymbolTable(int capacity, int avgStringSize, int txCountHint, File directory, String column, JournalMode mode, int size, long indexTxAddress) throws JournalException {
        // number of hash keys stored in index
        // assume it is 20% of stated capacity
        this.hashKeyCount = Math.max(1, (int) (capacity * CACHE_LOAD_FACTOR));
        this.column = column;
        JournalMode m;

        switch (mode) {
            case BULK_APPEND:
                m = JournalMode.APPEND;
                break;
            case BULK_READ:
                m = JournalMode.READ;
                break;
            default:
                m = mode;
        }

        MappedFile dataFile = new MappedFileImpl(new File(directory, column + DATA_FILE_SUFFIX), ByteBuffers.getBitHint(avgStringSize * 2 + 4, capacity), m);
        MappedFile indexFile = new MappedFileImpl(new File(directory, column + INDEX_FILE_SUFFIX), ByteBuffers.getBitHint(8, capacity), m);

        this.data = new VariableColumn(dataFile, indexFile);
        this.size = size;

        this.index = new KVIndex(new File(directory, column + HASH_INDEX_FILE_SUFFIX), this.hashKeyCount, capacity, txCountHint, mode, indexTxAddress);
        this.valueCache = new TObjectIntHashMap<>(capacity, CACHE_LOAD_FACTOR, VALUE_NOT_FOUND);
        this.keyCache = new ArrayList<>(capacity);
    }

    public void applyTx(int size, long indexTxAddress) {
        this.size = size;
        this.index.setTxAddress(indexTxAddress);
    }

    public void alignSize() {
        this.size = (int) data.size();
    }

    public int put(String value) {

        int key = getQuick(value);
        if (key == VALUE_NOT_FOUND) {
            data.putString(value);
            data.commit();
            key = (int) (data.size() - 1);
            index.add(hashKey(value), key);
            size++;
            cache(key, value);
        }

        return key;
    }

    public int getQuick(String value) {
        int key = value == null ? VALUE_IS_NULL : this.valueCache.get(value);

        if (key != VALUE_NOT_FOUND) {
            return key;
        }

        int hashKey = hashKey(value);

        if (!index.contains(hashKey)) {
            return VALUE_NOT_FOUND;
        }

        Cursor cursor = index.cachedCursor(hashKey);
        while (cursor.hasNext()) {
            key = (int) cursor.next();
            if (data.equalsString(key, value)) {
                cache(key, value);
                return key;
            }
        }
        return VALUE_NOT_FOUND;
    }

    public int get(String value) {
        int result = getQuick(value);
        if (result == VALUE_NOT_FOUND) {
            throw new JournalInvalidSymbolValueException("Invalid value %s for symbol %s", value, column);
        } else {
            return result;
        }
    }

    public boolean valueExists(String value) {
        return getQuick(value) != VALUE_NOT_FOUND;
    }

    public String value(int key) {
        if (key >= size) {
            throw new JournalRuntimeException("Invalid symbol key: " + key);
        }
        String value = key < keyCache.size() ? keyCache.get(key) : null;
        if (value == null) {
            value = data.getString(key);
            cache(key, value);
        }
        return value;
    }

    public Iterable<String> values() {

        return new AbstractImmutableIterator<String>() {

            private long current = 0;
            private final long size = SymbolTable.this.size();

            @Override
            public boolean hasNext() {
                return current < size;
            }

            @Override
            public String next() {
                return data.getString(current++);
            }
        };
    }

    public int size() {
        return size;
    }

    public void close() {
        if (data != null) {
            data.close();
        }
        if (index != null) {
            index.close();
        }
        index = null;
        data = null;
    }

    public void truncate() {
        truncate(0);
    }

    public void truncate(int size) {
        if (size() > size) {
            data.truncate(size);
            index.truncate(size);
            data.commit();
            clearCache();
            this.size = size;
        }
    }

    public void updateIndex(int oldSize, int newSize) {
        if (oldSize < newSize) {
            for (int i = oldSize; i < newSize; i++) {
                index.add(hashKey(data.getString(i)), i);
            }
        }
    }

    public VariableColumn getDataColumn() {
        return data;
    }

    public SymbolTable preLoad() {
        for (int key = 0, size = (int) data.size(); key < size; key++) {
            String value = data.getString(key);
            valueCache.putIfAbsent(value, key);
            keyCache.add(value);

        }
        return this;
    }

    public long getIndexTxAddress() {
        return index.getTxAddress();
    }

    public void commit() {
        data.commit();
        index.commit();
    }

    public void force() {
        data.force();
        index.force();
    }

    private void cache(int key, String value) {
        valueCache.put(value, key);
        Lists.advance(keyCache, key);
        keyCache.set(key, value);
    }

    private void clearCache() {
        valueCache.clear();
        keyCache.clear();
    }

    private int hashKey(String value) {
        return Checksum.hash(value, hashKeyCount);
    }
}
