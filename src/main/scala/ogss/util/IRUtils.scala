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
    var nextSTID = 10
    for (c ← asScalaBuffer(tc.getClasses)) {
      c.setStid(nextSTID)
      nextSTID += 1
    }
    for (c ← asScalaBuffer(tc.getContainers)) {
      c.setStid(nextSTID)
      nextSTID += 1
    }
    for (c ← asScalaBuffer(tc.getEnums)) {
      c.setStid(nextSTID)
      nextSTID += 1
    }
  }
}

object IRUtils extends IRUtils;