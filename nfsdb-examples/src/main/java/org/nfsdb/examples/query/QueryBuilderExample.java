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

package org.nfsdb.examples.query;

import com.nfsdb.journal.Journal;
import com.nfsdb.journal.JournalWriter;
import com.nfsdb.journal.exceptions.JournalException;
import com.nfsdb.journal.factory.JournalFactory;
import com.nfsdb.journal.query.api.QueryAllBuilder;
import com.nfsdb.journal.utils.Dates;
import com.nfsdb.journal.utils.Files;
import org.joda.time.DateTime;
import org.nfsdb.examples.model.ModelConfiguration;
import org.nfsdb.examples.model.Quote;
import org.nfsdb.examples.support.QuoteGenerator;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class QueryBuilderExample {

    public static void main(String[] args) throws JournalException {
        if (args.length != 1) {
            System.out.println("Usage: " + QueryBuilderExample.class.getName() + " <path>");
            System.exit(1);
        }
        String journalLocation = args[0];

        try (JournalFactory factory = new JournalFactory(ModelConfiguration.CONFIG.build(journalLocation))) {

            // delete existing quote journal
            Files.delete(new File(factory.getConfiguration().getJournalBase(), "quote"));

            int count = 1000000;
            long t = System.nanoTime();

            // get some data in :)
            try (JournalWriter<Quote> w = factory.bulkWriter(Quote.class)) {
                QuoteGenerator.generateQuoteData(w, count, 90);
            }

            System.out.println("Created " + count + " records in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t) + "ms");

            try (Journal<Quote> journal = factory.reader(Quote.class)) {
                count = 0;
                t = System.nanoTime();
                // create query builder to search for all records with key (sym) = "BP.L"
                QueryAllBuilder<Quote> builder = journal.query().all().withKeys("BP.L");

                // execute query and consume result set
                for (Quote q : builder.asResultSet().bufferedIterator()) {
                    assert q != null;
                    count++;
                }
                System.out.println("Full read " + count + " records in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t) + "ms");

                //
                // reuse builder to narrow down query interval
                //
                DateTime lo = Dates.utc().plusDays(10);
                DateTime hi = lo.plusDays(10);
                t = System.nanoTime();
                count = 0;
                for (Quote q : builder.slice(Dates.interval(lo, hi)).asResultSet().bufferedIterator()) {
                    assert q != null;
                    count++;
                }
                System.out.println("Interval read " + count + " records in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t) + "ms");
            }
        }
    }
}
