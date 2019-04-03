/*******************************************************************************
 * Copyright 2019 University of Stuttgart, Germany
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package ogss.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * A print writer that will cache all input and write on close. If the result
 * exists already and the content is identical, the write is omitted. Also, the
 * writer is managed by a printing service that can be used to wipe a file
 * system directory from old files after code generation has completed.
 * 
 * @author Timm Felden
 *
 */
public class PrintWriter implements Closeable {
    final StringBuilder buffer;
    final File target;

    PrintWriter(File target, String header) {
        this.target = target;
        buffer = new StringBuilder(header);
    }

    public void write(String text) {
        buffer.append(text);
    }

    @Override
    public void close() throws IOException {
        String content;
        if (null != (content = checkPreexistingFile())) {
            target.getParentFile().mkdirs();
            try (java.io.PrintWriter out = new java.io.PrintWriter(target.getAbsolutePath(), "UTF-8")) {
                out.write(content);
            }
        }
    }

    /**
     * @return new content, if it has to be written or null if it is up-to-date
     * @note protected for testing
     * 
     * @throws IOException
     */
    String checkPreexistingFile() throws IOException {
        String rval = buffer.toString();
        if (target.exists()) {
            byte[] bytes = rval.getBytes();
            byte[] existing = Files.readAllBytes(target.toPath());

            if (Arrays.equals(bytes, existing))
                return null;
        }
        return rval;
    }

}
