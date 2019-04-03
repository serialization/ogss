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

import java.util.ArrayList

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import ogss.frontend.common.FrontEnd
import ogss.oil.ClassDef
import ogss.oil.Identifier
import ogss.oil.InterfaceDef
import ogss.oil.UserDefinedType

/**
 * Turns definitions into the type hierarchy (Second pass).
 * At this point, definitions have unique names.
 */
class DefinitionParser(self : FrontEnd, definitions : HashMap[Identifier, Definition]) extends CommonParseRules(self) {

  private val todo = new HashSet[Identifier]()

  /**
   * Parse the super definition and create the respective oil object
   */
  protected def superImage(target : Definition) : Parser[Unit] = (
    "enum" ^^ { _ ⇒
      val r = self.out.EnumDefs.make
      target.IR = r
      r.setName(target.name)
      r.setComment(target.comment)
    }
    |

    "typedef" ^^ { _ ⇒
      target.IR = self.out.TypeAliass.make
      // follow definition
      ???
    }
    |

    opt("interface") ~ rep((":" | "with" | "extends") ~> id) ^^ {
      case kind ~ sups ⇒

        // process super types before this type to ensure that types get allocated in type order
        var superType : ClassDef = null;
        val si = new ArrayList[InterfaceDef]

        for (s ← sups) {
          val sup = definitions.getOrElse(s, self.reportError(s"""Type ${target.name} refers to undefined type ${s.getOgss}:
${target.file} ${target.pos}
${target.pos.longString}
"""))

          process(sup.name) match {
            case null ⇒ self.reportError(s"""Type ${target.name} has cyclic super types involving ${s.getOgss}:
${target.file} ${target.pos}
${target.pos.longString}""")

            case t : ClassDef if null != superType ⇒ self.reportError(s"""Type ${target.name} has two super classes: ${superType.getName.getOgss} and ${s.getOgss}:
${target.file} ${target.pos}
${target.pos.longString}""")

            case t : ClassDef     ⇒ superType = t
            case t : InterfaceDef ⇒ si.add(t)
          }
        }

        val r =
          if (kind.isEmpty) self.out.ClassDefs.make
          else self.out.InterfaceDefs.make

        target.IR = r
        r.setName(target.name)
        r.setComment(target.comment)
        r.setSuperType(superType)
        r.setSuperInterfaces(si)
        r.setSubTypes(new ArrayList)
    }
  )

  /**
   * Process definition with the given name.
   */
  private def process(name : Identifier) : UserDefinedType = {
    val d = definitions(name)

    // prevent duplicate processing
    if (!todo.contains(name))
      return d.IR
    todo -= name

    parseAll(superImage(d), d.superImage) match {
      case Success(_, _) ⇒
      case f             ⇒ self.reportError(s"parsing failed in definition ${d.name.getOgss}: $f");
    }

    return d.IR
  }

  /**
   * Process all definitions
   */
  def process {
    todo ++= definitions.keySet

    while (!todo.isEmpty)
      process(todo.head)
  }
}
