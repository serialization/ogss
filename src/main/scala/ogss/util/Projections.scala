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
package ogss.util

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import ogss.main.CommandLine
import ogss.oil.ArrayType
import ogss.oil.ClassDef
import ogss.oil.ContainerType
import ogss.oil.CustomField
import ogss.oil.Field
import ogss.oil.Identifier
import ogss.oil.InterfaceDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.OGFile
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.TypeAlias
import ogss.oil.TypeContext
import ogss.oil.View
import ogss.oil.WithInheritance
import ogss.oil.Type
import scala.collection.mutable.HashSet
import ogss.oil.Attribute

/**
 * Takes an unprojected .oil-file and creates interface and typedef
 * projections.
 */
class Projections(sg : OGFile) {

  private def run {
    if (sg.TypeContext.size != 1)
      throw new IllegalStateException(s"expected exactly one type context, but found ${sg.TypeContext.size}")

    val tc = sg.TypeContext.head
    if (tc.projectedInterfaces || tc.projectedTypeDefinitions)
      throw new IllegalStateException(s"""expected an unprojected type context, but found ${sg.TypeContext.size}
  interfaces: ${tc.projectedInterfaces}
  typedefs: ${tc.projectedTypeDefinitions}""")

    if (!sg.Type.toSet.sameElements(tc.byName.values.toSet)) {
      throw new IllegalStateException(s"types and byName targets differ")
    }

    substituteInterfaces(substituteAliases(tc))
    substituteInterfaces(tc)
  }

  private def substituteAliases(tc : TypeContext) : TypeContext = {
    // note we cannot fast exit if no interfaces are available, because we are not allowed to reuse the same types

    // set trivial properties
    val r =
      sg.TypeContext
        .build
        .projectedInterfaces(tc.projectedInterfaces)
        .projectedTypeDefinitions(true)
        .aliases(new ArrayBuffer)
        .enums(tc.enums)
        .classes(copyClasses(tc.classes))
        .interfaces(copyInterfaces(tc.interfaces))
        .containers(new ArrayBuffer)
        .make

    // calculate type map
    val typeMap = new HashMap[Type, Type]
    makeTBN(r)

    for (c ← tc.aliases) {
      typeMap(c) = ensure(c.target, typeMap, r)
    }
    completeTypeMap(tc, r, typeMap)

    // fix type hierarchy
    for (c ← (r.classes ++ r.interfaces)) {
      // move super to this tc
      val sis = new ArrayBuffer[InterfaceDef]
      for (s ← c.superInterfaces) {
        val n = typeMap(s).asInstanceOf[InterfaceDef]
        sis += n
        n.subTypes += c
      }
      c.superInterfaces = sis

      if (null != c.superType) {
        c.superType = typeMap(c.superType).asInstanceOf[ClassDef]
        c.superType.subTypes += c
      }
      if (null != c.baseType)
        c.baseType = (typeMap(c.baseType) match {
          case null         ⇒ throw new Error("internal error")
          case c : ClassDef ⇒ c
        })

      // calculate new fields
      copyFields(typeMap, tc.byName(c.name.ogss).asInstanceOf[WithInheritance], c)
    }

    // sort classes by pathname and subtypes with ogssLess
    r.classes = r.classes.sortBy(IRUtils.pathName).to
    for (c ← r.classes ++ r.interfaces) {
      c.subTypes = c.subTypes.sortWith(IRUtils.ogssLess).to
    }

    // we may have changed types, so we need to recalculate STIDs and KCCs
    IRUtils.recalculateSTIDs(r)

    r
  }

  private def substituteInterfaces(tc : TypeContext) {
    // @note we cannot fast exit if no interfaces are available, because we are not allowed to reuse the same types

    // set trivial properties
    val r = sg.TypeContext
      .build
      .projectedInterfaces(true)
      .projectedTypeDefinitions(tc.projectedTypeDefinitions)
      .aliases(copyAliases(tc.aliases))
      .enums(tc.enums)
      .classes(copyClasses(tc.classes))
      .interfaces(new ArrayBuffer)
      .containers(new ArrayBuffer)
      .make

    // calculate type map
    val typeMap = new HashMap[Type, Type]
    for (c ← tc.interfaces) {
      typeMap(c) =
        if (null == c.superType) tc.byName("AnyRef")
        else r.classes.find(_.name == c.superType.name).get
    }
    makeTBN(r)
    completeTypeMap(tc, r, typeMap)

    // fix type hierarchy
    for (c ← r.classes) {
      // remove interfaces from the type hierarchy
      c.superInterfaces = r.interfaces
      if (null != c.superType) {
        c.superType = typeMap(c.superType).asInstanceOf[ClassDef]
        c.superType.subTypes += c
      }
      if (null != c.baseType)
        c.baseType = typeMap(c.baseType).asInstanceOf[ClassDef]
    }

    // sort classes by pathname and subtypes with ogssLess
    r.classes = r.classes.sortBy(IRUtils.pathName).to
    for (c ← r.classes) {
      c.subTypes = c.subTypes.sortWith(IRUtils.ogssLess).to
    }

    // calculate new fields
    for (c ← r.classes) {
      collectFields(typeMap, tc.byName(c.name.ogss).asInstanceOf[ClassDef], c)
    }

    // update type alias targets
    for (c ← tc.aliases) {
      c.target = typeMap(c.target)
    }

    // we may have changed types, so we need to recalculate STIDs and KCCs
    IRUtils.recalculateSTIDs(r)
  }

