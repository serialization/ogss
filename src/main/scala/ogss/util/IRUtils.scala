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

import ogss.oil.Identifier
import ogss.oil.WithInheritance
import ogss.oil.FieldLike
import ogss.oil.Field
import scala.collection.mutable.HashSet
import scala.collection.JavaConverters._
import ogss.oil.Type
import ogss.oil.TypeContext
import ogss.oil.CustomField
import ogss.oil.View
import ogss.oil.ArrayType
import ogss.oil.SetType
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.ContainerType
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

/**
 * Utility functions that simplify working with some OIL classes.
 */
trait IRUtils {

  def allSuperTypes(t : WithInheritance) : HashSet[WithInheritance] = {
    val r = new HashSet[WithInheritance]
    allSuperTypes(t, r)
    r -= t
    r
  }

  private def allSuperTypes(t : WithInheritance, seen : HashSet[WithInheritance]) {
    if (null != t && !seen.contains(t)) {
      seen += t

      if (null != t.getSuperType)
        allSuperTypes(t.getSuperType, seen);

      for (s ← t.getSuperInterfaces.asScala)
        allSuperTypes(s, seen)
    }
  }

  def allCustoms(t : WithInheritance) : HashSet[CustomField] = {
    allSuperTypes(t).flatMap(_.getCustoms.asScala) ++ t.getCustoms.asScala
  }

  def allFields(t : WithInheritance) : HashSet[Field] = {
    allSuperTypes(t).flatMap(_.getFields.asScala) ++ t.getFields.asScala
  }

  def allViews(t : WithInheritance) : HashSet[View] = {
    allSuperTypes(t).flatMap(_.getViews.asScala) ++ t.getViews.asScala
  }

  def ogssname(t : Type) : String = {
    t.getName.getOgss
  }
  def ogssname(f : FieldLike) : String = {
    f.getName.getOgss
  }

  def adaStyle(id : Identifier) : String = {
    var r = id.getAdaStyle
    if (null == r) {
      var first = true
      r = id.getParts.asScala.map(_.capitalize).mkString("_")
      id.setAdaStyle(r)
    }
    r
  }

  def cStyle(id : Identifier) : String = {
    var r = id.getCStyle
    if (null == r) {
      var first = true
      r = id.getParts.asScala.map(_.toLowerCase()).mkString("_")
      id.setCStyle(r)
    }
    r
  }

  def camel(id : Identifier) : String = {
    var r = id.getCamelCase
    if (null == r) {
      var first = true
      r = id.getParts.asScala.map {
        p ⇒
          if (first) {
            first = false
            if (p.length() > 1 && p.charAt(1).isUpper) p
            else p.toLowerCase()
          } else
            p.capitalize
      }.mkString
      id.setCamelCase(r)
    }
    r
  }

  def capital(id : Identifier) : String = {
    var r = id.getCapitalCase
    if (null == r) {
      r = id.getParts.asScala.map(_.capitalize).mkString
      id.setCapitalCase(r)
    }
    r
  }

  def lowercase(id : Identifier) : String = {
    var r = id.getLowercase
    if (null == r) {
      r = id.getParts.asScala.map(_.toLowerCase).mkString
      id.setLowercase(r)
    }
    r
  }

  /**
   * Recalculate STIDs and KCCs in a type context.
   */
  def recalculateSTIDs(tc : TypeContext) {
    // STIDs
    var nextSTID = 10
    for (c ← tc.getClasses.asScala) {
      c.setStid(nextSTID)
      nextSTID += 1
    }
    for (c ← tc.getContainers.asScala) {
      // containers require reordering; we use negative stid to track state
      c.setStid(-1)
      nextSTID += 1
    }
    for (c ← tc.getEnums.asScala) {
      c.setStid(nextSTID)
      nextSTID += 1
    }

    // new KCCs, reorder containers
    // @note this algorithms complexity could be reduced below O(n²) with a heap that allows changing keys
    if (!tc.getContainers.isEmpty) {
      nextSTID = 10 + tc.getClasses.size()

      val cs : HashSet[ContainerType] = tc.getContainers.asScala.to
      // ucc -> type
      val ready = new HashMap[Long, ContainerType]
      val done = new ArrayBuffer[ContainerType](cs.size)

      // while there are pending types
      while (!cs.isEmpty || !ready.isEmpty) {
        if (!cs.isEmpty) {
          cs.toArray.foreach { c ⇒
            val UCC = ucc(c)
            if (-1 != UCC) {
              c.setKcc(kcc(c))
              ready(UCC) = c
              cs -= c
            }
          }
        }

        val mc = ready.keySet.min
        val dc = ready.remove(mc).get
        done += dc
        dc.setStid(nextSTID)
        nextSTID += 1
      }

      tc.getContainers.clear()
      tc.getContainers.addAll(done.asJava)
    }

    // types without STIDS
    for (c ← tc.getAliases.asScala) {
      c.setStid(-1)
    }
    for (c ← tc.getInterfaces.asScala) {
      c.setStid(-1)
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
      case c : ArrayType ⇒ c.getBaseType.getStid
      case c : ListType  ⇒ c.getBaseType.getStid
      case c : SetType   ⇒ c.getBaseType.getStid
      case c : MapType   ⇒ c.getKeyType.getStid
    }
    if (-1 == stid1)
      return -1

    val stid2 = ct match {
      case c : MapType ⇒ c.getValueType.getStid
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
    case c : ArrayType ⇒ (c.getBaseType.getStid & 0x7FFF)
    case c : ListType  ⇒ ((1 << 30) | (c.getBaseType.getStid & 0x7FFF))
    case c : SetType   ⇒ ((2 << 30) | (c.getBaseType.getStid & 0x7FFF))
    case c : MapType   ⇒ ((3 << 30) | (c.getKeyType.getStid & 0x7FFF) | ((c.getValueType.getStid & 0x7FFF) << 15))
  }
}

object IRUtils extends IRUtils;
