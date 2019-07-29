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

import ogss.oil.ArrayType
import ogss.oil.BuiltinType
import ogss.oil.ClassDef
import ogss.oil.EnumDef
import ogss.oil.Field
import ogss.oil.Identifier
import ogss.oil.InterfaceDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.TypeContext
import ogss.util.HeaderInfo
import ogss.oil.WithInheritance
import ogss.oil.TypeAlias

/**
 * A generator turns a set of skill declarations into a scala interface providing means of manipulating skill files
 * containing instances of the respective UserTypes.
 *
 * @author Timm Felden
 */
final class Main extends AbstractBackEnd
  with EnumMaker
  with TypedefMaker
  with TypesMaker {

  override def name : String = "doxygen";
  override def description = "doxygen-compatible c++ headers"

  dependencies = Seq()

  override def packageDependentPathPostfix = ""
  override def defaultCleanMode = "file";

  /**
   * Translates types into scala type names.
   */
  override def mapType(t : Type) : String = t match {
    case t : BuiltinType ⇒ t.name.ogss match {
      case "AnyRef" ⇒ "void*"

      case "Bool"   ⇒ "bool"

      case "I8"     ⇒ "int8_t"
      case "I16"    ⇒ "int16_t"
      case "I32"    ⇒ "int32_t"
      case "I64"    ⇒ "int64_t"
      case "V64"    ⇒ "int64_t"

      case "F32"    ⇒ "float"
      case "F64"    ⇒ "double"

      case "String" ⇒ "std::string*"
    }

    case t : ArrayType       ⇒ s"std::vector<${mapType(t.baseType)}>*"
    case t : ListType        ⇒ s"std::list<${mapType(t.baseType)}>*"
    case t : SetType         ⇒ s"std::set<${mapType(t.baseType)}>*"
    case t : MapType         ⇒ s"std::map<${mapType(t.keyType)}, ${mapType(t.valueType)}>*"

    case t : EnumDef         ⇒ name(t)

    case t : WithInheritance ⇒ s"${name(t)}*"

    case t : TypeAlias       ⇒ capital(t.name)

    case _                   ⇒ throw new IllegalStateException(s"Unknown type $t")
  }

  override def makeHeader(headerInfo : HeaderInfo) : String = headerInfo.format(this, "/*", "*\\", " *", "* ", "\\*", "*/")

  /**
   * provides the package prefix
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix : String = null

  override def setPackage(names : List[String]) {
    _packagePrefix = names.foldRight("")(_ + "." + _)
  }

  override def setOption(option : String, value : String) {
    option match {
      case unknown ⇒ sys.error(s"unkown Argument: $unknown")
    }
  }

  override def describeOptions = Seq()

  override def customFieldManual : String = """(unsupported)"""

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   */
  private val escapeCache = new HashMap[String, String]();
  final def escapedLonely(target : String) : String = escapeCache.getOrElseUpdate(target, EscapeFunction(target))
}

object EscapeFunction {
  def apply(target : String) : String = target match {
    //keywords get a suffix "_", because that way at least auto-completion will work as expected
    case "auto" | "const" | "double" | "float" | "int" | "short" | "struct" | "unsigned" | "break" | "continue"
      | "else" | "for" | "long" | "signed" | "switch" | "void" | "case" | "default" | "enum" | "goto" | "register"
      | "sizeof" | "typedef" | "volatile" | "char" | "do" | "extern" | "if" | "return" | "static" | "union" | "while"
      | "asm" | "dynamic_cast" | "namespace" | "reinterpret_cast" | "try" | "bool" | "explicit" | "new" | "static_cast"
      | "typeid" | "catch" | "false" | "operator" | "template" | "typename" | "class" | "friend" | "private" | "this"
      | "using" | "const_cast" | "inline" | "public" | "throw" | "virtual" | "delete" | "mutable" | "protected"
      | "true" | "wchar_t" | "and" | "bitand" | "compl" | "not_eq" | "or_eq" | "xor_eq" | "and_eq" | "bitor" | "not"
      | "or" | "xor" | "cin" | "endl" | "INT_MIN" | "iomanip" | "main" | "npos" | "std" | "cout" | "include"
      | "INT_MAX" | "iostream" | "MAX_RAND" | "NULL" | "string" | "stid" ⇒ s"_$target"

    case t if t.forall(c ⇒ '_' == c || Character.isLetterOrDigit(c) && c < 128) ⇒ t

    case _ ⇒ target.map {
      case 'Z' ⇒ "ZZ"
      case c if '_' == c || Character.isLetterOrDigit(c) && c < 128 ⇒ "" + c
      case c ⇒ f"Z$c%04X"
    }.mkString
  }
}
