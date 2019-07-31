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

import scala.collection.mutable.HashSet

import ogss.oil.EnumDef
import ogss.oil.MapType
import ogss.oil.OGFile
import ogss.oil.SeqType
import ogss.oil.SetType
import ogss.oil.TypeContext
import scala.collection.mutable.HashMap
import ogss.oil.Field
import ogss.oil.View
import ogss.oil.WithInheritance
import ogss.oil.BuiltinType

/**
 * This object provides some functions to check the well-formedness of
 * OIL-entities.
 *
 * @author Timm Felden
 */
object IRChecks {

  /**
   * Check a whole file by performing checks on its contents.
   */
  def check(sg : OGFile) {

    // check that there are exactly 10 builtins
    expect(sg.BuiltinType.size == 10, s"Wrong number of built-in types. Should be 10 but is ${sg.BuiltinType.size}")

    // check that all builtins have unique names and IDs
    for (t ← sg.BuiltinType) {
      val names = sg.BuiltinType.map(_.name).toSet
      val ids = sg.BuiltinType.map(_.stid).toSet

      expect(names.size == 10, s"Built-in types have duplicate names")
      expect(ids.size == 10, s"Built-in types have duplicate ids")
    }

    // check that all types have non-zero stids or are bool
    for (t ← sg.Type) {
      if (0 == t.stid) {
        if (!"Bool".equals(t.name.ogss) || !t.isInstanceOf[BuiltinType]) {
          println(s"Type ${t.name.ogss} has no valid STID!")
          //          expect(false, s"Type ${t.name.ogss} has no valid STID!")
        }
      }
    }

    // check each type context
    sg.TypeContext.foreach(check)
  }

