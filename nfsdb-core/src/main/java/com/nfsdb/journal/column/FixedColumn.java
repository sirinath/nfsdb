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

import com.nfsdb.journal.utils.Unsafe;

public class FixedColumn extends AbstractColumn {
    protected final int width;

    public FixedColumn(MappedFile mappedFile, int width) {
        super(mappedFile);
        this.width = width;
    }

    public boolean getBool(long localRowID) {
        return Unsafe.getUnsafe().getByte(mappedFile.getAddress(getOffset(localRowID), 1)) == 1;
    }

    public byte getByte(long localRowID) {
        return Unsafe.getUnsafe().getByte(mappedFile.getAddress(getOffset(localRowID), 1));
    }

    public double getDouble(long localRowID) {
        return Unsafe.getUnsafe().getDouble(mappedFile.getAddress(getOffset(localRowID), 8));
    }

    public float getFloat(long localRowID) {
        return Unsafe.getUnsafe().getFloat(mappedFile.getAddress(getOffset(localRowID), 4));
    }

    public int getInt(long localRowID) {
        return Unsafe.getUnsafe().getInt(mappedFile.getAddress(getOffset(localRowID), 4));
    }

    public long getLong(long localRowID) {
        return Unsafe.getUnsafe().getLong(mappedFile.getAddress(getOffset(localRowID), 8));
    }

    public short getShort(long localRowID) {
        return Unsafe.getUnsafe().getShort(mappedFile.getAddress(getOffset(localRowID), 2));
    }

    public void putBool(boolean value) {
        Unsafe.getUnsafe().putByte(getAddress(), (byte) (value ? 1 : 0));
    }

    public void putByte(byte b) {
        Unsafe.getUnsafe().putByte(getAddress(), b);
    }

    public void copy(Object obj, long offset, long len) {
        Unsafe.getUnsafe().copyMemory(obj, offset, null, getAddress(), len);
    }

    public void putDouble(double value) {
        Unsafe.getUnsafe().putDouble(getAddress(), value);
    }

    public void putFloat(float value) {
        Unsafe.getUnsafe().putFloat(getAddress(), value);
    }

    public long putInt(int value) {
        Unsafe.getUnsafe().putInt(getAddress(), value);
        return txAppendOffset / width - 1;
    }

    public long putLong(long value) {
        Unsafe.getUnsafe().putLong(getAddress(), value);
        return txAppendOffset / width - 1;
    }

    public void putShort(short value) {
        Unsafe.getUnsafe().putShort(getAddress(), value);
    }

    public long putNull() {
        long appendOffset = mappedFile.getAppendOffset();
        mappedFile.getAddress(appendOffset, width);
        preCommit(appendOffset + width);
        return appendOffset;
    }

    @Override
    public long getOffset(long localRowID) {
        return localRowID * width;
    }

    @Override
    public long size() {
        return getOffset() / width;
    }

    @Override
    public void truncate(long size) {
        if (size < 0) {
            size = 0;
        }
        preCommit(size * width);
    }

    long getAddress() {
        long appendOffset = mappedFile.getAppendOffset();
        preCommit(appendOffset + width);
        return mappedFile.getAddress(appendOffset, width);
    }
}
