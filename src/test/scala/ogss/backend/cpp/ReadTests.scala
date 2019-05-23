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
package ogss.backend.cpp

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
 * Generic tests built for C++.
 * Generic tests have an implementation for each programming language, because otherwise deleting the generated code
 * upfront would be ugly.
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class GenericTests extends common.GenericTests {

  override def language : String = "cpp"

  var gen = new Main

  override def deleteOutDir(out : String) {
    import scala.reflect.io.Directory
    Directory(new File("testsuites/cpp/src/", out)).deleteRecursively
  }

  override def callMainFor(name : String, source : String, options : Seq[String]) {
    CommandLine.main(Array[String](
      "build",
      source,
      "--debug-header",
      "-L", "cpp",
      "-p", name,
      "-Ocpp:revealID=true",
      "-d", "testsuites/cpp/lib",
      "-o", "testsuites/cpp/src/" + name
    ) ++ options)
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter = {
    val packageName = packagePath.split("/").map(EscapeFunction.apply).mkString("::")
    gen = new Main
    gen.setPackage(List(packagePath))

    val f = new File(s"testsuites/cpp/test/$packagePath/generic${name}Test.cpp")
    f.getParentFile.mkdirs
    if (f.exists)
      f.delete
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""
#include <gtest/gtest.h>
#include "../../src/$packagePath/File.h"

using ::$packageName::api::File;
""")
    rval
  }

  def closeTestFile(out : java.io.PrintWriter) {
    out.write("""
""")
    out.close
  }

  override def makeTests(name : String) {
    val (accept, reject) = collectBinaries(name)

    // generate read tests
    locally {
      val out = newTestFile(name, "Read")

      for (f ← accept) out.write(s"""
TEST(${gen.escaped(name.capitalize)}_Read_Test, Accept_${f.getName.replaceAll("\\W", "_")}) {
    try {
        auto s = std::unique_ptr<File>(File::open("../../${f.getPath.replaceAll("\\\\", "\\\\\\\\")}"));
        s->check();
    } catch (ogss::Exception& e) {
        GTEST_FAIL() << "an exception was thrown:" << std::endl << e.what();
    }
    GTEST_SUCCEED();
}
""")
      for (f ← reject) out.write(s"""
TEST(${gen.escaped(name.capitalize)}_Read_Test, Reject_${f.getName.replaceAll("\\W", "_")}) {
    try {
        auto s = std::unique_ptr<File>(File::open("../../${f.getPath.replaceAll("\\\\", "\\\\\\\\")}"));
        s->check();
    } catch (ogss::Exception& e) {
        GTEST_SUCCEED();
        return;
    }
    GTEST_FAIL() << "expected an exception, but none was thrown.";
}
""")
      closeTestFile(out)
    }
  }

  override def finalizeTests {
    // nothing yet
  }
}