  /**
   * Check consistency of a type context. Throws an exception, if the check
   * fails.
   */
  def check(tc : TypeContext) {

    // check that alias targets exist
    for (t ← tc.aliases) {
      if (null == t.target)
        throw new IllegalStateException(s"Type alias ${t.name.ogss} has no target")
    }

    // check that enums have at least one instance
    for (t ← tc.enums) {
      if (t.values.isEmpty)
        throw new IllegalStateException(s"Enum ${t.name.ogss} has no instances")
    }

    // check that containers are UCC-ordered
    tc.containers.scanLeft(-1L) {
      case (last, c) ⇒
        val ucc = IRUtils.ucc(c.kcc)
        if (last < ucc)
          ucc
        else
          throw new IllegalStateException(f"Container ${c.name.ogss} has UCC $ucc%8x, but last container had $last%8x")
    }

    // check that all types have STIDs and that they are continuous
    var nextSTID = 10
    for (t ← tc.classes) {
      expect(nextSTID == t.stid, s"type ${t.name.ogss} has an invalid STID: ${t.stid}, should be $nextSTID")
      nextSTID += 1
    }
    for (t ← tc.containers) {
      expect(nextSTID == t.stid, s"type ${t.name.ogss} has an invalid STID: ${t.stid}, should be $nextSTID")
      nextSTID += 1
    }
    for (t ← tc.enums) {
      expect(nextSTID == t.stid, s"type ${t.name.ogss} has an invalid STID: ${t.stid}, should be $nextSTID")
      nextSTID += 1
    }

    // check that all super types have lower STIDS
    for (t ← tc.classes; sup = t.superType if null != sup) {
      expect(sup.stid < t.stid, s"class ${t.name.ogss} has an STID lower than that of its super class: ${t.stid}, super: ${sup.stid}")
    }
    for (t ← tc.interfaces; sup = t.superType if null != sup) {
      for (superInterface ← t.superInterfaces; ssi = superInterface.superType if null != ssi)
        expect(ssi.stid <= sup.stid, s"interface ${t.name.ogss} has a super type with an STID lower than that of its super interfaces (${superInterface.name.ogss}) super class(${ssi.name.ogss}): ${sup.stid}, super: ${ssi.stid}")
    }
    // check that all super types without supertypes have only super interfaces without supertypes
    for (
      t ← tc.classes ++ tc.interfaces if null == t.superType;
      superInterface ← t.superInterfaces; ssi = superInterface.superType if null != ssi
    ) {
      expect(false, s"type ${t.name.ogss} has no super class but its super interface ${superInterface.name.ogss} has a super class: ${ssi.name.ogss}")
    }

    // check that all user type names are unique
    {
      val seen = new HashSet[String]
      for (t ← tc.classes ++ tc.interfaces ++ tc.containers ++ tc.enums) {
        expect(!seen(t.name.ogss), s"type ${t.name.ogss} has the same name as another type")
        seen += t.name.ogss
      }
    }

    // check that fields are unique per type and name
    {
      for (t ← tc.classes ++ tc.interfaces) {
        val seen = new HashSet[String]
        for (c ← IRUtils.allFields(t)) {
          expect(!seen(c.name.ogss), s"type ${t.name.ogss} defines field ${c.name.ogss} twice.")
          seen += c.name.ogss
        }
      }
    }

    // check that views are unique per type and name and do not collide with fields
    {
      for (t ← tc.classes ++ tc.interfaces) {
        val seen = new HashSet[String]
        seen ++= t.fields.map(_.name.ogss)

        for (c ← t.views) {
          expect(!seen(c.name.ogss), s"view ${c.name.ogss} in type ${t.name.ogss} collides with another member.")
          seen += c.name.ogss
        }
      }
    }

    // check that custom fields are unique per type, name and language
    {
      for (t ← tc.classes ++ tc.interfaces) {
        // lang -> names
        val seen = new HashMap[String, HashSet[String]]
        for (c ← IRUtils.allCustoms(t)) {
          val ns = seen.getOrElseUpdate(c.language, new HashSet)
          expect(!ns(c.name.ogss), s"type ${t.name.ogss} defines custom field ${c.name.ogss} twice for language ${c.language}.")
          ns += c.name.ogss
        }
      }
    }

    // check that views reside in super types and have assignable retyping
    for (t ← tc.classes ++ tc.interfaces; v ← t.views) {
      expect(
        IRUtils.allSuperTypes(t).contains(v.target.owner) || t == v.target.owner,
        s"view ${v.name.ogss} in type ${t.name.ogss} targets a field in unrelated type ${v.target.owner.name.ogss}."
      )

      expect(
        (tc.classes ++ tc.interfaces).contains(v.target.owner),
        s"view ${v.name.ogss} in type ${t.name.ogss} targets a field in unrelated type context."
      )

      val ft = v.`type`
      val tt = (v.target match {
        case f : Field ⇒ f.`type`
        case f : View  ⇒ f.`type`
      })

      expect(
        ft == tt || ((ft, tt) match {
          case (ft : WithInheritance, tt : WithInheritance) ⇒
            if (IRUtils.allSuperTypes(ft).contains(tt)) true
            else {
              println("view could retype : " + IRUtils.allSuperTypes(ft).map(_.name.ogss).mkString(", "))
              println("view retypes : " + tt.name.ogss)
              false
            }
          case (ft : WithInheritance, tt : BuiltinType) if tt.name.ogss.equals("AnyRef") ⇒ true
          case _ ⇒ false
        }),
        s"view ${v.name.ogss} in type ${t.name.ogss} of type ${ft.name.ogss} cannot retype ${tt.name.ogss}."
      )
    }

    // check that used field types exist in this type context
    for (t ← tc.classes ++ tc.interfaces) {
      val allTypes = tc.byName.values.toSet
      for (f ← t.fields) {
        expect(
          allTypes(f.`type`),
          s"field ${f.name.ogss} in type ${t.name.ogss} uses type ${f.`type`.name.ogss} from an unrelated type context."
        )
      }
      for (f ← t.views) {
        expect(
          allTypes(f.`type`),
          s"view ${f.name.ogss} in type ${t.name.ogss} uses type ${f.`type`.name.ogss} from an unrelated type context."
        )
      }
    }

    // check that containers do not have bool or enum as base type
    for (t ← tc.containers) {
      t match {
        case t : SeqType ⇒ {
          expect(0 != t.baseType.stid, s"type ${t.name.ogss} has bool as base type")
          expect(!t.baseType.isInstanceOf[EnumDef], s"type ${t.name.ogss} has an enum as base type")
        }
        case t : MapType ⇒ {
          expect(0 != t.keyType.stid, s"type ${t.name.ogss} has bool as key type")
          expect(!t.keyType.isInstanceOf[EnumDef], s"type ${t.name.ogss} has an enum as key type")

          expect(0 != t.valueType.stid, s"type ${t.name.ogss} has bool as value type")
          expect(!t.valueType.isInstanceOf[EnumDef], s"type ${t.name.ogss} has an enum as value type")
        }
      }
    }

    // check that sets or map keys are not containers
    for (t ← tc.containers) {
      t match {
        case t : SetType ⇒ {
          expect(!t.baseType.isInstanceOf[SeqType]
            && !t.baseType.isInstanceOf[MapType], s"set ${t.name.ogss} has a container as base type")
        }
        case t : MapType ⇒ {
          expect(!t.keyType.isInstanceOf[SeqType]
            && !t.keyType.isInstanceOf[MapType], s"set ${t.name.ogss} has a container as key type")
        }
        case _ ⇒ // ok
      }
    }
  }

  private def expect(predicate : Boolean, msg : String) = if (!predicate) throw new IllegalStateException(msg)
}
