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
package ogss.backend.skill

import ogss.util.HeaderInfo
import ogss.oil.Type
import ogss.oil.BuiltinType
import ogss.oil.SetType
import ogss.oil.ArrayType
import ogss.oil.MapType
import ogss.oil.ListType

/**
 * Dump an .oil-file as SKilL specification.
 *
 * @author Timm Felden
 */
class Main
  extends AbstractBackEnd
  with SpecificationMaker {

  override def name : String = "SKilL";
  override def description = "SKilL specification"

  override def packageDependentPathPostfix = ""
  override def defaultCleanMode = "file";

  override def mapType(t : Type) : String = t match {
    case t : BuiltinType ⇒ camel(t.name) match {
      case "anyRef" ⇒ "annotation"
      case s        ⇒ s
    }
    case t : ArrayType ⇒ s"${mapType(t.baseType)}[]"
    case t : ListType  ⇒ s"list<${mapType(t.baseType)}>"
    case t : SetType   ⇒ s"set<${mapType(t.baseType)}>"

    // note: should rebuild n-ary maps
    case t : MapType ⇒ s"map<${mapType(t.keyType)}, ${mapType(t.valueType)}>"
      .replace(", map<", ", ")
      .replaceAll(">+", ">")

    case t ⇒ t.name.ogss
  }

  override def makeHeader(headerInfo : HeaderInfo) : String = headerInfo.format(this, "# ", "", "# ", "", "# ", "\n")

  /**
   * provides the package prefix
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix : String = null

  override def setPackage(names : List[String]) {
    _packagePrefix = names.foldRight("")(_ + "." + _)
  }

  override def setOption(option : String, value : String) {
    sys.error(s"unkown Argument: $option")
  }

  override def describeOptions = Seq()

  override def customFieldManual : String = """(unsupported)"""

  /**
   * We do not escape anything
   */
  final def escapedLonely(target : String) : String = target
}
