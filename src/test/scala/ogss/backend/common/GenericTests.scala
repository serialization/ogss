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

import scala.io.Codec

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import ogss.main.CommandLine

/**
 * Common implementation of generic tests
 *
 * @author Timm Felden
 */
abstract class GenericTests extends FunSuite with BeforeAndAfterAll {

  /**
   * can be used to restrict tests to a single specification; should always be [[empty string]] on commit
   */
  val testOnly = ""

  /**
   * parameter name of the language. This is required for code generator invocation.
   */
  def language : String

  /**
   * This method should delete output directories, if they exist.
   * It is invoked once before invocation of CommandLine.main
   */
  def deleteOutDir(out : String) : Unit

  /**
   * hook called to call the generator
   */
  def callMainFor(name : String, source : String, options : Seq[String])

  /**
   *  creates unit tests in the target language
   */
  def makeTests(name : String) : Unit

  def collect(f : File) : Seq[File] = {
    (for (path ← f.listFiles if path.isDirectory) yield collect(path)).flatten ++
      f.listFiles.filterNot(_.isDirectory)
  }

  /**
   * helper function that collects binaries for a given test name.
   *
   * @return (accept, reject)
   */
  final def collectBinaries(name : String) : (Seq[File], Seq[File]) = {
    val base = new File("src/test/resources/binarygen")

    val targets = (
      collect(new File(base, "[[all]]"))
      ++ collect(if (new File(base, name).exists) new File(base, name) else new File(base, "[[empty]]"))
    ).filter(_.getName.endsWith(".sf")).sortBy(_.getName)

    targets.partition(_.getPath.contains("accept"))
  }

  /**
   * hook called once after all tests have been generated
   */
  def finalizeTests() : Unit = {}

  final def makeTest(path : File, name : String, options : Seq[String]) : Unit = test("generic: " + name) {
    deleteOutDir(name)

    CommandLine.exit = { s ⇒ throw (new Error(s)) }

    callMainFor(name, path.getPath, options)

    makeTests(name)
  }

  implicit class Regex(sc : StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ ⇒ "x") : _*)
  }

  for (path ← getFileList(new File("src/test/resources/testgen")) if path.getName.endsWith(testOnly + ".skill")) {
    try {
      val r"""#!\s(\w+)${ name }(.*)${ options }""" =
        io.Source.fromFile(path)(Codec.UTF8).getLines.toSeq.headOption.getOrElse("")

      makeTest(path, name, options.split("\\s+").filter(_.length() != 0))
    } catch {
      case e : MatchError ⇒
        println(s"failed processing of $path:")
        e.printStackTrace(System.out)
    }
  }

  override def afterAll {
    finalizeTests
  }

  def getFileList(startFile : File) : Array[File] = {
    val files = startFile.listFiles();
    files ++ files.filter(_.isDirectory()).flatMap(getFileList);
  }
}
