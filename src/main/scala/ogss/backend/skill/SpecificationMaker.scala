/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.skill

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

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
      oil.TypeContexts.asScala.find { tc ⇒ !tc.getProjectedTypeDefinitions && !tc.getProjectedInterfaces }.get,
      "specification.skill"
    )
    makeFile(
      oil.TypeContexts.asScala.find { tc ⇒ tc.getProjectedTypeDefinitions && !tc.getProjectedInterfaces }.get,
      "noAlias.skill"
    )
    makeFile(
      oil.TypeContexts.asScala.find { tc ⇒ !tc.getProjectedTypeDefinitions && tc.getProjectedInterfaces }.get,
      "noInterface.skill"
    )
    makeFile(
      oil.TypeContexts.asScala.find { tc ⇒ tc.getProjectedTypeDefinitions && tc.getProjectedInterfaces }.get,
      "noSpecial.skill"
    )
  }

  private def makeFile(IR : TypeContext, name : String) {

    // write specification
    val out = files.open(name)

    out.write(IR.getEnums.asScala.map(t ⇒ s"""${comment(t)}enum ${capital(t.getName)} {
  ${t.getValues.asScala.map(v ⇒ capital(v.getName)).mkString("", ",\n  ", ";")}
}

""").mkString)

    out.write(IR.getAliases.asScala.map(t ⇒ s"""${comment(t)}typedef ${capital(t.getName)}
  ${mapType(t.getTarget)};

""").mkString)

    out.write(IR.getClasses.asScala.map(t ⇒ s"""${prefix(t)}${capital(t.getName)} ${
      if (null == t.getSuperType) ""
      else s"extends ${capital(t.getSuperType.getName)} "
    }${
      t.getSuperInterfaces.asScala.map(s ⇒ s"with ${capital(s.getName)} ").mkString
    }{${mkFields(t.getFields.asScala.to)}${mkCustom(t.getCustoms.asScala.to)}
}

""").mkString)

    out.write(IR.getInterfaces.asScala.map(t ⇒ s"""${prefix(t)}interface ${capital(t.getName)} ${
      if (null == t.getSuperType) ""
      else s"extends ${capital(t.getSuperType.getName)} "
    }${
      t.getSuperInterfaces.asScala.map(s ⇒ s"with ${capital(s.getName)} ").mkString
    }{${mkFields(t.getFields.asScala.to)}${mkCustom(t.getCustoms.asScala.to)}
}

""").mkString)

    out.close()
  }

  private def mkFields(fs : Array[Field]) : String = fs.map(f ⇒ s"""
  ${prefix(f)}${
    if (f.getIsTransient) "auto "
    else ""
  }${mapType(f.getType)} ${camel(f.getName)};""").mkString("\n")

  private def mkCustom(fs : List[CustomField]) : String = (for (f ← fs)
    yield s"""
  ${comment(f)}custom ${f.getLanguage}${
    (for (opt ← f.getOptions.asScala)
      yield s"""
  !${opt.getName} ${
      val vs = opt.getArguments.asScala.map(s ⇒ s""""$s"""")
      if (vs.size == 1) vs.head
      else vs.mkString("(", " ", ")")
    }""").mkString
  }
  "${f.getTypename}" ${camel(f.getName)};""").mkString("\n")

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
