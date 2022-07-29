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
package ogss.backend.java

import scala.collection.mutable.HashMap

import ogss.backend.common.BackEnd
import ogss.oil.{ClassDef, EnumConstant, EnumDef, FieldLike, Identifier, InterfaceDef, OGFile, Type, TypeContext, UserDefinedType}

/**
 * Abstract Java back-end
 *
 * @author Timm Felden
 */
abstract class AbstractBackEnd extends BackEnd {

  final def setIR(TC : OGFile) {
    types = TC.TypeContext.find { tc ⇒ tc.projectedTypeDefinitions && !tc.projectedInterfaces }.get
    flatTC = TC.TypeContext.find { tc ⇒ tc.projectedTypeDefinitions && tc.projectedInterfaces }.get
    IR = types.classes.to
    flatIR = flatTC.classes.to
    projected = flatIR.foldLeft(new HashMap[String, ClassDef])(
      (m, t) ⇒ { m(ogssname(t)) = t; m }
    )
    interfaces = types.interfaces.to
    enums = types.enums.to
  }

  var types : TypeContext = _
  var flatTC : TypeContext = _
  var IR : Array[ClassDef] = _
  var flatIR : Array[ClassDef] = _
  var interfaces : Array[InterfaceDef] = _
  var enums : Array[EnumDef] = _

  var projected : HashMap[String, ClassDef] = _

  lineLength = 120
  override def comment(d : UserDefinedType) : String = format(d.comment, "/**\n", " * ", " */\n")
  override def comment(f : FieldLike) : String = format(f.comment, "/**\n", "     * ", "     */\n    ")
  override def comment(f : EnumConstant) : String = format(f.comment, "/**\n", "     * ", "     */\n    ")

  // container type mappings
  val ArrayTypeName = "java.util.ArrayList"
  val ListTypeName = "java.util.LinkedList"
  val SetTypeName = "java.util.HashSet"
  val MapTypeName = "java.util.HashMap"

  /**
   * Translate a type to its Java type name
   */
  def mapType(t : Type, boxed : Boolean = false) : String

  /**
   * id's given to fields
   */
  private val poolNameStore : HashMap[String, Int] = new HashMap()
  /**
   * The name of T's storage pool
   */
  protected def access(t : ClassDef) : String = this.synchronized {
    "P" + poolNameStore.getOrElseUpdate(ogssname(t), poolNameStore.size).toString
  }
  /**
   * The name of T's builder
   */
  protected def builder(target : ClassDef) : String = {
    val t = projected(ogssname(target))
    if (null != t.superType && t.fields.isEmpty) {
      builder(t.superType)
    } else {
      this.synchronized {
        s"B${poolNameStore.getOrElseUpdate(ogssname(t), poolNameStore.size)}"
      }
    }
  }
  /**
   * The name of T's unknown sub pool
   */
  protected def subPool(t : Type) : String = this.synchronized {
    "S" + poolNameStore.getOrElseUpdate(ogssname(t), poolNameStore.size).toString
  }

  /**
   * id's given to fields
   */
  protected val fieldNameStore : HashMap[(String, String), Int] = new HashMap()
  /**
   * Class name of the representation of a known field
   */
  protected def knownField(f : FieldLike) : String = this.synchronized {
    "f" + fieldNameStore.getOrElseUpdate((ogssname(f.owner), ogssname(f)), fieldNameStore.size).toString
  }

  /**
   * Assume a package prefix provider.
   */
  protected def packagePrefix() : String
  protected def packageName = packagePrefix.substring(0, packagePrefix.length - 1)

  /**
   * all string literals used in type and field names
   */
  protected lazy val allStrings : Array[Identifier] = (flatIR.map(_.name).toSet ++
    flatIR.flatMap(_.fields).map(_.name).toSet ++
    flatTC.enums.map(_.name).toSet ++
    flatTC.enums.flatMap(_.values).map(_.name).toSet).toArray.sortBy(_.ogss)

  /**
   * getter name
   */
  protected def getter(f : FieldLike) : String = s"get${escaped(capital(f.name))}"
  /**
   * setter name
   */
  protected def setter(f : FieldLike) : String = s"set${escaped(capital(f.name))}"

  /// options \\\

  /**
   * this string may contain a "@SuppressWarnings("all")\n", in order to suppress warnings in generated code;
   * the option can be enabled by "-O@java:SuppressWarnings=true"
   */
  protected var suppressWarnings = "";
}
