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
package ogss.frontend.common

import java.io.File

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer

import ogss.oil.ArrayType
import ogss.oil.ClassDef
import ogss.oil.Comment
import ogss.oil.CustomFieldOption
import ogss.oil.Identifier
import ogss.oil.InterfaceDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.OGFile
import ogss.oil.SetType
import ogss.oil.SourcePosition
import ogss.oil.Type
import ogss.util.IRUtils
import ogss.oil.Attribute

/**
 * Provides common functionalities to be used by all front-ends.
 *
 * @note All Front-Ends must provide a default constructor.
 */
abstract class FrontEnd {

  /**
   * The result of this front-end.
   *
   * @note the OGFile is opened by CLI and remains untouched after invocation of
   * run
   *
   * @note helper methods use out to create new objects
   */
  var out : OGFile = _;

  /**
   * The name of this front-end as per command line interface.
   */
  def name : String;

  /**
   * The human readable description of this front-end.
   */
  def description : String;

  /**
   * The file extension associated with this front-end.
   */
  def extension : String;

  def reportWarning(msg : String) {
    println("[warning]" + msg)
  }
  def reportWarning(pos : SourcePosition, msg : String) {
    println(s"[warning] ${printPosition(pos)} $msg")
  }

  def reportError(msg : String) : Nothing = {
    throw new ParseException(msg)
  }
  def reportError(pos : Positioned, msg : String) : Nothing = {
    throw new ParseException(s"${printPosition(makeSPos(pos))} $msg")
  }
  def reportError(pos : SourcePosition, msg : String) : Nothing = {
    throw new ParseException(s"${printPosition(pos)} $msg")
  }

  /**
   * Run the front-end on the argument path
   */
  def run(path : File)

  /**
   * Create a comment from a comment string
   *
   * The representation of text is without the leading "/°" and trailing "°/" (where °=*)
   */
  final def toComment(text : String) : Comment = {
    // scan s to split it into pieces
    @inline def scan(last : Int) : ListBuffer[String] = {
      var begin = 0;
      var next = 0;
      // we have to insert a line break, because the whitespace handling may have removed one
      var r = ListBuffer[String]("\n")
      while (next < last) {
        text.charAt(next) match {
          case ' ' | '\t' | 0x0B | '\f' | '\r' ⇒
            if (begin != next)
              r.append(text.substring(begin, next))
            begin = next + 1;
          case '\n' ⇒
            if (begin != next)
              r.append(text.substring(begin, next))
            r.append("\n")
            begin = next + 1;
          case _ ⇒
        }
        next += 1
      }
      if (begin != last) r.append(text.substring(begin, last))
      r
    }

    val ws = scan(text.size - 2)

    val r = out.Comment.make
    r.tags = new ArrayBuffer

    @inline def amend(text : ListBuffer[String]) {
      if (null == r.text)
        r.text = text.to
      else
        r.tags.last.text = text.to
    }

    @inline def amendTag(text : ListBuffer[String], tag : String) {
      if (null == r.text)
        r.text = text.to
      else
        r.tags.last.text = text.to

      r.tags += out.CommentTag
        .build
        .name(tag)
        .make
    }

    @tailrec def parse(ws : ListBuffer[String], text : ListBuffer[String]) : Unit =
      if (ws.isEmpty) amend(text)
      else (ws.head, ws.tail) match {
        case ("\n", ws) if (ws.isEmpty)     ⇒ amend(text)
        case ("\n", ws) if (ws.head == "*") ⇒ parse(ws.tail, text)
        case ("\n", ws)                     ⇒ parse(ws, text)
        case (w, ws) if w.matches("""\*?@.+""") ⇒
          val end = if (w.contains(":")) w.lastIndexOf(':') else w.size
          val tag = w.substring(w.indexOf('@') + 1, end).toLowerCase
          amendTag(text, tag); parse(ws, ListBuffer[String]())
        case (w, ws) ⇒ text.append(w); parse(ws, text)
      }

    parse(ws, ListBuffer[String]())

    r
  }

  // ogssname -> identifier
  private val idCache = new HashMap[String, Identifier]
  /**
   * Create an identifier from string
   *
   * @note results are deduplicated
   */
  final def toIdentifier(text : String) : Identifier = {
    if (null == text)
      return null

    var parts = ArrayBuffer(text)

    // split into parts at _
    parts = parts.flatMap(_.split("_").map { s ⇒ if (s.isEmpty()) "_" else s }.to)

    // split before changes between lowercase to Uppercase
    parts = parts.flatMap { s ⇒
      val parts = ArrayBuffer[String]()
      var last = 0
      for (i ← 1 until s.length - 1) {
        if (s.charAt(i - 1).isLower && s.charAt(i).isUpper) {
          parts += s.substring(last, i)
          last = i
        }
      }
      parts += s.substring(last)
      parts
    }

    // create OGSS representation
    val ogssName = parts.map(_.capitalize).mkString

    idCache.getOrElseUpdate(
      ogssName,
      out.Identifier
        .build
        .ogss(ogssName)
        .parts(parts.to)
        .make
    )
  }

