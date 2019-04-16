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

import ogss.oil.OGFile;
import ogss.oil.Comment
import ogss.oil.Identifier
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import java.util.ArrayList
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import ogss.oil.Type
import ogss.oil.ArrayType
import ogss.oil.ListType
import ogss.oil.SetType
import ogss.oil.MapType
import ogss.oil.CustomFieldOption
import ogss.oil.View
import ogss.oil.InterfaceDef
import ogss.oil.ClassDef
import ogss.util.IRUtils
import scala.collection.mutable.HashSet
import ogss.oil.SourcePosition
import scala.util.parsing.input.Positional

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

    val r = out.Comments.make()
    r.setTags(new ArrayList)

    @inline def ammend(text : ListBuffer[String]) = {
      if (null == r.getText)
        r.setText(new ArrayList(asJavaCollection(text)))
      else
        r.getTags.get(r.getTags.size() - 1).setText(new ArrayList(asJavaCollection(text)))
    }

    @inline def ammendTag(text : ListBuffer[String], tag : String) = {
      if (null == r.getText)
        r.setText(new ArrayList(asJavaCollection(text)))
      else
        r.getTags.get(r.getTags.size() - 1).setText(new ArrayList(asJavaCollection(text)))
      val t = out.CommentTags.make()
      r.getTags.add(t)
      t.setName(tag)
    }

    @tailrec def parse(ws : ListBuffer[String], text : ListBuffer[String]) : Unit =
      if (ws.isEmpty) ammend(text)
      else (ws.head, ws.tail) match {
        case ("\n", ws) if (ws.isEmpty)     ⇒ ammend(text)
        case ("\n", ws) if (ws.head == "*") ⇒ parse(ws.tail, text)
        case ("\n", ws)                     ⇒ parse(ws, text)
        case (w, ws) if w.matches("""\*?@.+""") ⇒
          val end = if (w.contains(":")) w.lastIndexOf(':') else w.size
          val tag = w.substring(w.indexOf('@') + 1, end).toLowerCase
          ammendTag(text, tag); parse(ws, ListBuffer[String]())
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

    // split before changes from uppercase to lowercase
    parts = parts.flatMap { s ⇒
      val parts = ArrayBuffer[String]()
      var last = 0
      for (i ← 1 until s.length - 1) {
        if (s.charAt(i).isUpper && s.charAt(i + 1).isLower) {
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
      {
        val r = out.Identifiers.make
        r.setOgss(ogssName)
        r.setParts(new ArrayList(asJavaCollection(parts)))
        r
      }
    )
  }

  private val SPosCache = new HashMap[(String, Int, Int), SourcePosition]
  final def makeSPos(from : Positioned) : SourcePosition = SPosCache.getOrElseUpdate(
    (from.declaredInFile, from.pos.line, from.pos.column), {
      val r = out.SourcePositions.make()
      r.setFile(from.declaredInFile)
      r.setColumn(from.pos.column)
      r.setLine(from.pos.line)
      r
    }
  )

  final def printPosition(pos : SourcePosition) = s"${pos.getFile} ${pos.getLine}:${pos.getColumn}"

  private val arrayTypes = new HashMap[Type, ArrayType]
  final def makeArray(t : Type) : ArrayType = arrayTypes.getOrElseUpdate(
    t,
    {
      val r = out.ArrayTypes.make()
      r.setBaseType(t)
      r
    }
  )
  private val listTypes = new HashMap[Type, ListType]
  final def makeList(t : Type) : ListType = listTypes.getOrElseUpdate(
    t,
    {
      val r = out.ListTypes.make()
      r.setBaseType(t)
      r
    }
  )
  private val setTypes = new HashMap[Type, SetType]
  final def makeSet(t : Type) : SetType = setTypes.getOrElseUpdate(
    t,
    {
      val r = out.SetTypes.make()
      r.setBaseType(t)
      r
    }
  )
  private val mapTypes = new HashMap[(Type, Type), MapType]
  final def makeMap(k : Type, v : Type) : MapType = mapTypes.getOrElseUpdate(
    (k, v),
    {
      val r = out.MapTypes.make()
      r.setKeyType(k)
      r.setValueType(v)
      r
    }
  )

  private val namedTypes = new HashMap[Identifier, Type]
  final def makeNamedType(name : Identifier) : Type = namedTypes.getOrElseUpdate(
    name,
    name.getOgss match {
      case "Bool" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(0)
        r.setName(name)
        r
      case "I8" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(1)
        r.setName(name)
        r
      case "I16" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(2)
        r.setName(name)
        r
      case "I32" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(3)
        r.setName(name)
        r
      case "I64" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(4)
        r.setName(name)
        r
      case "V64" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(5)
        r.setName(name)
        r
      case "F32" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(6)
        r.setName(name)
        r
      case "F64" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(7)
        r.setName(name)
        r
      case "AnyRef" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(8)
        r.setName(name)
        r
      case "String" ⇒
        val r = out.BuiltinTypes.make()
        r.setStid(9)
        r.setName(name)
        r

      case _ ⇒
        val ts = out.UserDefinedTypes.iterator()
        while (ts.hasNext()) {
          val r = ts.next
          if (name == r.getName)
            return r
        }

        reportError(s"Undeclared type ${name.getOgss}")
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
      var builtins = out.BuiltinTypes.asScala.map(_.getStid).toSet
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
      val noArgs = new ArrayList[String]
      for (c ← asScalaIterator(out.CustomFieldOptions.iterator()))
        if (null == c.getArguments)
          c.setArguments(noArgs)
    }
    {
      val noOpts = new ArrayList[CustomFieldOption]
      for (c ← asScalaIterator(out.CustomFields.iterator()))
        if (null == c.getOptions)
          c.setOptions(noOpts)
    }

    val tc = out.TypeContexts.make()

    // normalize inheritance hierarchies
    {
      // calculate all super types to perform type hierarchy normalization as set opertions
      val allSuperOf = out.WithInheritances.asScala.map(t ⇒ (t, IRUtils.allSuperTypes(t))).toMap

      for (c ← out.WithInheritances.asScala) {
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
          reportError(c.getPos, s"Type ${c.getName.getOgss} has multiple distinct super classes: ${superCS.map(_.getName.getOgss).mkString(", ")}")
        }
        superCS.foreach { sup ⇒
          if (sup == c)
            reportError(c.getPos, s"Type ${c.getName.getOgss} is its own super class")

          if (sup != c.getSuperType) {
            if (null != c.getSuperType) {
              reportWarning(c.getPos, s"Type ${c.getName.getOgss} has super classes ${c.getSuperType.getName.getOgss}, but should have ${sup.getName.getOgss} instead")
            }
            c.setSuperType(sup)
          }
        }
        // set correct base type
        if (null != c.getSuperType) {
          c.setBaseType(allSuperOf(c).collect { case c : ClassDef if c.getSuperType == null ⇒ c }.head)
        } else {
          c match {
            case c : ClassDef ⇒ c.setBaseType(c)
            case _            ⇒
          }
        }

        for (sup ← c.getSuperInterfaces.asScala -- superIS) {
          reportWarning(c.getPos, s"Type ${c.getName.getOgss} has an unneeded super interface ${sup.getSuperType.getName.getOgss}.")
        }
        // reorder super interfaces
        c.setSuperInterfaces(new ArrayList(superIS.toSeq.sortBy(_.getName.getOgss).asJava))

        // Also, ensure FieldLikes
        if (null == c.getCustoms)
          c.setCustoms(new ArrayList)

        if (null == c.getFields)
          c.setFields(new ArrayList)

        if (null == c.getViews)
          c.setViews(new ArrayList)
      }

      // reorder TC interfaces
      tc.setInterfaces(new ArrayList(asScalaIterator(out.InterfaceDefs.iterator()).toSeq.sortBy(
        t ⇒ (allSuperOf(t).size, t.getName.getOgss)
      ).asJava))
    }

    // normalize fieldLike orders
    {
      for (c ← out.WithInheritances.asScala) {
        c.getCustoms.sort { (l, r) ⇒
          val cmp = Integer.compare(l.getName.getOgss.length(), r.getName.getOgss.length())
          if (0 != cmp) cmp
          else l.getName.getOgss.compareTo(r.getName.getOgss)
        }
        c.getFields.sort { (l, r) ⇒
          val cmp = Integer.compare(l.getName.getOgss.length(), r.getName.getOgss.length())
          if (0 != cmp) cmp
          else l.getName.getOgss.compareTo(r.getName.getOgss)
        }
        c.getViews.sort { (l, r) ⇒
          val cmp = Integer.compare(l.getName.getOgss.length(), r.getName.getOgss.length())
          if (0 != cmp) cmp
          else l.getName.getOgss.compareTo(r.getName.getOgss)
        }
      }
    }

    // aliases require no further action
    tc.setAliases(new ArrayList(out.TypeAliass.asScala.toSeq.sortBy(_.getName.getOgss).asJavaCollection))

    // normalize enums
    tc.setEnums(new ArrayList(
      (for (c ← asScalaIterator(out.EnumDefs.iterator())) yield {
        c.setValues(new ArrayList(c.getValues.asScala.sortBy(_.getName.getOgss).asJavaCollection))
        c
      }).toSeq.sortBy(_.getName.getOgss).asJavaCollection
    ))

    // classes require complex sorting
    val pathNameCache = new HashMap[ClassDef, String]
    def pathName(cls : ClassDef) : String = {
      if (null == cls.getSuperType)
        cls.getName.getOgss
      else {
        pathNameCache.getOrElseUpdate(cls, pathName(cls.getSuperType) + "\0" + cls.getName.getOgss)
      }
    }

    val classes = asScalaIterator(out.ClassDefs.iterator()).toSeq.map(c ⇒ (pathName(c), c)).sortBy(p ⇒ p._1).map(_._2)
    tc.setClasses(new ArrayList(classes.asJavaCollection))

    // sort containers by name and create names
    def ensureName(c : Type) : String = {
      if (null == c.getName)
        c.setName(c match {
          case c : ArrayType ⇒ toIdentifier(s"${ensureName(c.getBaseType)}[]")
          case c : ListType  ⇒ toIdentifier(s"list<${ensureName(c.getBaseType)}>")
          case c : SetType   ⇒ toIdentifier(s"set<${ensureName(c.getBaseType)}>")
          case c : MapType   ⇒ toIdentifier(s"map<${ensureName(c.getKeyType)},${ensureName(c.getValueType)}>")
        })
      c.getName.getOgss
    }
    out.ContainerTypes.asScala.foreach(ensureName)
    tc.setContainers(new ArrayList(
      out.ContainerTypes.asScala.toSeq.sortBy(c ⇒ (c.getName.getOgss.length(), c.getName.getOgss)).asJavaCollection
    ))

    // calculate variable STIDs
    IRUtils.recalculateSTIDs(tc)

    // create byName
    val tbn = new java.util.HashMap[String, Type]
    tc.setByName(tbn)
    for (c ← out.Types.asScala)
      tbn.put(c.getName.getOgss, c)
  }
}
