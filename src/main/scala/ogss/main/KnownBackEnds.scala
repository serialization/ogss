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

import ogss.backend._;

/**
 * Known back-ends have to be registered here.
 *
 * @author Timm Felden
 */
object KnownBackEnds {

  val allClasses : Array[Class[_ <: common.BackEnd]] = Array(
    classOf[cpp.Main],
    classOf[doxygen.Main],
    classOf[java.Main],
    classOf[oil.Main],
    classOf[ogss.backend.skill.Main],
    classOf[scala.Main],
    classOf[sidl.Main],
  )

  private[main] val all = allClasses.map(_.newInstance)

  def forLanguage(language : String) : common.BackEnd = {
    val lang = language.toLowerCase()
    all.find { f ⇒
      f.name.toLowerCase.equals(lang)
    }.getOrElse(CommandLine.error(s"no available back-end matches language $language")).getClass.newInstance()
  }
}
