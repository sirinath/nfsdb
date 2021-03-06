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

package com.nfsdb.journal.utils;

import com.nfsdb.journal.exceptions.JournalException;
import com.nfsdb.journal.exceptions.JournalRuntimeException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public final class Files {

    public static final Charset UTF_8;

    static {
        UTF_8 = Charset.forName("UTF-8");
    }

    public static boolean delete(File file) {
        try {
            deleteOrException(file);
            return true;
        } catch (JournalException e) {
            return false;
        }
    }

    public static void deleteOrException(File file) throws JournalException {
        if (!file.exists()) {
            return;
        }
        deleteDirContentsOrException(file);
        if (!file.delete()) {
            throw new JournalException("Cannot delete file %s", file);
        }
    }

    public static void deleteDirContentsOrException(File file) throws JournalException {
        if (!file.exists()) {
            return;
        }
        try {
            if (notSymlink(file)) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        deleteOrException(f);
                    }
                }
            }
        } catch (IOException e) {
            throw new JournalException("Cannot delete dir contents: %s", file, e);
        }
    }

    public static void writeStringToFile(File file, String s) throws JournalException {
        try {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(s.getBytes(UTF_8));
            }
        } catch (IOException e) {
            throw new JournalException("Cannot write to %s", e, file.getAbsolutePath());
        }
    }

    public static String readStringFromFile(File file) throws JournalException {
        try {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte buffer[] = new byte[(int) fis.getChannel().size()];
                byte b;
                int index = 0;
                while ((b = (byte) fis.read()) != -1) {
                    buffer[index++] = b;
                }
                return new String(buffer, UTF_8);
            }
        } catch (IOException e) {
            throw new JournalException("Cannot read from %s", e, file.getAbsolutePath());
        }
    }

    public static File makeTempDir() {
        File result;
        try {
            result = File.createTempFile("journal", "");
            deleteOrException(result);
            mkDirsOrException(result);
        } catch (Exception e) {
            throw new JournalRuntimeException("Exception when creating temp dir", e);
        }
        return result;
    }

    public static void mkDirsOrException(File dir) {
        if (!dir.mkdirs()) {
            throw new JournalRuntimeException("Cannot create temp directory: %s", dir);
        }
    }

    private Files() {
    } // Prevent construction.

    private static boolean notSymlink(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File must not be null");
        }
        if (File.separatorChar == '\\') {
            return true;
        }

        File fileInCanonicalDir;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        return fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile());
    }
}
