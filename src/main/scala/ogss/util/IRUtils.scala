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
import scala.collection.mutable.HashSet

import ogss.oil.ArrayType
import ogss.oil.ContainerType
import ogss.oil.CustomField
import ogss.oil.Field
import ogss.oil.FieldLike
import ogss.oil.Identifier
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.TypeContext
import ogss.oil.View
import ogss.oil.WithInheritance
import ogss.oil.WithName
import scala.collection.mutable.WeakHashMap
import com.sun.org.apache.xml.internal.serializer.ToStream

/**
 * Utility functions that simplify working with some OIL classes.
 */
trait IRUtils {

  def isInteger(t : Type) : Boolean = 1 <= t.stid && t.stid <= 5

  def allSuperTypes(t : WithInheritance) : HashSet[WithInheritance] = {
    val r = new HashSet[WithInheritance]
    allSuperTypes(t, r)
    r -= t
    r
  }

  private def allSuperTypes(t : WithInheritance, seen : HashSet[WithInheritance]) {
    if (null != t && !seen.contains(t)) {
      seen += t

      if (null != t.superType)
        allSuperTypes(t.superType, seen);

      for (s ← t.superInterfaces)
        allSuperTypes(s, seen)
    }
  }

  def allCustoms(t : WithInheritance) : HashSet[CustomField] = {
    allSuperTypes(t).flatMap(_.customs) ++ t.customs
  }

  def allFields(t : WithInheritance) : HashSet[Field] = {
    if (null == t) HashSet()
    else allSuperTypes(t).flatMap(_.fields) ++ t.fields
  }

  def allViews(t : WithInheritance) : HashSet[View] = {
    allSuperTypes(t).flatMap(_.views) ++ t.views
  }

  def isDistributed(f : Field) : Boolean = {
    // TODO check distributed or lazy attribute
    false
  }

  /**
   * query for distributed in any field. This is required to create a correct API in the presence of
   * distributed fields.
   *
   * @return true, if the type or a super type has a distributed field
   */
  def hasDistributedField(t : WithInheritance) : Boolean = allFields(t).exists(isDistributed)

  def ogssname(t : WithName) : String = {
    t.name.ogss
  }

  // classes require complex sorting
  class PathName(val names : Array[Identifier]) extends Ordered[PathName] {
    override def compare(r : PathName) : Int = {
      var i = 0
      while (i < names.size && i < r.names.size) {
        if (names(i) != r.names(i)) {
          if (ogssLess(names(i), r.names(i)))
            return -1
          else
            return 1
        }
        i += 1
      }
      return names.size - r.names.size
    }
    override def toString = names.mkString("⇒")
  }
  private val pathNameCache = new WeakHashMap[WithInheritance, PathName]
  private val currentPathConstruction = new HashSet[WithInheritance]
  def pathName(t : WithInheritance) : PathName = synchronized {
    if (currentPathConstruction.contains(t)) {
      throw new IllegalStateException(s"Type ${t.name.ogss} has a cyclic path name, i.e. cyclic super classes.")
    }

    currentPathConstruction += t
    try {
      if (null == t.superType)
        new PathName(Array(t.name))
      else {
        pathNameCache.getOrElseUpdate(t, new PathName(pathName(t.superType).names ++ Seq(t.name)))
      }
    } finally {
      currentPathConstruction -= t
    }
  }

  def adaStyle(id : Identifier) : String = {
    var r = id.adaStyle
    if (null == r) {
      var first = true
      r = id.parts.map(_.capitalize).mkString("_")
      id.adaStyle = r
    }
    r
  }

  def cStyle(id : Identifier) : String = {
    var r = id.cStyle
    if (null == r) {
      var first = true
      r = id.parts.map(_.toLowerCase()).mkString("_")
      id.cStyle = r
    }
    r
  }

  def camel(id : Identifier) : String = {
    var r = id.camelCase
    if (null == r) {
      var first = true
      r = id.parts.map {
        p ⇒
          if (first) {
            first = false
            if (p.length() > 1 && p.charAt(1).isUpper) p
            else p.toLowerCase()
          } else
            p.capitalize
      }.mkString
      id.camelCase = r
    }
    r
  }

  def capital(id : Identifier) : String = {
    var r = id.capitalCase
    if (null == r) {
      r = id.parts.map(_.capitalize).mkString
      id.capitalCase = r
    }
    r
  }

