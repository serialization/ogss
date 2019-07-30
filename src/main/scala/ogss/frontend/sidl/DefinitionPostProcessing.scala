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

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import ogss.frontend.common.FrontEnd
import ogss.oil.ClassDef
import ogss.oil.Field
import ogss.oil.FieldLike
import ogss.oil.Identifier
import ogss.oil.InterfaceDef
import ogss.oil.Type
import ogss.oil.TypeAlias
import ogss.oil.UserDefinedType
import ogss.oil.View
import ogss.oil.WithInheritance
import scala.collection.mutable.ArrayBuffer
import ogss.oil.Comment
import ogss.oil.CustomField
import ogss.frontend.common.Positioned
import ogss.oil.SourcePosition

/**
 * Post processing transformations that have to performed after parsing all
 * files, because information flow can be across files.
 */
abstract class DefinitionPostProcessing(self : FrontEnd) extends CommonParseRules(self) {

  /**
   * Field definitions are gathered until all types are known, because we dont
   * know if the target entity is an interface.
   */
  protected val addFields = new HashMap[Identifier, ArrayBuffer[FieldLike]]

  /**
   * Comments on field definitions are gathered until all types are known, because we dont
   * know if the target entity is an interface.
   */
  protected val addComments = new HashMap[Identifier, Comment]
  /**
   * Subtypes are gathered until all types are known, because we cannot
   * introduce them if we do not know if they are interfaces.
   */
  protected val addSubtypes = new HashMap[WithInheritance, ArrayBuffer[Identifier]]

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
  private def fieldType(pos : SourcePosition) : Parser[Type] = (
    (("set" | "list") ~! ("<" ~> fieldType(pos) <~ ">")) ^^ {
      case "set" ~ base ⇒ {
        self.makeSet(base)
      }
      case "list" ~ base ⇒ {
        self.makeList(base)
      }
    }
    | ("map" ~> ("<" ~> fieldType(pos) <~ ",") ~ (fieldType(pos) <~ ">")) ^^ {
      case l ~ r ⇒ {
        self.makeMap(l, r)
      }
    }
    | id ^^ { name ⇒
      definitions.getOrElse(name, self.makeNamedType(name, pos)) match {
        case t : TypeAlias ⇒ ensureTypeAlias(t) // unpack type aliases
        case t             ⇒ t
      }
    }
  ) ~ rep("[" ~> "]") ^^ {
      case t ~ as ⇒ as.foldLeft[Type](t) { (l, r) ⇒ self.makeArray(l) }
    }

  private def parseType(pos : SourcePosition, image : String) : Type = parseAll(fieldType(pos), image) match {
    case Success(r, _) ⇒ r
    case f             ⇒ self.reportError(pos, s"parsing field type failed: $f");
  }

  private def ensureTypeAlias(t : TypeAlias) : Type = {
    if (null != t.target) t.target
    else {
      val r = parseType(t.pos, typedefImages(t))
      t.target = r
      r
    }
  }

  private[sidl] def postProcess {
    // add fields
    for ((n, fs) ← addFields) {
      val t = definitions.getOrElseUpdate(
        n,
        self.out.ClassDef
          .build
          .comment(null)
          .name(n)
          .pos(fs.head.pos)
          .superInterfaces(new ArrayBuffer)
          .subTypes(new ArrayBuffer)
          .fields(new ArrayBuffer)
          .views(new ArrayBuffer)
          .customs(new ArrayBuffer)
          .make
      ).asInstanceOf[WithInheritance]

      fs.foreach {
        case f : Field ⇒
          t.fields += f
          f.owner = t

        case f : View ⇒
          t.views += f
          f.owner = t

        case f : CustomField ⇒
          t.customs += f
          f.owner = t
      }
    }

    // add subtypes
    for ((sup, subs) ← addSubtypes; sub ← subs) {
      val s = definitions.getOrElseUpdate(
        sub,
        self.out.ClassDef
          .build
          .comment(null)
          .name(sub)
          .pos(sup.pos)
          .superInterfaces(new ArrayBuffer)
          .subTypes(new ArrayBuffer)
          .fields(new ArrayBuffer)
          .views(new ArrayBuffer)
          .customs(new ArrayBuffer)
          .make
      ).asInstanceOf[WithInheritance]

      superTypes.getOrElseUpdate(s, new HashSet) += sup.name
    }

    // add comments
    for ((n, c) ← addComments) {
      mergeComment(definitions(n), c)
    }

    // follow type aliases
    typedefImages.keySet.foreach(ensureTypeAlias)

    // set types
    for ((f, i) ← fieldTypeImages) {
      f match {
        case f : Field ⇒ f.`type` = parseType(f.pos, i)
        case f : View  ⇒ f.`type` = parseType(f.pos, i)
      }
    }

    // create type hierarchy
    for (
      (t, supers) ← superTypes;
      sup ← supers.map(s ⇒ definitions.getOrElse(
        s,
        self.reportError(t.pos, s"type ${t.name.ogss} has an undefined super type ${s.ogss}")
      )).collect { case t : WithInheritance ⇒ t }
    ) {
      sup match {
        case sup : ClassDef if (null != t.superType) ⇒ self.reportError(t.pos, s"$t cannot have two super classes")
        case sup : ClassDef                          ⇒ t.superType = sup
        case sup : InterfaceDef                      ⇒ t.superInterfaces += sup
      }
      sup.subTypes += t
    }

    // create views
    for ((v, (st, sf)) ← superViews) {
      if (null == st) {
        // if there is no explicit super name, take any super field
        val target = allFields(v.owner).find(_.name == sf).getOrElse(self.reportError(v.pos, "Could not find super field to be viewed."))
        v.target = target
      } else {
        definitions(st) match {
          case t : WithInheritance ⇒
            val target = t.fields.find(_.name == sf).getOrElse(self.reportError(v.pos, "Could not find super field to be viewed."))
            v.target = target
          case _ ⇒ ???
        }
      }
    }
  }

  /**
   * merge a comment into a user defined type
   */
  protected def mergeComment(t : UserDefinedType, c : Comment) {
    if (null == t.comment) {
      t.comment = c
    } else {
      mergeComment(t.comment, c)
    }
  }

  /**
   * merge a comment into another comment
   */
  protected def mergeComment(to : Comment, c : Comment) {
    to.text ++= c.text
    to.tags ++= c.tags

    self.out.delete(c)
  }
}
