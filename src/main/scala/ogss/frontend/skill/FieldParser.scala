package ogss.frontend.skill

import java.util.ArrayList

import scala.collection.mutable.HashMap

import ogss.frontend.common.FrontEnd
import ogss.oil.Comment
import ogss.oil.CustomFieldOption
import ogss.oil.EnumConstant
import ogss.oil.EnumDef
import ogss.oil.Identifier
import ogss.oil.Type
import ogss.oil.WithInheritance

/**
 * Turns field definitions into IR (Third pass).
 * At this point, definitions have unique names and form an
 * acyclic type hierarchy.
 */
class FieldParser(self : FrontEnd, definitions : HashMap[Identifier, Definition]) extends CommonParseRules(self) {

  /**
   * Parse the super definition and create the respective oil object
   */
  protected def body(target : WithInheritance) : Parser[_] = (";" | rep(field(target)) <~ "}")

  protected def field(target : WithInheritance) : Parser[Unit] = (
    opt(comment) <~ attributes ^^ { c ⇒ c.getOrElse(null) }
  ) >> { c ⇒ view(target, c) | custom(target, c) | data(target, c) } <~ ";" ^^ {
      f ⇒ f.setOwner(target)
    }

  /**
   * A field with language custom properties. This field will almost behave like an auto field.
   */
  protected def custom(target : WithInheritance, c : Comment) = ("custom" ~> id) ~ customFiledOptions ~ string ~! id ^^ {
    case lang ~ opts ~ t ~ n ⇒
      var fs = target.getCustoms
      if (null == fs) {
        fs = new ArrayList
        target.setCustoms(fs)
      }
      val f = self.out.CustomFields.make()
      f.setComment(c)
      f.setLanguage(lowercase(lang))
      f.setName(n)
      f.setTypename(t)
      f.setOptions(opts)
      fs.add(f)
      f
  }
  protected def customFiledOptions : Parser[ArrayList[CustomFieldOption]] = (
    rep("!" ~> id ~!
      (("(" ~> rep(string) <~ ")") | opt(string) ^^ { s ⇒ s.toList })) ^^ {
      s ⇒
        val r = new ArrayList[CustomFieldOption]
        for (n ~ args ← s) {
          val opt = self.out.CustomFieldOptions.make()
          opt.setName(lowercase(n))
          val jargs = new ArrayList[String]
          args.foreach(jargs.add)
          opt.setArguments(jargs)
        }
        r
    }
  )

  /**
   * View an existing view as something else.
   */
  private def view(target : WithInheritance, c : Comment) = ("view" ~> opt(id <~ ".")) ~ (id <~ "as") ~ fieldType ~! id ^^ {
    case targetType ~ targetField ~ newType ~ newName ⇒
      var fs = target.getViews
      if (null == fs) {
        fs = new ArrayList
        target.setViews(fs)
      }
      val f = self.out.Views.make()
      f.setComment(c)
      f.setType(newType)
      f.setName(newName)
      fs.add(f)
      f
  }

  /**
   * A data field may be marked to be auto and will therefore only be present at runtime.
   */
  private def data(target : WithInheritance, c : Comment) = opt("auto" | "transient") ~ fieldType ~! id ^^ {
    case a ~ t ~ n ⇒
      var fs = target.getFields
      if (null == fs) {
        fs = new ArrayList
        target.setFields(fs)
      }
      val f = self.out.Fields.make()
      f.setComment(c)
      f.setIsTransient(!a.isEmpty)
      f.setType(t)
      f.setName(n)
      fs.add(f)
      f
  }

  /**
   * Calculate a field type.
   * @note types created during backtracking are necessarily part of a
   * successful application of this parser
   */
  protected def fieldType : Parser[Type] = (
    (("map" | "set" | "list") ~! ("<" ~> repsep(fieldType, ",") <~ ">")) ^^ {
      case "map" ~ l ⇒ {
        if (1 >= l.size)
          self.reportError(s"Did you mean set<${l.mkString}> instead of map?")
        else
          l.reduceRight { (l, r) ⇒ self.makeMap(l, r) }
      }
      case "set" ~ l ⇒ {
        if (1 != l.size)
          self.reportError(s"Did you mean map<${l.mkString}> instead of set?")
        else
          self.makeSet(l.head)
      }
      case "list" ~ l ⇒ {
        if (1 != l.size)
          self.reportError(s"Did you mean map<${l.mkString}> instead of list?")
        else
          self.makeList(l.head)
      }
    }
    // these choices have to happen in that order to get recursion right
    | "annotation" ^^ { _ ⇒ self.makeNamedType(self.toIdentifier("AnyRef")) }
    | id ^^ { self.makeNamedType(_) }
  ) ~ rep("[" ~> "]") ^^ {
      case t ~ as ⇒
        var r = t
        for (0 ← 0 until as.size) {
          r = self.makeArray(r)
        }
        r
    }

  protected def body(target : EnumDef) : Parser[Unit] = (
    repsep(id, ",") <~ ";" <~ "}"
  ) ^^ {
      case names ⇒
        if (names.isEmpty) {
          val d = definitions(target.getName)
          self.reportError(s"""Enum ${d.name} has no instance:
${d.file} ${d.pos}
${d.pos.longString}
""")
        }

        val is = new ArrayList[EnumConstant]
        for (n ← names) {
          val c = self.out.EnumConstants.make()
          c.setName(n)
          is.add(c)
        }
        target.setValues(is)
    }

  /**
   * Process all definitions
   */
  def process {
    for (d ← definitions.values) d.IR match {
      case null ⇒ self.reportError(s"Internal error: Definition ${d.name.getOgss} has no IR")

      case ir : WithInheritance ⇒
        parseAll(body(ir), d.bodyImage) match {
          case Success(_, _) ⇒
          case f             ⇒ self.reportError(s"parsing failed in definition ${d.name.getOgss}: $f");
        }

      case ir : EnumDef ⇒
        parseAll(body(ir), d.bodyImage) match {
          case Success(_, _) ⇒
          case f             ⇒ self.reportError(s"parsing failed in definition ${d.name.getOgss}: $f");
        }
    }
  }
}