package ogss.backend.scala

import scala.collection.mutable.HashMap

import ogss.oil.ArrayType
import ogss.oil.BuiltinType
import ogss.oil.EnumDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.WithInheritance
import ogss.util.HeaderInfo

/**
 * @author Timm Felden
 */
final class Main extends AbstractBackEnd
  with EnumMaker
  with InternalMaker
  with OGFileMaker
  with TypesMaker {

  override def name : String = "Scala"
  override def description = "Scala 2.12 source code"

  dependencies = Seq("ogss.common.jvm.jar", "ogss.common.scala.jar")

  /**
   * Translates types into scala type names.
   */
  override def mapType(t : Type) : String = t match {
    case t : BuiltinType ⇒ t.name.ogss match {
      case "AnyRef" ⇒ "ogss.common.scala.internal.Obj"

      case "Bool"   ⇒ "scala.Boolean"

      case "I8"     ⇒ "scala.Byte"
      case "I16"    ⇒ "scala.Short"
      case "I32"    ⇒ "scala.Int"
      case "I64"    ⇒ "scala.Long"
      case "V64"    ⇒ "scala.Long"

      case "F32"    ⇒ "scala.Float"
      case "F64"    ⇒ "scala.Double"

      case "String" ⇒ "java.lang.String"
    }

    case t : ArrayType       ⇒ s"$ArrayTypeName[${mapType(t.baseType)}]"
    case t : ListType        ⇒ s"$ListTypeName[${mapType(t.baseType)}]"
    case t : SetType         ⇒ s"$SetTypeName[${mapType(t.baseType)}]"
    case t : MapType         ⇒ s"$MapTypeName[${mapType(t.keyType)}, ${mapType(t.valueType)}]"

    case t : EnumDef         ⇒ s"EnumProxy[$packagePrefix${name(t)}.type]"

    case t : WithInheritance ⇒ "_root_." + packagePrefix + name(t)

    case _                   ⇒ throw new IllegalStateException(s"Unknown type $t")
  }

  override def makeHeader(headerInfo : HeaderInfo) : String = headerInfo.format(this, "/*", "*\\", " *", "* ", "\\*", "*/")

  /**
   * provides the package prefix
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix = ""

  override def setPackage(names : List[String]) {
    _packagePrefix = names.foldRight("")(_ + "." + _)
  }

  override def packageDependentPathPostfix = if (packagePrefix.length > 0) {
    packagePrefix.replace(".", "/")
  } else {
    ""
  }
  override def defaultCleanMode = "file";

  override def setOption(option : String, value : String) : Unit = option match {
    case unknown ⇒ sys.error(s"unkown Argument: $unknown")
  }

  override def describeOptions = Seq()

  override def customFieldManual : String = """
!import string+    A list of imports that will be added where required.
!modifier string   A modifier, that will be put in front of the variable declaration.
!default string    Text to be inserted as replacement for default initialization."""

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   * Will add Z's if escaping is required.
   */
  private val escapeCache = new HashMap[String, String]();
  override final def escapedLonely(target : String) : String = escapeCache.getOrElse(target, {
    val result = target match {
      //keywords get a suffix "_", because that way at least auto-completion will work as expected
      case "abstract" | "case" | "catch" | "class" | "def" | "do" | "else" | "extends" | "false" | "final" | "finally" |
        "for" | "forSome" | "if" | "implicit" | "import" | "lazy" | "match" | "new" | "null" | "object" | "override" |
        "package" | "private" | "protected" | "return" | "sealed" | "super" | "this" | "throw" | "trait" | "true" |
        "try" | "type" | "var" | "while" | "with" | "yield" | "val" ⇒ s"`$target`"

      case t if t.forall(c ⇒ '_' == c || Character.isLetterOrDigit(c)) ⇒ t

      case _ ⇒ s"`$target`"
    }
    escapeCache(target) = result
    result
  })
}