  private val SPosCache = new HashMap[(String, Int, Int), SourcePosition]
  final def makeSPos(from : Positioned) : SourcePosition = SPosCache.getOrElseUpdate(
    (from.declaredInFile, from.pos.line, from.pos.column),
    out.SourcePosition
      .build
      .file(from.declaredInFile)
      .column(from.pos.column)
      .line(from.pos.line)
      .make
  )

  final def printPosition(pos : SourcePosition) = s"${pos.file} ${pos.line}:${pos.column}"

  private val arrayTypes = new HashMap[Type, ArrayType]
  final def makeArray(t : Type) : ArrayType = arrayTypes.getOrElseUpdate(
    t,
    out.ArrayType
      .build
      .baseType(t)
      .make
  )
  private val listTypes = new HashMap[Type, ListType]
  final def makeList(t : Type) : ListType = listTypes.getOrElseUpdate(
    t,
    out.ListType
      .build
      .baseType(t)
      .make
  )
  private val setTypes = new HashMap[Type, SetType]
  final def makeSet(t : Type) : SetType = setTypes.getOrElseUpdate(
    t,
    out.SetType
      .build
      .baseType(t)
      .make
  )
  private val mapTypes = new HashMap[(Type, Type), MapType]
  final def makeMap(k : Type, v : Type) : MapType = mapTypes.getOrElseUpdate(
    (k, v),
    out.MapType
      .build
      .keyType(k)
      .valueType(v)
      .make
  )

  private val namedTypes = new HashMap[Identifier, Type]
  final def makeNamedType(name : Identifier, pos : SourcePosition = null) : Type = namedTypes.getOrElseUpdate(
    name,
    name.ogss match {
      case "Bool"   ⇒ out.BuiltinType.build.stid(0).name(name).make
      case "I8"     ⇒ out.BuiltinType.build.stid(1).name(name).make
      case "I16"    ⇒ out.BuiltinType.build.stid(2).name(name).make
      case "I32"    ⇒ out.BuiltinType.build.stid(3).name(name).make
      case "I64"    ⇒ out.BuiltinType.build.stid(4).name(name).make
      case "V64"    ⇒ out.BuiltinType.build.stid(5).name(name).make
      case "F32"    ⇒ out.BuiltinType.build.stid(6).name(name).make
      case "F64"    ⇒ out.BuiltinType.build.stid(7).name(name).make
      case "AnyRef" ⇒ out.BuiltinType.build.stid(8).name(name).make
      case "String" ⇒ out.BuiltinType.build.stid(9).name(name).make

      case _ ⇒ out.UserDefinedType.find(_.name == name).getOrElse(
        if (null != pos)
          reportError(pos, s"Undeclared type ${name.ogss}")
        else
          reportError(s"Undeclared type ${name.ogss}")
      )
    }
  )

