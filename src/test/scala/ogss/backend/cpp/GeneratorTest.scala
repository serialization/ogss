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

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import ogss.main.CommandLine

@RunWith(classOf[JUnitRunner])
class GeneratorTest extends FunSuite {

  def check(src : String, out : String) {
    CommandLine.exit = { s ⇒ fail(s) }
    CommandLine.main(Array[String](
      "build",
      "src/test/resources/cpp/" + src,
      "--debug-header",
      "-c",
      "-L", "cpp",
      "-p", out,
      "-o", "testsuites/cpp/src/" + out
    ))
  }

  // use this test to check that build without reveal skillID can be performed
  test("aircraft")(check("aircraft.skill", "aircraft"))

  // test for nested namespaces
  test("aircraft -- nested")(check("aircraft.skill", "air.craft"))
}