  /**
   * Collect fieldLikes from a type and its super interfaces and type them in
   * the new type context using typeMap
   */
  private def collectFields(typeMap : HashMap[Type, Type], from : ClassDef, target : ClassDef) {
    val cs = IRUtils.allCustoms(from)
    if (null != from.superType)
      cs --= IRUtils.allCustoms(from.superType)

    val tcs = new ArrayBuffer[CustomField]
    target.customs = tcs
    for (f ← cs.toArray.sortBy(f ⇒ (f.name.ogss.length(), f.name.ogss))) {
      val r = copy(f)
      r.owner = target
      tcs += r
    }

    val fs = IRUtils.allFields(from)
    if (null != from.superType)
      fs --= IRUtils.allFields(from.superType)

    val tfs = new ArrayBuffer[Field]
    target.fields = tfs
    for (f ← fs.toArray.sortBy(f ⇒ (f.name.ogss.length(), f.name.ogss))) {
      val r = copy(f, typeMap)
      r.owner = target
      tfs += r
    }

    val vs = IRUtils.allViews(from)
    if (null != from.superType)
      vs --= IRUtils.allViews(from.superType)

    val tvs = new ArrayBuffer[View]
    target.views = tvs
    for (f ← vs.toArray.sortBy(f ⇒ (f.name.ogss.length(), f.name.ogss))) {
      val r = copy(f, typeMap)
      if (null != r) {
        if (target == r.target.owner) {
          // project away fields which might alias with their targets
          sg.delete(r)
        } else {
          // add views which have not been projected away
          r.owner = target
          tvs += r
        }
      }
    }
  }

  private def copyFields(typeMap : HashMap[Type, Type], from : WithInheritance, target : WithInheritance) {
    val tcs = new ArrayBuffer[CustomField]
    target.customs = tcs
    for (f ← from.customs) {
      val r = copy(f)
      r.owner = target
      tcs += r
    }

    val tfs = new ArrayBuffer[Field]
    target.fields = tfs
    for (f ← from.fields) {
      val r = copy(f, typeMap)
      r.owner = target
      tfs += r
    }

    val tvs = new ArrayBuffer[View]
    target.views = tvs
    for (f ← from.views) {
      val r = copy(f, typeMap)
      if (null != r) {
        r.owner = target
        tvs += r
      }
    }
  }

  private def copy(f : CustomField) : CustomField = {
    sg.CustomField
      .build
      .comment(f.comment)
      .name(f.name)
      .pos(f.pos)
      .language(f.language)
      .options(f.options)
      .typename(f.typename)
      .make
  }
  /**
   * Copy a field but will not set owner to simplify moves in interface
   * projection!
   */
  private def copy(f : Field, typeMap : HashMap[Type, Type]) : Field = {
    sg.Field.build
      .isTransient(f.isTransient)
      .`type`(typeMap(f.`type`))
      .comment(f.comment)
      .attrs(copyAttributes(f.attrs))
      .name(f.name)
      .pos(f.pos)
      .make
  }
  private def copy(f : View, typeMap : HashMap[Type, Type]) : View = {
    // find target; if it is no longer there, we discard the view
    val target = (typeMap(f.target.owner) match {
      case null                ⇒ null
      case t : WithInheritance ⇒ t.fields.find(_.name == f.target.name).getOrElse(null)
      case _                   ⇒ null
    })

    if (null == target) {
      null
    } else {
      sg.View.build
        .name(f.name)
        .pos(f.pos)
        .comment(f.comment)
        .`type`(typeMap(f.`type`))
        .target(target)
        .make
    }
  }

  private def copyAttributes(as : ArrayBuffer[Attribute]) : ArrayBuffer[Attribute] = {
    if (as.isEmpty) as
    else as.map { a ⇒
      sg.Attribute.build
        .isSerialized(a.isSerialized)
        .name(a.name)
        .arguments(a.arguments.to)
        .make
    }.to
  }

  private def copyAliases(aliases : ArrayBuffer[TypeAlias]) : ArrayBuffer[TypeAlias] = {
    val r = new ArrayBuffer[TypeAlias]
    for (c ← aliases) {
      r += sg.TypeAlias.build
        .pos(c.pos)
        .comment(c.comment)
        .attrs(copyAttributes(c.attrs))
        .name(c.name)
        .target(c.target)
        .make
    }
    r
  }

