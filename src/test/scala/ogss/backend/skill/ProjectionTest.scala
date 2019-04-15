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
package ogss.main.skill

import java.io.File

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.exceptions.TestFailedException
import org.scalatest.junit.JUnitRunner

import ogss.frontend.common.ParseException
import ogss.main.CommandLine

/**
 * Create all projections for specifications inside the src/test/resources/frontend directory.
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class ProjectionTest extends FunSuite {

  CommandLine.exit = { s ⇒ fail(s) }
  private def check(file : File) = CommandLine.main(Array[String](
    "build",
    file.getAbsolutePath,
    "--debug-header",
    "-L", "skill",
    "-o", "testsuites/skill/" + file.getName.split('.')(0)
  ))

  for (
    f ← new File("src/test/resources/frontend").listFiles() if f.isFile()
  ) {
    test("write " + f.getName()) { check(f) }
  }
}
