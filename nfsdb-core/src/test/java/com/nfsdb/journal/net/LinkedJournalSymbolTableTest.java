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

package com.nfsdb.journal.net;

import com.nfsdb.journal.JournalWriter;
import com.nfsdb.journal.column.SymbolTable;
import com.nfsdb.journal.exceptions.JournalNetworkException;
import com.nfsdb.journal.model.RDFNode;
import com.nfsdb.journal.net.comsumer.JournalClientStateConsumer;
import com.nfsdb.journal.net.comsumer.JournalSymbolTableConsumer;
import com.nfsdb.journal.net.model.IndexedJournal;
import com.nfsdb.journal.net.producer.JournalClientStateProducer;
import com.nfsdb.journal.net.producer.JournalSymbolTableProducer;
import com.nfsdb.journal.test.tools.AbstractTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LinkedJournalSymbolTableTest extends AbstractTest {

    private final JournalClientStateProducer journalClientStateProducer = new JournalClientStateProducer();
    private final JournalClientStateConsumer journalClientStateConsumer = new JournalClientStateConsumer();
    private JournalWriter<RDFNode> origin;
    private JournalWriter<RDFNode> master;
    private JournalWriter<RDFNode> slave;
    private MockByteChannel channel;
    private JournalSymbolTableProducer journalSymbolTableProducer;
    private JournalSymbolTableConsumer journalSymbolTableConsumer;

    @Before
    public void setUp() throws Exception {
        origin = factory.writer(RDFNode.class, "origin");
        master = factory.writer(RDFNode.class, "master");
        slave = factory.writer(RDFNode.class, "slave");

        channel = new MockByteChannel();

        journalSymbolTableProducer = new JournalSymbolTableProducer(master);
        journalSymbolTableConsumer = new JournalSymbolTableConsumer(slave);

        origin.append(new RDFNode().setObj("O1").setSubj("S1"));
        origin.append(new RDFNode().setObj("O2").setSubj("S1"));
        origin.append(new RDFNode().setObj("O3").setSubj("S2"));
        origin.append(new RDFNode().setObj("S2").setSubj("S1"));
    }

    @Test
    public void testSameAsSymbolTable() throws Exception {
        master.append(origin);
        slave.append(origin.query().all().asResultSet().subset(0, 2));
        executeSequence(true);
    }

    private void compareSymbolTables() {
        for (int i = 0; i < master.getMetadata().getColumnCount(); i++) {
            SymbolTable m = master.getColumnMetadata(i).symbolTable;
            if (m != null) {
                SymbolTable s = slave.getColumnMetadata(i).symbolTable;
                for (String value : m.values()) {
                    Assert.assertEquals(m.getQuick(value), s.getQuick(value));
                }
            }
        }
    }

    private void executeSequence(boolean expectContent) throws JournalNetworkException {
        journalClientStateProducer.write(channel, new IndexedJournal(0, slave));
        journalClientStateConsumer.reset();
        journalClientStateConsumer.read(channel);

        journalSymbolTableProducer.configure(journalClientStateConsumer.getValue());
        Assert.assertEquals(expectContent, journalSymbolTableProducer.hasContent());
        if (expectContent) {
            journalSymbolTableProducer.write(channel);
            journalSymbolTableConsumer.reset();
            journalSymbolTableConsumer.read(channel);
            Assert.assertTrue(journalSymbolTableConsumer.isComplete());
            compareSymbolTables();
        }
    }
}