  /**
   * note: does not copy subtypes, fields, custom and views, as those require a deep
   * copy; they are all initialized with empty arrays
   */
  private def copyClasses(classes : ArrayBuffer[ClassDef]) : ArrayBuffer[ClassDef] = {
    val r = new ArrayBuffer[ClassDef]
    for (c ← classes) {
      r += sg.ClassDef.build
        .pos(c.pos)
        .name(c.name)
        .comment(c.comment)
        .attrs(copyAttributes(c.attrs))
        .baseType(c.baseType)
        .superType(c.superType)
        .superInterfaces(c.superInterfaces)
        .subTypes(new ArrayBuffer)
        .customs(new ArrayBuffer)
        .fields(new ArrayBuffer)
        .views(new ArrayBuffer)
        .make
    }
    r
  }

  /**
   * note: does not copy subtypes, fields, custom and views, as those require a deep
   * copy; they are all initialized with empty arrays
   */
  private def copyInterfaces(interfaces : ArrayBuffer[InterfaceDef]) : ArrayBuffer[InterfaceDef] = {
    val r = new ArrayBuffer[InterfaceDef]
    for (c ← interfaces) {
      r += sg.InterfaceDef.build
        .pos(c.pos)
        .name(c.name)
        .comment(c.comment)
        .attrs(copyAttributes(c.attrs))
        .baseType(c.baseType)
        .superType(c.superType)
        .superInterfaces(c.superInterfaces)
        .subTypes(new ArrayBuffer)
        .customs(new ArrayBuffer)
        .fields(new ArrayBuffer)
        .views(new ArrayBuffer)
        .make
    }
    r
  }

  private def makeTBN(to : TypeContext) {
    val tbn = new HashMap[String, Type]
    to.byName = tbn
    for (c ← sg.BuiltinType)
      tbn.put(c.name.ogss, c)
    for (c ← to.aliases)
      tbn.put(c.name.ogss, c)
    for (c ← to.enums)
      tbn.put(c.name.ogss, c)
    for (c ← to.classes)
      tbn.put(c.name.ogss, c)
    for (c ← to.interfaces)
      tbn.put(c.name.ogss, c)
  }

  /**
   * Complete a given type mapping from one context to another by adding
   * identity mappings until every type can be mapped.
   * Also, container types will be created.
   *
   * @note will recalculate to.byName
   */
  private def completeTypeMap(from : TypeContext, to : TypeContext, typeMap : HashMap[Type, Type]) {

    from.byName.values.foreach(ensure(_, typeMap, to))

    to.containers.sortWith(IRUtils.ogssLess)
  }

  private def ensure(t : Type, typeMap : HashMap[Type, Type], to : TypeContext) : Type = {
    typeMap.getOrElseUpdate(t, t match {
      case t : ArrayType ⇒ {
        val b = ensure(t.baseType, typeMap, to)
        val name = toIdentifier(s"${b.name.ogss}[]")
        to.byName.getOrElseUpdate(
          name.ogss,
          {
            val t = sg.ArrayType
              .build
              .baseType(b)
              .name(name)
              .make
            to.containers += t
            t
          }
        )
      }
      case t : ListType ⇒ {
        val b = ensure(t.baseType, typeMap, to)
        val name = toIdentifier(s"list<${b.name.ogss}>")
        to.byName.getOrElseUpdate(
          name.ogss,
          {
            val t = sg.ListType
              .build
              .baseType(b)
              .name(name)
              .make
            to.containers += t
            t
          }
        )
      }
      case t : SetType ⇒ {
        val b = ensure(t.baseType, typeMap, to)
        val name = toIdentifier(s"set<${b.name.ogss}>")
        to.byName.getOrElseUpdate(
          name.ogss,
          {
            val t = sg.SetType
              .build
              .baseType(b)
              .name(name)
              .make
            to.containers += t
            t
          }
        )
      }
      case t : MapType ⇒ {
        val k = ensure(t.keyType, typeMap, to)
        val v = ensure(t.valueType, typeMap, to)
        val name = toIdentifier(s"map<${k.name.ogss},${v.name.ogss}>")
        to.byName.getOrElseUpdate(
          name.ogss,
          {
            val t = sg.MapType
              .build
              .keyType(k)
              .valueType(v)
              .name(name)
              .make
            to.containers += t
            t
          }
        )
      }
      case _ ⇒ to.byName(t.name.ogss)
    })
  }

  // ogssname -> identifier
  private val idCache = new HashMap[String, Identifier]
  for (id ← sg.Identifier)
    idCache(id.ogss) = id

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
      sg.Identifier
        .build
        .ogss(ogssName)
        .parts(parts)
        .make
    )
  }

  run
}
