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

/**
 * Utility functions that simplify working with some OIL classes.
 */
trait IRUtils {

  def allSuperTypes(t : WithInheritance, seen : HashSet[WithInheritance] = new HashSet) : HashSet[WithInheritance] = {
    if (null != t && !seen.contains(t)) {
      if (null != t.getSuperType)
        allSuperTypes(t.getSuperType, seen);

      for (s ← t.getSuperInterfaces.asScala)
        allSuperTypes(s, seen)
    }

    return seen
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
      c.setStid(nextSTID)
      nextSTID += 1
    }
    for (c ← tc.getEnums.asScala) {
      c.setStid(nextSTID)
      nextSTID += 1
    }

    // new KCCs
    for (c ← tc.getContainers.asScala) {
      c match {
        case c : ArrayType ⇒ c.setKcc(c.getBaseType.getStid & 0x7FFF)
        case c : ListType  ⇒ c.setKcc((1 << 30) | (c.getBaseType.getStid & 0x7FFF))
        case c : SetType   ⇒ c.setKcc((2 << 30) | (c.getBaseType.getStid & 0x7FFF))
        case c : MapType   ⇒ c.setKcc((3 << 30) | (c.getKeyType.getStid & 0x7FFF) | ((c.getValueType.getStid & 0x7FFF) << 15))
      }
    }
  }
}

object IRUtils extends IRUtils;
