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
package ogss.backend.java

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

import scala.reflect.io.Path.jfile2path

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import ogss.backend.common
import ogss.main.CommandLine

/**
 * Generic tests built for Java.
 * Generic tests have an implementation for each programming language, because otherwise deleting the generated code
 * upfront would be ugly.
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class ReadTests extends common.GenericTests {

  override val language = "java"

  override def deleteOutDir(out : String) {
  }

  override def callMainFor(name : String, source : String, options : Seq[String]) {
    CommandLine.main(Array[String](
      "build",
      source,
      "--debug-header",
      "-c",
      "-L", "java",
      "-p", name,
      "-Ojava:SuppressWarnings=true",
      "-d", "testsuites/java/lib",
      "-o", "testsuites/java/src/main/java/"
    ) ++ options)
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter = {
    val f = new File(s"testsuites/java/src/test/java/$packagePath/Generic${name}Test.java")
    f.getParentFile.mkdirs
    if (f.exists)
      f.delete
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""
package $packagePath;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import $packagePath.OGFile;

import ogss.common.java.api.Mode;
import ogss.common.java.api.OGSSException;

/**
 * Tests the file reading capabilities.
 */
@SuppressWarnings("static-method")
public class Generic${name}Test extends common.CommonTest {
    public OGFile read(String s) throws Exception {
        return OGFile.open("../../" + s, Mode.Read, Mode.ReadOnly);
    }

/*    @Test
    public void writeRandomGraph() throws Exception {
        Path path = tmpFile("write.generic");
        try (SkillFile sf = SkillFile.open(path)) {
            reflectiveInit(sf);
        }
    }

    @Test
    public void writeRandomGraphChecked() throws Exception {
        Path path = tmpFile("write.generic.checked");

        // create a name -> type map
        Map<String, Access<? extends SkillObject>> types = new HashMap<>();
        try (SkillFile sf = SkillFile.open(path)) {
            reflectiveInit(sf);

            for (Access<?> t : sf.allTypes())
                types.put(t.name(), t);
        }

        // read file and check skill IDs
        SkillFile sf2 = SkillFile.open(path, Mode.Read);
        for (Access<?> t : sf2.allTypes()) {
            Iterator<? extends SkillObject> os = types.get(t.name()).iterator();
            for (SkillObject o : t) {
                Assert.assertTrue("to few instances in read stat", os.hasNext());
                Assert.assertEquals(o.getSkillID(), os.next().getSkillID());
            }
            Assert.assertFalse("to many instances in read stat", os.hasNext());
        }
    }*/
""")
    rval
  }

  def closeTestFile(out : java.io.PrintWriter) {
    out.write("""
}
""")
    out.close
  }

  override def makeTests(name : String) {
    val (accept, reject) = collectBinaries(name)

    // generate read tests
    val out = newTestFile(name, "Read")

    for (f ← accept) out.write(s"""
    @Test
    public void test_${name}_read_accept_${f.getName.replaceAll("\\W", "_")}() throws Exception {
        try (OGFile sf = read("${f.getPath.replaceAll("\\\\", "\\\\\\\\")}")) {
            sf.loadLazyData();
            sf.check();
        }
    }
""")
    for (f ← reject) out.write(s"""
    @Test
    public void test_${name}_read_reject_${f.getName.replaceAll("\\W", "_")}() throws Exception {
        try (OGFile sf = read("${f.getPath.replaceAll("\\\\", "\\\\\\\\")}")) {
            sf.loadLazyData();
            sf.check();
            Assert.fail("Expected ParseException to be thrown");
        } catch (OGSSException e) {
            // success
        }
    }
""")
    closeTestFile(out)
  }
}
