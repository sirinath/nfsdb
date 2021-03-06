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

package com.nfsdb.journal.lang.cst.impl.fltr;

import com.nfsdb.journal.column.AbstractColumn;
import com.nfsdb.journal.column.FixedColumn;
import com.nfsdb.journal.exceptions.JournalRuntimeException;
import com.nfsdb.journal.lang.cst.Choice;
import com.nfsdb.journal.lang.cst.PartitionSlice;
import com.nfsdb.journal.lang.cst.RowAcceptor;
import com.nfsdb.journal.lang.cst.RowFilter;

public class DoubleGreaterThanRowFilter implements RowFilter, RowAcceptor {
    private final String column;
    private final double value;
    private FixedColumn columnRef;

    public DoubleGreaterThanRowFilter(String column, double value) {
        this.column = column;
        this.value = value;
    }

    @Override
    public RowAcceptor acceptor(PartitionSlice a, PartitionSlice b) {
        AbstractColumn col = a.partition.getAbstractColumn(a.partition.getJournal().getMetadata().getColumnIndex(column));
        if (!(col instanceof FixedColumn)) {
            throw new JournalRuntimeException("Invalid column type");
        }
        columnRef = (FixedColumn) col;

        return this;
    }

    @Override
    public Choice accept(long localRowIDA, long localRowIDB) {
        return columnRef.getDouble(localRowIDA) > value ? Choice.PICK : Choice.SKIP;
    }
}
