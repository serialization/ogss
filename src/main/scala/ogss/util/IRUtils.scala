package ogss.util

import ogss.oil.Identifier
import ogss.oil.WithInheritance
import ogss.oil.FieldLike
import ogss.oil.Field
import scala.collection.mutable.HashSet
import scala.collection.JavaConverters._
import ogss.oil.Type

/**
 * Utility functions that simplify working with some OIL classes.
 */
trait IRUtils {

  def allSuperTypes(t : WithInheritance, seen : HashSet[WithInheritance] = new HashSet) : HashSet[WithInheritance] = {
    if (!seen.contains(t)) {
      if (null != t.getSuperType)
        allSuperTypes(t.getSuperType, seen);

      for (s ← t.getSuperInterfaces.asScala)
        allSuperTypes(s, seen)
    }

    return seen
  }

  def allFields(t : WithInheritance) : HashSet[Field] = {
    allSuperTypes(t).flatMap(_.getFields.asScala) ++ t.getFields.asScala
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
}

object IRUtils extends IRUtils;