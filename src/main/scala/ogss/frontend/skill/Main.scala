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
package ogss.frontend.skill

import java.io.File

import ogss.frontend.common.FrontEnd
import ogss.oil.OGFile
import ogss.util.Projections

/**
 * Parse a .skill-specification and apply OGSS-semantics to its definitions.
 */
class Main extends FrontEnd {

  def name = "skill"
  def extension = ".skill"
  def description = "SKilL Specification"

  override def run(path : File) {
    // parse all definitions
    val files = new FileParser(this)
    files.process(path)
    files.postProcess

    normalize

    new Projections(out)

    out.close()
  }
}
