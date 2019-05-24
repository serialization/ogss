package ogss.frontend.skill

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.JavaConverters._

import ogss.oil.FieldLike
import ogss.oil.Identifier
import ogss.oil.TypeAlias
import ogss.oil.UserDefinedType
import ogss.oil.View
import ogss.oil.Type
import ogss.frontend.common.FrontEnd
import ogss.oil.Field
import ogss.oil.WithInheritance
import ogss.oil.ClassDef
import ogss.oil.InterfaceDef

/**
 * Post processing transformations that have to performed after parsing all
 * files, because information flow can be across files.
 */
abstract class DefinitionPostProcessing(self : FrontEnd) extends CommonParseRules(self) {

  /**
   * A definition entry is created iff a type definition is encountered; super
   * type relations may exist before.
   */
  val definitions = new HashMap[Identifier, UserDefinedType]

  /**
   * Super type relations cannot be processed top-down
   */
  val superTypes = new HashMap[WithInheritance, HashSet[Identifier]]

  /**
   * Targets of views cannot be processed top-down
   */
  val superViews = new HashMap[View, (Identifier, Identifier)]

  /**
   * Field types cannot be processed top-down
   */
  val fieldTypeImages = new HashMap[FieldLike, String]

  /**
   * Targets of type aliases cannot be processed top-down
   */
  val typedefImages = new HashMap[TypeAlias, String]

  /**
   * Calculate a field type.
   * @note types created during backtracking are necessarily part of a
   * successful application of this parser
   */
  private def fieldType : Parser[Type] = (
    (("set" | "list") ~! ("<" ~> fieldType <~ ">")) ^^ {
      case "set" ~ base ⇒ {
        self.makeSet(base)
      }
      case "list" ~ base ⇒ {
        self.makeList(base)
      }
    }
    | ("map" ~> ("<" ~> fieldType <~ ",") ~ (fieldType <~ ">")) ^^ {
      case l ~ r ⇒ {
        self.makeMap(l, r)
      }
    }
    | id ^^ { name ⇒
      definitions.getOrElse(name, self.makeNamedType(name)) match {
        case t : TypeAlias ⇒ ensureTypeAlias(t) // unpack type aliases
        case t             ⇒ t
      }
    }
  ) ~ rep("[" ~> "]") ^^ {
      case t ~ as ⇒ as.foldLeft[Type](t) { (l, r) ⇒ self.makeArray(l) }
    }

  private def parseType(image : String) : Type = parseAll(fieldType, image) match {
    case Success(r, _) ⇒ r
    case f             ⇒ self.reportError(s"parsing field type failed: $f");
  }

  private def ensureTypeAlias(t : TypeAlias) : Type = {
    if (null != t.getTarget) t.getTarget
    else {
      val r = parseType(typedefImages(t))
      t.setTarget(r)
      r
    }
  }

  private[skill] def postProcess {

    // follow type aliases
    typedefImages.keySet.foreach(ensureTypeAlias)

    // set types
    for ((f, i) ← fieldTypeImages) {
      f match {
        case f : Field ⇒ f.setType(parseType(i))
        case f : View  ⇒ f.setType(parseType(i))
      }
    }

    // create type hierarchy
    for (
      (t, supers) ← superTypes;
      sup ← supers.map(s ⇒ definitions.getOrElse(
        s,
        self.reportError(t.getPos, s"type ${t.getName.getOgss} has an undefined super type ${s.getOgss}")
      )).collect { case t : WithInheritance ⇒ t }
    ) {
      sup match {
        case sup : ClassDef if (null != t.getSuperType) ⇒ self.reportError(t.getPos, s"$t cannot have two super classes")
        case sup : ClassDef ⇒ {
          t.setSuperType(sup)
        }
        case sup : InterfaceDef ⇒ var r = t.getSuperInterfaces.add(sup)
      }
      sup.getSubTypes.add(t)
    }

    // create views
    for ((v, (st, sf)) ← superViews) {
      if (null == st) {
        // if there is no explicit super name, take any super field
        val target = allFields(v.getOwner).find(_.getName == sf).getOrElse(self.reportError(v.getPos, "Could not find super field to be viewed."))
        v.setTarget(target)
      } else {
        definitions(st) match {
          case t : WithInheritance ⇒
            val target = t.getFields.asScala.find(_.getName == sf).getOrElse(self.reportError(v.getPos, "Could not find super field to be viewed."))
            v.setTarget(target)
          case _ ⇒ ???
        }
      }
    }
  }
}