  /**
   * Normalize out, i.e.
   * - initialize non-null container fields
   * - create baseType
   * - calculate subTypes
   * - create unprojected TC
   * - sort enums
   * - create type names of compound types
   * - set STIDs
   * - set KCCs
   *
   * @note normalize assumes no order and is therefore slower than SKilL TC-construction
   */
  protected def normalize {
    // ensure existence of builtin types
    {
      var builtins = out.BuiltinType.map(_.stid).toSet
      for (i ← 0 until 10)
        if (!builtins(i))
          i match {
            case 0 ⇒ makeNamedType(toIdentifier("Bool"))
            case 1 ⇒ makeNamedType(toIdentifier("I8"))
            case 2 ⇒ makeNamedType(toIdentifier("I16"))
            case 3 ⇒ makeNamedType(toIdentifier("I32"))
            case 4 ⇒ makeNamedType(toIdentifier("I64"))
            case 5 ⇒ makeNamedType(toIdentifier("V64"))
            case 6 ⇒ makeNamedType(toIdentifier("F32"))
            case 7 ⇒ makeNamedType(toIdentifier("F64"))
            case 8 ⇒ makeNamedType(toIdentifier("AnyRef"))
            case 9 ⇒ makeNamedType(toIdentifier("String"))
          }
    }

    // normalize custom fields
    {
      val noArgs = new ArrayBuffer[String]
      for (c ← out.CustomFieldOption)
        if (null == c.arguments)
          c.arguments = noArgs
    }
    {
      val noOpts = new ArrayBuffer[CustomFieldOption]
      for (c ← out.CustomField)
        if (null == c.options)
          c.options = noOpts
    }

    val tc = out.TypeContext.make

    // normalize inheritance hierarchies
    {
      // calculate all super types to perform type hierarchy normalization as set opertions
      val allSuperOf = out.WithInheritance.map(t ⇒ (t, IRUtils.allSuperTypes(t))).toMap

      for (c ← out.WithInheritance) {
        // calculate super frontier
        val superCS : HashSet[ClassDef] = allSuperOf(c).collect { case c : ClassDef ⇒ c }
        for (x ← superCS.flatMap(allSuperOf)) {
          x match {
            case x : ClassDef ⇒ superCS -= x
            case _            ⇒
          }
        }

        val superIS : HashSet[InterfaceDef] = allSuperOf(c).collect { case i : InterfaceDef ⇒ i }
        for (x ← allSuperOf(c).flatMap(allSuperOf)) {
          x match {
            case x : InterfaceDef ⇒ superIS -= x
            case _                ⇒
          }
        }

        if (superCS.size > 1) {
          reportError(c.pos, s"Type ${c.name.ogss} has multiple distinct super classes: ${superCS.map(_.name.ogss).mkString(", ")}")
        }
        superCS.foreach { sup ⇒
          if (sup == c)
            reportError(c.pos, s"Type ${c.name.ogss} is its own super class")

          if (sup != c.superType) {
            if (null != c.superType) {
              reportWarning(c.pos, s"Type ${c.name.ogss} has super classes ${c.superType.name.ogss}, but should have ${sup.name.ogss} instead")
            }
            c.superType = sup
          }
        }

        // fix self cycles
        if (c == c.superType) {
          reportError(c.pos, s"Type ${c.name.ogss} is its own super class")
          c.superType = null
        }

        // set correct base type
        if (null != c.superType) {
          c.baseType = allSuperOf(c).collect { case c : ClassDef if c.superType == null ⇒ c }.headOption.getOrElse {
            reportWarning(c.pos, s"Type ${c.name.ogss} has cyclic super classes")
            null
          }
        } else {
          c match {
            case c : ClassDef ⇒ c.baseType = c
            case _            ⇒
          }
        }

        for (sup ← c.superInterfaces -- superIS) {
          reportWarning(c.pos, s"Type ${c.name.ogss} has an unneeded super interface ${sup.name.ogss}.")
        }
        // reorder super interfaces
        c.superInterfaces = superIS.toSeq.sortBy(_.name.ogss).to

        // Also, ensure FieldLikes
        if (null == c.customs)
          c.customs = new ArrayBuffer

        if (null == c.fields)
          c.fields = new ArrayBuffer

        if (null == c.views)
          c.views = new ArrayBuffer
      }

      // reorder TC interfaces
      tc.interfaces = out.InterfaceDef.toSeq.sortBy(
        t ⇒ (allSuperOf(t).size, t.name.ogss.length, t.name.ogss)
      ).to
    }

    // normalize fieldLike orders
    {
      for (c ← out.WithInheritance) {
        c.customs.sortWith(IRUtils.ogssLess)
        c.fields.sortWith(IRUtils.ogssLess)
        c.views.sortWith(IRUtils.ogssLess)
      }
    }

    // aliases require no further action
    tc.aliases = out.TypeAlias.toSeq.sortBy(_.name.ogss).to

    // normalize enums
    tc.enums =
      (for (c ← out.EnumDef) yield {
        c.values.sortBy(v ⇒ (v.name.ogss.size, v.name.ogss))
        c
      }).toSeq.sortBy(_.name.ogss).to

    // sort classes by pathname and subtypes with ogssLess
    tc.classes = out.ClassDef.toSeq.sortBy(IRUtils.pathName).to
    for (c ← tc.classes ++ tc.interfaces) {
      c.subTypes = c.subTypes.sortWith(IRUtils.ogssLess).to
    }

    // sort containers by name and create names
    def ensureName(c : Type) : String = {
      if (null == c.name)
        c.name = (c match {
          case c : ArrayType ⇒ toIdentifier(s"${ensureName(c.baseType)}[]")
          case c : ListType  ⇒ toIdentifier(s"list<${ensureName(c.baseType)}>")
          case c : SetType   ⇒ toIdentifier(s"set<${ensureName(c.baseType)}>")
          case c : MapType   ⇒ toIdentifier(s"map<${ensureName(c.keyType)},${ensureName(c.valueType)}>")
        })
      c.name.ogss
    }
    out.ContainerType.foreach(ensureName)
    tc.containers = out.ContainerType.toSeq.sortWith(IRUtils.ogssLess).to

    // calculate variable STIDs
    IRUtils.recalculateSTIDs(tc)

    // create byName
    val tbn = new HashMap[String, Type]
    tc.byName = tbn
    for (c ← out.Type)
      tbn.put(c.name.ogss, c)

    // set missing attrs to empty array
    {
      val none = new ArrayBuffer[Attribute]
      for (
        t ← out.UserDefinedType if null == t.attrs
      ) t.attrs = none

      for (
        f ← out.Field if null == f.attrs
      ) f.attrs = none
    }
  }
}
