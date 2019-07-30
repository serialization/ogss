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
package ogss.backend.sidl

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
      "specification.sidl"
    )
    makeFile(
      oil.TypeContext.find { tc ⇒ tc.projectedTypeDefinitions && !tc.projectedInterfaces }.get,
      "noAlias.sidl"
    )
    makeFile(
      oil.TypeContext.find { tc ⇒ !tc.projectedTypeDefinitions && tc.projectedInterfaces }.get,
      "noInterface.sidl"
    )
    makeFile(
      oil.TypeContext.find { tc ⇒ tc.projectedTypeDefinitions && tc.projectedInterfaces }.get,
      "noSpecial.sidl"
    )
  }

  private def makeFile(IR : TypeContext, path : String) {

    // write specification
    val out = files.open(path)

    out.write(IR.enums.map(t ⇒ s"""${comment(t)}enum ${capital(t.name)} ::= ${
      t.values.map(v ⇒ capital(v.name)).mkString("", "\n  | ", "")
    }

""").mkString)

    out.write(IR.aliases.map(t ⇒ s"""${comment(t)}typedef ${capital(t.name)}
  ${mapType(t.target)};

""").mkString)

    out.write(IR.classes.map(t ⇒ s"""${prefix(t)}${capital(t.name)}${
      if (t.subTypes.isEmpty) ";"
      else t.subTypes.map(name).toArray.sorted.mkString(" ::= ", " | ", "")
    }${
      val fs = mkFields(t.fields) ++ mkViews(t.views) ++ mkCustom(t.customs)
      if (fs.isEmpty) ""
      else fs.mkString(s"\n${capital(t.name)} -> ", ",", "")
    }

""").mkString)

    out.write(IR.interfaces.map(t ⇒ s"""${prefix(t)}interface ${capital(t.name)}${
      if (t.subTypes.isEmpty) ";"
      else t.subTypes.map(name).toArray.sorted.mkString(" ::= ", " | ", "")
    }${
      val fs = mkFields(t.fields) ++ mkViews(t.views) ++ mkCustom(t.customs)
      if (fs.isEmpty) ""
      else fs.mkString(s"\n${capital(t.name)} -> ", ",", "")
    }

""").mkString)

    out.close()
  }

  private def mkFields(fs : Seq[Field]) : Seq[String] = fs.map(f ⇒ s"""
  ${prefix(f)}${camel(f.name)} : ${
    if (f.isTransient) "auto "
    else ""
  }${mapType(f.`type`)}""")

  private def mkViews(fs : Seq[View]) : Seq[String] = fs.map(f ⇒ s"""
  ${prefix(f)}${camel(f.name)} : ${mapType(f.`type`)} view ${name(f.target.owner)}.${name(f.target)}""")

  private def mkCustom(fs : Seq[CustomField]) : Seq[String] = for (f ← fs)
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
  "${f.typename}" ${camel(f.name)}"""

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
}
