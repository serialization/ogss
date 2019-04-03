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

import ogss.oil.ArrayType
import ogss.oil.BuiltinType
import ogss.oil.EnumDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.WithInheritance
import ogss.util.HeaderInfo
import ogss.oil.Field

/**
 * @author Timm Felden
 */
class Main extends AbstractBackEnd
  with EnumMaker
  with InterfacesMaker
  with InternalMaker
  with OGFileMaker
  with TypesMaker
  with SubTypesMaker
  with VisitorMaker {

  override def name : String = "Java"
  override def description = "Java source code"

  dependencies = Seq("ogss.common.jvm.jar", "ogss.common.java.jar")

  /**
   * Translates types into Java type names.
   */
  override def mapType(t : Type, boxed : Boolean) : String = t match {
    case t : BuiltinType ⇒ t.getName.getOgss match {
      case "AnyRef" ⇒ "Object"

      case "Bool"   ⇒ if (boxed) "java.lang.Boolean" else "boolean"

      case "I8"     ⇒ if (boxed) "java.lang.Byte" else "byte"
      case "I16"    ⇒ if (boxed) "java.lang.Short" else "short"
      case "I32"    ⇒ if (boxed) "java.lang.Integer" else "int"
      case "I64"    ⇒ if (boxed) "java.lang.Long" else "long"
      case "V64"    ⇒ if (boxed) "java.lang.Long" else "long"

      case "F32"    ⇒ if (boxed) "java.lang.Float" else "float"
      case "F64"    ⇒ if (boxed) "java.lang.Double" else "double"

      case "String" ⇒ "java.lang.String"
    }

    case t : ArrayType       ⇒ s"$ArrayTypeName<${mapType(t.getBaseType(), true)}>"
    case t : ListType        ⇒ s"$ListTypeName<${mapType(t.getBaseType(), true)}>"
    case t : SetType         ⇒ s"$SetTypeName<${mapType(t.getBaseType(), true)}>"
    case t : MapType         ⇒ s"$MapTypeName<${mapType(t.getKeyType, true)}, ${mapType(t.getValueType, true)}>"

    case t : EnumDef         ⇒ s"EnumProxy<$packagePrefix${name(t)}>"

    case t : WithInheritance ⇒ packagePrefix + name(t)

    case _                   ⇒ throw new IllegalStateException(s"Unknown type $t")
  }

  override def makeHeader(headerInfo : HeaderInfo) : String = headerInfo.format(this, "/*", "*\\", " *", "* ", "\\*", "*/")

  /**
   * provides the package prefix
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix = ""

  override def setPackage(names : List[String]) {
    _packagePrefix = names.foldRight("")(_ + "." + _)
  }

  override def packageDependentPathPostfix = if (packagePrefix.length > 0) {
    packagePrefix.replace(".", "/")
  } else {
    ""
  }
  override def defaultCleanMode = "file";

  override def setOption(option : String, value : String) : Unit = option match {
    case "suppresswarnings" ⇒ suppressWarnings = if ("true".equals(value)) "@SuppressWarnings(\"all\")\n" else ""
    case unknown            ⇒ sys.error(s"unkown Argument: $unknown")
  }

  override def describeOptions = Seq(
    OptionDescription("suppressWarnings", "true/false", """add a @SuppressWarnings("all") annotation to generated classes""")
  )

  override def customFieldManual : String = """
  !import string+    A list of imports that will be added where required.
  !modifier string   A modifier, that will be put in front of the variable declaration."""

  override protected def defaultValue(f : Field) = {
    val stid = f.getType.getStid
    if (stid < 0 || 8 >= stid)
      "null"
    else if (0 == stid)
      "false"
    else if (stid < 6)
      "0"
    else
      "0.0f"
  }

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   * Will add Z's if escaping is required.
   */
  private val escapeCache = new HashMap[String, String]();
  final override def escapedLonely(target : String) : String = escapeCache.getOrElse(target, {
    val result = target match {
      case "OGFile" | "Visitor" | "internal"
        | "abstract" | "continue" | "for" | "new" | "switch" | "assert" | "default" | "if" | "package" | "synchronized"
        | "boolean" | "do" | "goto" | "private" | "this" | "break" | "double" | "implements" | "protected" | "throw"
        | "byte" | "else" | "import" | "public" | "throws" | "case" | "enum" | "instanceof" | "return" | "transient"
        | "catch" | "extends" | "int" | "short" | "try" | "char" | "final" | "interface" | "static" | "void" | "class"
        | "finally" | "long" | "strictfp" | "volatile" | "const" | "float" | "native" | "super" | "while" ⇒ "Z" + target

      case _ ⇒ target.map {
        case ':'                                    ⇒ "$"
        case 'Z'                                    ⇒ "ZZ"
        case c if Character.isJavaIdentifierPart(c) ⇒ c.toString
        case c                                      ⇒ "Z" + c.toHexString
      }.reduce(_ + _)
    }
    escapeCache(target) = result
    result
  })
}
