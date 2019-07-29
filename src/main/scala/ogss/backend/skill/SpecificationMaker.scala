/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.skill

import ogss.oil.CustomField
import ogss.oil.Field
import ogss.oil.TypeContext
import ogss.oil.UserDefinedType

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

    out.write(IR.enums.map(t ⇒ s"""${comment(t)}enum ${capital(t.name)} {
  ${t.values.map(v ⇒ capital(v.name)).mkString("", ",\n  ", ";")}
}

""").mkString)

    out.write(IR.aliases.map(t ⇒ s"""${comment(t)}typedef ${capital(t.name)}
  ${mapType(t.target)};

""").mkString)

    out.write(IR.classes.map(t ⇒ s"""${prefix(t)}${capital(t.name)} ${
      if (null == t.superType) ""
      else s"extends ${capital(t.superType.name)} "
    }${
      t.superInterfaces.map(s ⇒ s"with ${capital(s.name)} ").mkString
    }{${mkFields(t.fields.to)}${mkCustom(t.customs.to)}
}

""").mkString)

    out.write(IR.interfaces.map(t ⇒ s"""${prefix(t)}interface ${capital(t.name)} ${
      if (null == t.superType) ""
      else s"extends ${capital(t.superType.name)} "
    }${
      t.superInterfaces.map(s ⇒ s"with ${capital(s.name)} ").mkString
    }{${mkFields(t.fields.to)}${mkCustom(t.customs.to)}
}

""").mkString)

    out.close()
  }

  private def mkFields(fs : Array[Field]) : String = fs.map(f ⇒ s"""
  ${prefix(f)}${
    if (f.isTransient) "auto "
    else ""
  }${mapType(f.`type`)} ${camel(f.name)};""").mkString("\n")

  private def mkCustom(fs : List[CustomField]) : String = (for (f ← fs)
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

  private def prefix(f : Field) : String = {
    var prefix = comment(f) // TODO + (f.getHints.asScala ++ f.getRestrictions.asScala).map(s ⇒ s"$s\n  ").mkString
    if (!prefix.isEmpty()) {
      prefix = "\n  " + prefix
    }

    prefix
  }
}
