package ogss.backend.skill

import ogss.util.HeaderInfo
import ogss.oil.Type
import ogss.oil.BuiltinType
import ogss.oil.SetType
import ogss.oil.ArrayType
import ogss.oil.MapType
import ogss.oil.ListType

/**
 * Dump an .oil-file as SKilL specification.
 *
 * @author Timm Felden
 */
class Main
  extends AbstractBackEnd
  with SpecificationMaker {

  override def name : String = "SKilL";
  override def description = "SKilL specification"

  override def packageDependentPathPostfix = ""
  override def defaultCleanMode = "file";

  override def mapType(t : Type) : String = t match {
    case t : BuiltinType ⇒ camel(t.name) match {
      case "anyRef" ⇒ "annotation"
      case s        ⇒ s
    }
    case t : ArrayType ⇒ s"${mapType(t.baseType)}[]"
    case t : ListType  ⇒ s"list<${mapType(t.baseType)}>"
    case t : SetType   ⇒ s"set<${mapType(t.baseType)}>"

    // note: should rebuild n-ary maps
    case t : MapType ⇒ s"map<${mapType(t.keyType)}, ${mapType(t.valueType)}>"
      .replace(", map<", ", ")
      .replaceAll(">+", ">")

    case t ⇒ t.name.ogss
  }

  override def makeHeader(headerInfo : HeaderInfo) : String = headerInfo.format(this, "# ", "", "# ", "", "# ", "\n")

  /**
   * provides the package prefix
   */
  override protected def packagePrefix() : String = _packagePrefix
  private var _packagePrefix : String = null

  override def setPackage(names : List[String]) {
    _packagePrefix = names.foldRight("")(_ + "." + _)
  }

  override def setOption(option : String, value : String) {
    sys.error(s"unkown Argument: $option")
  }

  override def describeOptions = Seq()

  override def customFieldManual : String = """
  !include string+    Argument strings are added to the head of the generated file and included using
                      <> around the strings content.
  !default string     Text to be inserted as replacement for default initialization."""

  /**
   * We do not escape anything
   */
  final def escapedLonely(target : String) : String = target
}
