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
package ogss.frontend.sidl

import java.io.File

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import ogss.frontend.common.FrontEnd
import ogss.frontend.common.Positioned
import ogss.oil.Attribute
import ogss.oil.ClassDef
import ogss.oil.Comment
import ogss.oil.CustomFieldOption
import ogss.oil.EnumConstant
import ogss.oil.FieldLike
import ogss.oil.Identifier
import ogss.oil.InterfaceDef
import ogss.oil.UserDefinedType
import ogss.oil.WithInheritance

/**
 * Parse a file into definitions. A three-pass approach allows us to create
 * ir.Objects using the parser.
 */
class FileParser(self : FrontEnd) extends DefinitionPostProcessing(self) {

  /**
   * While parsing, this variable will set the current target of field-like
   * declarations.
   */
  var currentType : UserDefinedType = null

  protected var currentFile : File = _
  protected val seen = new HashSet[File]

  case class PositionedID(val image : Identifier) extends Positioned {
    override val declaredInFile : String = currentFile.getName
  }

  protected def file = opt(headComment) ~! rep(includes) ~! rep(content)

  /**
   * Files may start with an arbitrary of lines starting with '#'
   * Theses lines sereve as true comments and do not affect the specification.
   */
  protected def headComment = rep("""^#[^\r\n]*[\r\n]*""".r)

  /**
   * Includes are just strings containing relative paths to *our* path.
   */
  protected def includes = ("include" | "with") ~> rep(
    string ^^ { s ⇒ process(new File(currentFile.getParentFile, s).getAbsoluteFile) }
  );

  /**
   * A user type Definition
   */
  protected def content : Parser[Unit] = (opt(comment) ~ attributes ^^ { case c ~ as ⇒ (c.getOrElse(null), as) }) >> {
    case (c, as) ⇒ typedef(c, as) | enumType(c, as) | addedFields(c) | interfaceType(c, as) | classType(c, as)
  }

  /**
   * A declaration may start with a description, is followed by modifiers and a name, might have a super class and has
   * a body.
   */
  private def classType(c : Comment, as : ArrayBuffer[Attribute]) = (positioned(id ^^ PositionedID) ^^ {
    case name ⇒
      definitions.getOrElse(name.image, null) match {
        case null ⇒ {
          currentType = self.out.ClassDef
            .build
            .comment(c)
            .attrs(as)
            .name(name.image)
            .pos(self.makeSPos(name))
            .superInterfaces(new ArrayBuffer)
            .subTypes(new ArrayBuffer)
            .fields(new ArrayBuffer)
            .views(new ArrayBuffer)
            .customs(new ArrayBuffer)
            .make

          definitions(name.image) = currentType
        }

        case t : ClassDef ⇒ {
          mergeComment(t, c)
          currentType = t
        }

        case t ⇒ wrongKind(t, name)
      }
  }) ~> withInheritance

  /**
   * Interfaces are processed like ClassDefs
   */
  private def interfaceType(c : Comment, as : ArrayBuffer[Attribute]) = ("interface" ~! positioned(id ^^ PositionedID) ^^ {
    case keyword ~ name ⇒
      definitions.getOrElse(name.image, null) match {
        case null ⇒ {
          currentType = self.out.InterfaceDef
            .build
            .comment(c)
            .attrs(as)
            .name(name.image)
            .pos(self.makeSPos(name))
            .superInterfaces(new ArrayBuffer)
            .subTypes(new ArrayBuffer)
            .fields(new ArrayBuffer)
            .views(new ArrayBuffer)
            .customs(new ArrayBuffer)
            .make

          definitions(name.image) = currentType
        }

        case t : InterfaceDef ⇒ {
          mergeComment(t, c)
          currentType = t
        }

        case t ⇒ wrongKind(t, name)
      }
  }) ~> withInheritance

  private def withInheritance : Parser[Unit] = (
    ";" ^^ (_ ⇒ ()) | ("::=" ~!
      rep1sep(positioned(id ^^ PositionedID), "|") <~ opt(";")) ^^ {
        case _ ~ sub ⇒
          val target = currentType.asInstanceOf[WithInheritance]
          addSubtypes.getOrElseUpdate(target, new ArrayBuffer) ++= sub.map(_.image)
      }
  )

  /**
   * creates a type alias
   */
  private def typedef(c : Comment, as : ArrayBuffer[Attribute]) = "typedef" ~! positioned(id ^^ PositionedID) ~! (attributes ~> fieldTypeImage <~ ";") ^^ {
    case keyword ~ name ~ t ⇒
      ensureUnique(name)

      val pos = self.makeSPos(name)

      val r = self.out.TypeAlias
        .build
        .name(name.image)
        .pos(pos)
        .comment(c)
        .attrs(as)
        .make
      typedefImages(r) = t
  }

  /**
   * creates an enum definition
   */
  private def enumType(c : Comment, as : ArrayBuffer[Attribute]) = "enum" ~! positioned(id ^^ PositionedID) ~! ("::=" ~> rep1sep(
    opt(comment) ~ positioned(id ^^ PositionedID), "|"
  ) <~ opt(";")) ^^ {
      case keyword ~ name ~ i ⇒
        ensureUnique(name)

        val pos = self.makeSPos(name)

        val vs = new ArrayBuffer[EnumConstant]
        self.out.EnumDef
          .build
          .name(name.image)
          .pos(pos)
          .comment(c)
          .attrs(as)
          .values(vs)
          .make

        for (c ~ n ← i) {
          vs += self.out.EnumConstant
            .build
            .name(n.image)
            .pos(self.makeSPos(n))
            .comment(c.getOrElse(null))
            .make
        }
    }

