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

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import ogss.oil.TypeContext
import ogss.backend.common.BackEnd
import ogss.oil.Type
import ogss.oil.Field
import ogss.oil.OGFile
import ogss.oil.ClassDef
import ogss.oil.FieldLike
import ogss.oil.UserDefinedType
import ogss.oil.Identifier
import ogss.oil.EnumDef

/**
 * The parent class for all output makers.
 *
 * @author Timm Felden
 */
trait AbstractBackEnd extends BackEnd {

  final def setIR(TC : OGFile) {
    this.types = asScalaIterator(TC.TypeContexts.iterator()).find { tc ⇒ tc.getProjectedTypeDefinitions && tc.getProjectedInterfaces }.get
    this.IR = types.getClasses.asScala.to
    enums = types.getEnums.asScala.to

    // filter implemented interfaces from original IR
    if (interfaceChecks) {
      filterIntarfacesFromIR(
        TC.TypeContexts.asScala.find { tc ⇒ tc.getProjectedTypeDefinitions && !tc.getProjectedInterfaces }.get
      )
    }
  }
  var types : TypeContext = _
  var IR : Array[ClassDef] = _
  var enums : Array[EnumDef] = _

  lineLength = 120
  override def comment(d : UserDefinedType) : String = format(d.getComment, "/**\n", "     * ", "     */\n    ")
  override def comment(f : FieldLike) : String = format(f.getComment, "/**\n", "         * ", "         */\n        ")

  // options
  /**
   * If set to true, the generated binding will reveal the values of object IDs.
   */
  protected var revealObjectID = false;
  /**
   * If set to true, the generated API will contain is[[interface]] methods.
   * These methods return true iff the type implements that interface.
   * These methods exist for direct super types of interfaces.
   * For rootless interfaces, they exist in base types.
   */
  protected var interfaceChecks = false;
  protected def filterIntarfacesFromIR(TS : TypeContext);

  /**
   * If interfaceChecks then skillName -> Name of sub-interfaces
   * @note the same interface can be sub and super, iff the type is a base type;
   * in that case, super wins!
   */
  protected val interfaceCheckMethods = new HashMap[Identifier, HashSet[Identifier]]
  /**
   * If interfaceChecks then skillName -> Name of super-interfaces
   */
  protected val interfaceCheckImplementations = new HashMap[Identifier, HashSet[Identifier]]

  override def defaultValue(f : Field) : String = {
    val stid = f.getType.getStid
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
   * Returns the selector required to turn a box into a useful type.
   * @note does not include . or -> to allow usage in both cases
   */
  protected def unbox(t : Type) : String

  /**
   * turns a declaration and a field into a string writing that field into an outStream
   * @note the used iterator is "outData"
   * @note the used target OutStream is "dataChunk"
   */
  protected def writeField(d : ClassDef, f : Field) : String

  /**
   * The name of the String Keeper global variable holding the name
   */
  protected def skName(id : Identifier) : String = s"internal::SK.${escaped(adaStyle(id))}"

  /**
   * id's given to fields
   */
  private val poolNameStore : HashMap[Identifier, Int] = new HashMap()
  /**
   * The name of T's storage pool
   */
  protected def access(t : ClassDef) : String = this.synchronized {
    "P" + poolNameStore.getOrElseUpdate(t.getName, poolNameStore.size).toString
  }
  /**
   * The name of T's builder
   */
  protected def builder(t : ClassDef) : String = {
    if (null != t.getSuperType && t.getFields.isEmpty) {
      builder(t.getSuperType)
    } else {
      this.synchronized {
        s"B${poolNameStore.getOrElseUpdate(t.getName, poolNameStore.size)}"
      }
    }
  }

  /**
   * getter name
   */
  protected[cpp] def getter(f : FieldLike) : String = s"get${escaped(capital(f.getName))}"
  /**
   * setter name
   */
  protected[cpp] def setter(f : FieldLike) : String = s"set${escaped(capital(f.getName))}"
  protected def knownField(f : Field) : String = escaped(s"KF_${capital(f.getOwner.getName)}_${camel(f.getName)}")

  /**
   * id's given to fields
   */
  protected val fieldNameStore : HashMap[(String, String), Int] = new HashMap()
  /**
   * Class name of the representation of a known field
   */
  protected def knownField(f : FieldLike) : String = this.synchronized {
    "f" + fieldNameStore.getOrElseUpdate((ogssname(f.getOwner), ogssname(f)), fieldNameStore.size).toString
  }

  /**
   * Assume a package prefix provider.
   */
  protected def packagePrefix() : String
  protected lazy val packageParts : Array[String] = packagePrefix().split("\\.").map(escaped)
  protected lazy val packageName : String = packageParts.mkString("::", "::", "")

  /**
   * all string literals used in type and field names
   */
  protected lazy val allStrings : Array[Identifier] = (IR.map(_.getName).toSet ++
    IR.flatMap(_.getFields.asScala).map(_.getName).toSet ++
    types.getEnums.asScala.map(_.getName).toSet ++
    types.getEnums.asScala.flatMap(_.getValues.asScala).map(_.getName).toSet).toArray.sortBy(_.getOgss)

  /**
   * start a guard word for a file
   */
  final protected def beginGuard(t : Type) : String = beginGuard(escaped(name(t)))
  final protected def beginGuard(word : String) : String = {
    val guard = "OGSS_CPP_GENERATED_" + packageParts.map(_.toUpperCase).mkString("", "_", "_") + word.toUpperCase
    s"""#ifndef $guard
#define $guard
"""
  }
  final protected val endGuard : String = """
#endif"""
}
