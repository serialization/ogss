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
package ogss.backend.skill

import ogss.oil.CustomField
import ogss.oil.Field
import ogss.oil.TypeContext
import ogss.oil.UserDefinedType
import ogss.oil.View
import ogss.oil.FieldLike

/**
 * Creates user type equivalents.
 *
 * @author Timm Felden
 */
trait SpecificationMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    makeFile(
      oil.TypeContext.find { tc ⇒ !tc.projectedTypeDefinitions && !tc.projectedInterfaces }.get,
      "specification.skill"
    )
    makeFile(
      oil.TypeContext.find { tc ⇒ tc.projectedTypeDefinitions && !tc.projectedInterfaces }.get,
      "noAlias.skill"
    )
    makeFile(
      oil.TypeContext.find { tc ⇒ !tc.projectedTypeDefinitions && tc.projectedInterfaces }.get,
      "noInterface.skill"
    )
    makeFile(
      oil.TypeContext.find { tc ⇒ tc.projectedTypeDefinitions && tc.projectedInterfaces }.get,
      "noSpecial.skill"
    )
  }

  private def makeFile(IR : TypeContext, name : String) {

    // write specification
    val out = files.open(name)

    out.write(IR.enums.map(t ⇒ s"""${comment(t)}${attributes(t)}enum ${capital(t.name)} {
  ${t.values.map(v ⇒ comment(v) + capital(v.name)).mkString("", ",\n  ", ";")}
}

""").mkString)

    out.write(IR.aliases.map(t ⇒ s"""${comment(t)}${attributes(t)}typedef ${capital(t.name)}
  ${mapType(t.target)};

""").mkString)

    out.write(IR.classes.map(t ⇒ s"""${prefix(t)}${attributes(t)}${capital(t.name)} ${
      if (null == t.superType) ""
      else s"extends ${capital(t.superType.name)} "
    }${
      t.superInterfaces.map(s ⇒ s"with ${capital(s.name)} ").mkString
    }{${mkFields(t.fields)}${mkViews(t.views)}${mkCustom(t.customs)}
}

""").mkString)

    out.write(IR.interfaces.map(t ⇒ s"""${prefix(t)}${attributes(t)}interface ${capital(t.name)} ${
      if (null == t.superType) ""
      else s"extends ${capital(t.superType.name)} "
    }${
      t.superInterfaces.map(s ⇒ s"with ${capital(s.name)} ").mkString
    }{${mkFields(t.fields)}${mkViews(t.views)}${mkCustom(t.customs)}
}

""").mkString)

    out.close()
  }

  private def mkFields(fs : Seq[Field]) : String = fs.map(f ⇒ s"""
  ${prefix(f)}${
    if (f.isTransient) "auto "
    else ""
  }${mapType(f.`type`)} ${camel(f.name)};""").mkString("\n")

  private def mkViews(fs : Seq[View]) : String = fs.map(f ⇒ s"""
  ${prefix(f)}view ${capital(f.target.owner.name)}.${camel(f.target.name)}
  as ${mapType(f.`type`)} ${camel(f.name)};""").mkString("\n")

  private def mkCustom(fs : Seq[CustomField]) : String = (for (f ← fs)
    yield s"""
  ${comment(f)}custom ${f.language}${
    (for (opt ← f.options)
      yield s"""
  !${opt.name} ${
      val vs = opt.arguments.map(s ⇒ s""""$s"""")
      if (vs.size == 1) vs.head
      else vs.mkString("(", " ", ")")
    }""").mkString
  }
  "${f.typename}" ${camel(f.name)};""").mkString("\n")

  private def prefix(t : UserDefinedType) : String = {
    var prefix = comment(t) // TODO + (t.getHints.asScala ++ t.getRestrictions.asScala).map(s ⇒ s"$s\n").mkString
    if (!prefix.isEmpty()) {
      prefix = "\n" + prefix
    }

    prefix
  }

  private def prefix(f : FieldLike) : String = {
    var prefix = comment(f) // TODO + (f.getHints.asScala ++ f.getRestrictions.asScala).map(s ⇒ s"$s\n  ").mkString
    if (!prefix.isEmpty()) {
      prefix = "\n  " + prefix
    }

    prefix
  }

  private def attributes(t : UserDefinedType) : String = t.attrs.map { a ⇒
    s"""${
      if (a.isSerialized) "@" else "!"
    }${a.name}${
      if (a.arguments.isEmpty) ""
      else if ("pragma".equals(a.name)) {
        if (a.arguments.size == 1) a.arguments.head
        else a.arguments.head + a.arguments.tail.mkString("(", ", ", ")")
      } else {
        a.arguments.mkString("(", ", ", ")")
      }
    }
"""
  }.mkString
}