  /**
   * Add fields to an interface or class def.
   */
  private def addedFields(c : Comment) : Parser[Unit] = id ~ (("->" | "⇒") ~> rep1sep(field, ",") <~ opt(";")) ^^ {
    case n ~ f ⇒
      addFields.getOrElseUpdate(n, new ArrayBuffer) ++= f

      if (null != c) {
        val ec = addComments.getOrElse(n, null)
        if (null == ec)
          addComments(n) = c
        else
          mergeComment(ec, c)
      }
  }

  /**
   * A field is either a constant or a real data field.
   */
  private def field : Parser[FieldLike] = (
    opt(comment) <~ attributes ^^ { c ⇒ c.getOrElse(null) }
  ) >> { c ⇒ view(c) | custom(c) | data(c) }

  protected def custom(c : Comment) : Parser[FieldLike] = ("custom" ~> id) ~ customFiledOptions ~ string ~! positioned(id ^^ PositionedID) ^^ {
    case lang ~ opts ~ t ~ n ⇒
      self.out.CustomField
        .build
        .comment(c)
        .language(lowercase(lang))
        .name(n.image)
        .pos(self.makeSPos(n))
        .typename(t)
        .options(opts)
        .make
  }
  protected def customFiledOptions : Parser[ArrayBuffer[CustomFieldOption]] = (
    rep("!" ~> id ~!
      (("(" ~> rep(string) <~ ")") | opt(string) ^^ { s ⇒ s.toList })) ^^ {
      s ⇒
        val r = new ArrayBuffer[CustomFieldOption]
        for (n ~ args ← s) {
          r += self.out.CustomFieldOption
            .build
            .name(lowercase(n))
            .arguments(args.to)
            .make
        }
        r
    }
  )

  /**
   * View an existing view as something else.
   */
  private def view(c : Comment) : Parser[FieldLike] = (
    positioned(id ^^ PositionedID) <~ ":"
  ) ~ fieldTypeImage ~ (
      "view" ~> opt(id <~ ".")
    ) ~ id ^^ {
        case newName ~ newType ~ targetType ~ targetField ⇒
          val f = self.out.View
            .build
            .comment(c)
            .name(newName.image)
            .pos(self.makeSPos(newName))
            .make

          fieldTypeImages(f) = newType
          superViews(f) = (targetType.getOrElse(null), targetField)
          f
      }

  /**
   * A data field may be marked to be auto and will therefore only be present at runtime.
   */
  private def data(c : Comment) : Parser[FieldLike] = (
    positioned(id ^^ PositionedID) <~ ":"
  ) ~ opt("auto" | "transient") ~ fieldTypeImage ^^ {
      case n ~ a ~ t ⇒
        val f = self.out.Field
          .build
          .comment(c)
          .isTransient(!a.isEmpty)
          .name(n.image)
          .pos(self.makeSPos(n))
          .make

        fieldTypeImages(f) = t
        f
    }

  /**
   * Parse a field type but return a canonical image
   */
  protected def fieldTypeImage : Parser[String] = (
    (("set" | "list") ~! ("<" ~> fieldTypeImage <~ ">")) ^^ {
      case kind ~ base ⇒ s"$kind<$base>"
    }
    | ("map" ~! ("<" ~> rep(fieldTypeImage <~ ",")) ~ (fieldTypeImage <~ ">")) ^^ {
      case kind ~ l ~ r ⇒ l.foldRight(r)((l, r) ⇒ s"map<$l,$r>")
    }
    // these choices have to happen in that order to get recursion right
    | "annotation" ^^ { _ ⇒ "anyRef" }
    | id ^^ { _.ogss }
  ) ~ rep("[" ~> "]") ^^ {
      case t ~ as ⇒ as.foldLeft(t)((l, r) ⇒ l + "[]")
    }

  def process(in : File) {
    // prevent duplicate processing
    if (seen.contains(in))
      return
    seen += in

    if (!in.exists() || !in.isFile()) {
      self.reportError(s"cannot import file $in")
    } else {

      currentFile = in;
      val lines = scala.io.Source.fromFile(in, "utf-8").getLines.mkString("\n")

      parseAll(file, lines) match {
        case Success(_, _) ⇒
        case f             ⇒ self.reportError(s"parsing failed in ${in.getName}: $f");
      }
    }
  }

  /**
   * ensure that a type name is unique
   */
  private def ensureUnique(name : PositionedID) {
    if (definitions.contains(name.image)) {
      val first = definitions(name.image)
      val dpos = self.makeSPos(name)
      self.reportError(s"""duplicate definition: ${name.image.ogss}
first:
${self.printPosition(first.pos)}

second:
${self.printPosition(dpos)}
${name.pos.longString}""")
    }
  }

  /**
   * issue an error because a type was defined with two different kinds
   */
  private def wrongKind(first : UserDefinedType, name : PositionedID) {
    val dpos = self.makeSPos(name)
    self.reportError(s"""definition has differing kinds: ${name.image.ogss}
first:
${self.printPosition(first.pos)}

second:
${self.printPosition(dpos)}
${name.pos.longString}""")
  }
}
