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

import scala.collection.JavaConverters.asScalaBufferConverter
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

/**
 * A generator turns a set of skill declarations into a scala interface providing means of manipulating skill files
 * containing instances of the respective UserTypes.
 *
 * @author Timm Felden
 */
final class Main extends AbstractBackEnd
  with CMakeListsMaker
  with EnumMaker
  with FieldDeclarationsMaker
  with OGFileMaker
  with StringKeeperMaker
  with PoolsMaker
  with TypesMaker
  //@note this maker has to be performed after others
  with FileNamesMaker {

  override def name : String = "Cpp";
  override def description = "C++ source code"

  dependencies = Seq("ogss.common.cpp")

  override def packageDependentPathPostfix = ""
  override def defaultCleanMode = "file";

  /**
   * Translates types into scala type names.
   */
  override def mapType(t : Type) : String = t match {
    case t : BuiltinType ⇒ t.getName.getOgss match {
      case "AnyRef" ⇒ "::ogss::api::Object*"

      case "Bool"   ⇒ "bool"

      case "I8"     ⇒ "int8_t"
      case "I16"    ⇒ "int16_t"
      case "I32"    ⇒ "int32_t"
      case "I64"    ⇒ "int64_t"
      case "V64"    ⇒ "int64_t"

      case "F32"    ⇒ "float"
      case "F64"    ⇒ "double"

      case "String" ⇒ "::ogss::api::String"
    }

    case t : ArrayType ⇒ s"::ogss::api::Array<${mapType(t.getBaseType())}>*"
    case t : ListType  ⇒ s"::ogss::api::Array<${mapType(t.getBaseType())}>*"
    case t : SetType   ⇒ s"::ogss::api::Set<${mapType(t.getBaseType())}>*"
    case t : MapType   ⇒ s"::ogss::api::Map<${mapType(t.getKeyType)}, ${mapType(t.getValueType)}>*"

    case t : EnumDef   ⇒ s"::ogss::api::EnumProxy<$packageName::${name(t)}>*"

    case t : ClassDef  ⇒ s"$packageName::${name(t)}*"

    case _             ⇒ throw new IllegalStateException(s"Unknown type $t")
  }

  override protected def unbox(t : Type) : String = t match {

    case t : BuiltinType ⇒ t.getName.getOgss match {
      case "AnyRef" ⇒ "anyRef"
      case "Bool"   ⇒ "boolean"
      case "V64"    ⇒ "i64"
      case t        ⇒ t.toLowerCase();
    }

    case t : ArrayType ⇒ "array"
    case t : ListType  ⇒ "list"
    case t : SetType   ⇒ "set"
    case t : MapType   ⇒ "map"

    case t : EnumDef   ⇒ "enumProxy"

    case t : ClassDef  ⇒ "anyRef"

    case _             ⇒ throw new IllegalStateException(s"Unknown type $t")
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
      case "revealid"         ⇒ revealObjectID = ("true".equals(value))
      case "interfacechecks"  ⇒ interfaceChecks = ("true".equals(value))
      case "writefilenames"   ⇒ writeGeneratedSources = ("true".equals(value))
      case "pic"              ⇒ cmakeFPIC = ("true".equals(value))
      case "suppressWarnings" ⇒ cmakeNoWarn = ("true".equals(value))
      case unknown            ⇒ sys.error(s"unkown Argument: $unknown")
    }
  }

  override def describeOptions = Seq(
    OptionDescription("revealID", "true/false", "if set to true, the generated API will reveal object IDs"),
    OptionDescription("interfaceChecks", "true/false", "if set to true, the generated API will contain is[[interface]] methods"),
    OptionDescription("writeFileNames", "true/false", "if set to true, create generatedFiles.txt that contains all generated file names"),
    OptionDescription("PIC", "true/false", "generated cmake project will create position independent code"),
    OptionDescription("suppressWarnings", "true/false", "generated cmake project will tell gcc to suppress warnings")
  )

  override def customFieldManual : String = """
  !include string+    Argument strings are added to the head of the generated file and included using
                      <> around the strings content.
  !default string     Text to be inserted as replacement for default initialization."""

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   */
  private val escapeCache = new HashMap[String, String]();
  final def escapedLonely(target : String) : String = escapeCache.getOrElseUpdate(target, EscapeFunction(target))

  protected def filterIntarfacesFromIR(TS : TypeContext) {
    // find implementers
    for (t ← TS.getClasses.asScala) {
      val is = allSuperTypes(t).collect { case i : InterfaceDef ⇒ i }
      interfaceCheckImplementations(t.getName) = is.map(insertInterface(_, t))
    }
  }
  private def insertInterface(i : InterfaceDef, target : ClassDef) : Identifier = {
    // register a potential implementation for the target type and interface
    i.getBaseType match {
      case null ⇒
        interfaceCheckMethods.getOrElseUpdate(target.getBaseType.getName, new HashSet) += i.getName
      case b ⇒
        interfaceCheckMethods.getOrElseUpdate(b.getName, new HashSet) += i.getName
    }
    // return the name to be used
    i.getName
  }

  protected def writeField(d : ClassDef, f : Field) : String = {
    val fName = name(f)
    f.getType match {
      case t : BuiltinType ⇒
        if (8 <= t.getStid) s"for(i ← outData) ${lowercase(f.getType.getName)}(i.$fName, dataChunk)"
        else s"for(i ← outData) dataChunk.${lowercase(f.getType.getName)}(i.$fName)"

      case _ ⇒ s"""for(i ← outData) userRef(i.$fName, dataChunk)"""
    }
  }
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
