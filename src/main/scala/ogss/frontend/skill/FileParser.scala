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
package ogss.frontend.skill

import java.io.File

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import ogss.frontend.common.FrontEnd
import ogss.frontend.common.Positioned
import ogss.oil.Attribute
import ogss.oil.Comment
import ogss.oil.CustomFieldOption
import ogss.oil.EnumConstant
import ogss.oil.Identifier
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
    case (c, as) ⇒ typedef(c, as) | enumType(c, as) | interfaceType(c, as) | classType(c, as)
  }

  /**
   * A declaration may start with a description, is followed by modifiers and a name, might have a super class and has
   * a body.
   */
  private def classType(c : Comment, as : ArrayBuffer[Attribute]) = (positioned(id ^^ PositionedID) ^^ {
    case name ⇒
      ensureUnique(name)

      currentType = self.out.ClassDef
        .build
        .comment(c)
        .attrs(as)
        .name(name.image)
        .pos(self.makeSPos(name))
        .fields(new ArrayBuffer)
        .views(new ArrayBuffer)
        .customs(new ArrayBuffer)
        .make

      definitions(name.image) = currentType
  }) ~> withInheritance

  /**
   * Interfaces are processed like ClassDefs
   */
  private def interfaceType(c : Comment, as : ArrayBuffer[Attribute]) =
    ("interface" ~! positioned(id ^^ PositionedID) ^^ {
      case keyword ~ name ⇒
        ensureUnique(name)

        currentType = self.out.InterfaceDef
          .build
          .comment(c)
          .attrs(as)
          .name(name.image)
          .pos(self.makeSPos(name))
          .fields(new ArrayBuffer)
          .views(new ArrayBuffer)
          .customs(new ArrayBuffer)
          .make

        definitions(name.image) = currentType
    }) ~> withInheritance

  /**
   * creates a type alias
   */
  private def typedef(c : Comment, as : ArrayBuffer[Attribute]) =
    "typedef" ~! positioned(id ^^ PositionedID) ~! (attributes ~> fieldTypeImage <~ ";") ^^ {
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
  private def enumType(c : Comment, as : ArrayBuffer[Attribute]) =
    "enum" ~! positioned(id ^^ PositionedID) ~! ("{" ~> repsep(
      opt(comment) ~ positioned(id ^^ PositionedID), ","
    ) <~ ";") ~ (rep(field) <~ "}") ^^ {
        case keyword ~ name ~ i ~ f ⇒
          ensureUnique(name)

          val pos = self.makeSPos(name)
          if (i.isEmpty)
            self.reportError(s"""${self.printPosition(pos)}
Enum ${name.image.ogss} requires a non-empty list of instances!""")

          if (!f.isEmpty)
            self.reportWarning(s"""${self.printPosition(pos)}
Enum ${name.image.ogss} has fields. Enum fields are not supported in OGSS.""")

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

  private def withInheritance : Parser[Unit] = rep((":" | "with" | "extends") ~> id) ~! definitionBody ^^ {
    case sup ~ body ⇒
      val target = currentType.asInstanceOf[WithInheritance]
      target.superInterfaces = new ArrayBuffer
      target.subTypes = new ArrayBuffer

      val ss = superTypes.getOrElseUpdate(target, new HashSet)
      for (s ← sup) {
        ss += s
      }
      currentType = null
  }

  private def definitionBody : Parser[Unit] = (";" | ("{" ~> rep(field) <~ ("}" | ("\\z".r ^^ {
    self.reportError("unexpected EOF while parsing " + currentType.name.ogss)
  })))) ^^ { _ ⇒ () }

  /**
   * A field is either a constant or a real data field.
   */
  private def field : Parser[Unit] = (
    (opt(comment) ~ attributes ^^ { case c ~ as ⇒ (c.getOrElse(null), as) }) >> {
      case (c, as) ⇒ view(c) | custom(c) | constant | data(c, as)
    } <~ ";"
    | """[^\}]+""".r ^^ {
      case image ⇒ self.reportError("parse error in field declaration:\n" + image)
    }
  )

  protected def custom(c : Comment) : Parser[Unit] = ("custom" ~> id) ~ customFiledOptions ~ string ~! positioned(id ^^ PositionedID) ^^ {
    case lang ~ opts ~ t ~ n ⇒
      val target = currentType.asInstanceOf[WithInheritance]
      // ensure field parsing works even in fallback mode
      if (null != target) {
        target.customs += self.out.CustomField
          .build
          .comment(c)
          .language(lowercase(lang))
          .name(n.image)
          .pos(self.makeSPos(n))
          .typename(t)
          .options(opts)
          .owner(target)
          .make
      }
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
  private def view(c : Comment) = ("view" ~> opt(id <~ ".")) ~ (id <~ "as") ~ fieldTypeImage ~! positioned(id ^^ PositionedID) ^^ {
    case targetType ~ targetField ~ newType ~ newName ⇒
      val target = currentType.asInstanceOf[WithInheritance]

      // ensure field parsing works even in fallback mode
      if (null != target) {
        val f = self.out.View
          .build
          .comment(c)
          .name(newName.image)
          .pos(self.makeSPos(newName))
          .owner(target)
          .make

        target.views += f
        fieldTypeImages(f) = newType
        superViews(f) = (targetType.getOrElse(null), targetField)
      }
  }

  /**
   * Constants a recognized by the keyword "const" and are required to have a value.
   */
  private def constant = "const" ~> fieldTypeImage ~! positioned(id ^^ PositionedID) ~! ("=" ~> int) ^^ {
    case t ~ n ~ v ⇒ self.reportWarning(s"ignored constant ${n.image.ogss} at ${self.printPosition(self.makeSPos(n))}")
  }

  /**
   * A data field may be marked to be auto and will therefore only be present at runtime.
   */
  private def data(c : Comment, as : ArrayBuffer[Attribute]) =
    opt("auto" | "transient") ~ fieldTypeImage ~! positioned(id ^^ PositionedID) ^^ {
      case a ~ t ~ n ⇒
        val target = currentType.asInstanceOf[WithInheritance]

        // ensure field parsing works even in fallback mode
        if (null != target) {
          val f = self.out.Field
            .build
            .comment(c)
            .attrs(as)
            .isTransient(!a.isEmpty)
            .name(n.image)
            .pos(self.makeSPos(n))
            .owner(target)
            .make

          target.fields += f
          fieldTypeImages(f) = t
        }
    }

  /**
   * Parse a field type but return a canonical image
   */
  protected def fieldTypeImage : Parser[String] = (
    (("set" | "list") ~! ("<" ~> fieldTypeImage <~ ">")) ^^ {
      case kind ~ base ⇒ s"$kind<$base>"
    }
    | ("map" ~! ("<" ~> rep1(fieldTypeImage <~ ",")) ~ (fieldTypeImage <~ ">")) ^^ {
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
}