  def lowercase(id : Identifier) : String = {
    var r = id.lowercase
    if (null == r) {
      r = id.parts.map(_.toLowerCase).mkString
      id.lowercase = r
    }
    r
  }

  def ogssLess(left : String, right : String) : Boolean = {
    val llen = left.length
    val rlen = right.length
    if (llen != rlen) llen < rlen
    else left < right
  }

  def ogssLess(left : Identifier, right : Identifier) : Boolean = {
    val llen = left.ogss.length
    val rlen = right.ogss.length
    if (llen != rlen) llen < rlen
    else left.ogss < right.ogss
  }

  def ogssLess(left : WithName, right : WithName) : Boolean = ogssLess(left.name, right.name)

  /**
   * Recalculate STIDs and KCCs in a type context.
   */
  def recalculateSTIDs(tc : TypeContext) {
    // STIDs
    var nextSTID = 10
    for (c ← tc.classes) {
      c.stid = nextSTID
      nextSTID += 1
    }
    for (c ← tc.containers) {
      // containers require reordering; we use negative stid to track state
      c.stid = -1
      nextSTID += 1
    }
    for (c ← tc.enums) {
      c.stid = nextSTID
      nextSTID += 1
    }

    // new KCCs, reorder containers
    // @note this algorithms complexity could be reduced below O(n²) with a heap that allows changing keys
    if (!tc.containers.isEmpty) {
      nextSTID = 10 + tc.classes.size

      val cs : HashSet[ContainerType] = tc.containers.to
      // ucc -> type
      val ready = new HashMap[Long, ContainerType]
      val done = new ArrayBuffer[ContainerType](cs.size)

      // while there are pending types
      while (!cs.isEmpty || !ready.isEmpty) {
        if (!cs.isEmpty) {
          cs.toArray.foreach { c ⇒
            val UCC = ucc(c)
            if (-1 != UCC) {
              c.kcc = kcc(c)
              ready(UCC) = c
              cs -= c
            }
          }
        }

        val mc = ready.keySet.min
        val dc = ready.remove(mc).get
        done += dc
        dc.stid = nextSTID
        nextSTID += 1
      }

      tc.containers.clear
      tc.containers ++= done
    }

    // types without STIDS
    for (c ← tc.aliases) {
      c.stid = -1
    }
    for (c ← tc.interfaces) {
      c.stid = -1
    }
  }

  /**
   * we use Long, because JVM provides no uint32_t
   */
  def ucc(kcc : Integer) : Long = {
    val stid1 = kcc.toLong & 0x7FFFL;
    val stid2 = (kcc.toLong >> 15L) & 0x7FFFL;
    val kind = (kcc.toLong >> 30L) & 0x3L;
    if (stid1 > stid2)
      (stid1 << 17L) | (kind << 15L) | (stid2)
    else
      (stid2 << 17L) | (kind << 15L) | (stid1)
  }

  /**
   * we use Long, because JVM provides no uint32_t
   * @return -1L, if no ucc can be assigned yet
   */
  def ucc(ct : ContainerType) : Long = {
    val stid1 = ct match {
      case c : ArrayType ⇒ c.baseType.stid
      case c : ListType  ⇒ c.baseType.stid
      case c : SetType   ⇒ c.baseType.stid
      case c : MapType   ⇒ c.keyType.stid
    }
    if (-1 == stid1)
      return -1

    val stid2 = ct match {
      case c : MapType ⇒ c.valueType.stid
      case _           ⇒ 0
    }
    if (-1 == stid2)
      return -1

    val kind = ct match {
      case c : ArrayType ⇒ 0
      case c : ListType  ⇒ 1
      case c : SetType   ⇒ 2
      case c : MapType   ⇒ 3
    }

    if (stid1 > stid2)
      (stid1 << 17L) | (kind << 15L) | (stid2)
    else
      (stid2 << 17L) | (kind << 15L) | (stid1)
  }

  def kcc(c : ContainerType) : Int = c match {
    case c : ArrayType ⇒ (c.baseType.stid & 0x7FFF)
    case c : ListType  ⇒ ((1 << 30) | (c.baseType.stid & 0x7FFF))
    case c : SetType   ⇒ ((2 << 30) | (c.baseType.stid & 0x7FFF))
    case c : MapType   ⇒ ((3 << 30) | (c.keyType.stid & 0x7FFF) | ((c.valueType.stid & 0x7FFF) << 15))
  }
}

object IRUtils extends IRUtils;
