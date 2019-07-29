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
package ogss.backend.common

import java.io.File
import java.io.PrintWriter

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.collection.mutable.HashMap
import scala.io.Source

import org.json.JSONObject
import org.json.JSONTokener

import ogss.common.scala.api.Create
import ogss.common.scala.api.Write
import ogss.main.KnownFrontEnds
import ogss.oil.OGFile
import ogss.oil.TypeContext
import ogss.util.IRUtils

/**
 * Common implementation of generic tests
 *
 * @author Timm Felden
 */
abstract class GenericAPITests extends GenericTests with IRUtils {

  /**
   * The current OGFile
   */
  var IR : OGFile = _

  /**
   * Helper function that collects test-specifications.
   * @return map from .skill-spec to list of .json-specs
   */
  lazy val collectTestspecs : HashMap[File, Array[File]] = {
    val base = new File("src/test/resources/testgen");

    val specs = collect(base).filter(_.getName.endsWith(".skill"));

    val rval = new HashMap[File, Array[File]]
    for (s ← specs)
      rval(s) = s.getParentFile.listFiles().filter(_.getName.endsWith(".json"))

    rval
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter;

  def closeTestFile(out : java.io.PrintWriter) : Unit;

  def makeSkipTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean);

  /**
   * obtain the preferred type context from IR
   */
  def preferredTypeContext : TypeContext = IR.TypeContext.find(tc ⇒ tc.projectedTypeDefinitions && !tc.projectedInterfaces).get

  def makeRegularTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean, tc : TypeContext, obj : JSONObject);

  final override def makeTests(name : String) {
    val (spec, tests) = collectTestspecs.filter(file ⇒ {
      val line = Source.fromFile(file._1).getLines().next()
      line.equals("#! " + name) || line.matches(s"#! $name\\s+.*")
    }).head

    // find and execute correct frond-end
    val frontEnd = KnownFrontEnds.forFile(spec)

    // write IR to temporary file, so we do not have to care about misbehaving back-ends
    val tmpPath = File.createTempFile("ogss", ".oil")
    tmpPath.deleteOnExit()

    frontEnd.out = OGFile.open(tmpPath, Create, Write)
    frontEnd.run(spec)
    IR = frontEnd.out

    val TC = preferredTypeContext

    val out = newTestFile(name, "API")

    println(spec.getName)

    // create tests
    for (f ← tests) {
      println(s"  - ${f.getName}")

      val test = new JSONObject(new JSONTokener(new java.io.FileInputStream(f)))

      val skipped = try {
        test.getJSONArray("skip").iterator().asScala.contains(language)
      } catch {
        case e : Exception ⇒ false;
      }

      val accept = try {
        test.getString("should").toLowerCase match {
          case "fail" ⇒ false
          case "skip" ⇒ skipped
          case _      ⇒ true
        }
      } catch {
        case e : Exception ⇒ true;
      }

      val kind = try {
        val v = test.getString("kind")
        if (null == v) "core" else v
      } catch {
        case e : Exception ⇒ "core";
      }

      val testName = f.getName.replace(".json", "");

      if (skipped) {
        makeSkipTest(out, kind, name, testName, accept);
      } else {
        makeRegularTest(out, kind, name, testName, accept, TC, test.getJSONObject("obj"));
      }
    }
    closeTestFile(out)
  }

  override def finalizeTests {
    // nothing yet
  }
}
