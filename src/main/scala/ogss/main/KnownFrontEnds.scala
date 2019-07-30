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
package ogss.main

import ogss.frontend._;
import java.io.File

/**
 * Known front-ends have to be registered here.
 *
 * @author Timm Felden
 */
object KnownFrontEnds {

  val allClasses : Array[Class[_ <: common.FrontEnd]] = Array(
    classOf[oil.Main],
    classOf[ogss.frontend.skill.Main],
    classOf[sidl.Main]
  )

  private[main] val all = allClasses.map(_.newInstance)

  def forFile(target : File) : common.FrontEnd = {
    all.find { f ⇒
      target.getName.endsWith(f.extension)
    }.getOrElse(CommandLine.error("no available front-end matches the file extension")).getClass.newInstance()
  }
}
