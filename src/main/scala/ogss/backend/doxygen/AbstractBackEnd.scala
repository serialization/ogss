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
package ogss.backend.doxygen

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import ogss.backend.common.BackEnd
import ogss.oil.{ClassDef, EnumConstant, EnumDef, Field, FieldLike, Identifier, OGFile, Type, TypeContext, UserDefinedType}

/**
 * The parent class for all output makers.
 *
 * @author Timm Felden
 */
trait AbstractBackEnd extends BackEnd {

  final def setIR(TC : OGFile) {
    this.types = TC.TypeContext.find { tc ⇒ !tc.projectedTypeDefinitions && !tc.projectedInterfaces }.get
    this.IR = types.classes.to
    enums = types.enums.to
  }
  var types : TypeContext = _
  var IR : Array[ClassDef] = _
  var enums : Array[EnumDef] = _

  lineLength = 80
  override def comment(d : UserDefinedType) : String = format(d.comment, "/**\n", "     * ", "     */\n    ")
  override def comment(f : FieldLike) : String = format(f.comment, "/**\n", "         * ", "         */\n        ")
  override def comment(f : EnumConstant) : String = format(f.comment, "/**\n", "         * ", "         */\n        ")

  // options

  override def defaultValue(f : Field) : String = {
    val stid = f.`type`.stid
    if (stid < 0 || 8 >= stid)
      "nullptr"
    else if (0 == stid)
      "false"
    else if (stid < 6)
      "0"
    else
      "0.0f"
  }

  /**
   * Assume the existence of a translation function for types.
   */
  protected def mapType(t : Type) : String

  /**
   * Assume a package prefix provider.
   */
  protected def packagePrefix() : String
  protected lazy val packageParts : Array[String] = packagePrefix().split("\\.").map(escaped)
  protected lazy val packageName : String = packageParts.mkString("::", "::", "")
}
