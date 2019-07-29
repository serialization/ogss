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
