package ogss.backend.java

import ogss.backend.common.BackEnd
import ogss.oil.FieldLike
import ogss.oil.EnumDef
import ogss.oil.OGFile
import scala.collection.mutable.ArrayBuffer
import ogss.oil.TypeContext

import scala.collection.JavaConverters._
import ogss.oil.InterfaceDef
import ogss.oil.Type
import ogss.oil.ClassDef
import scala.collection.mutable.HashMap
import ogss.oil.UserDefinedType

/**
 * Abstract java back-end
 *
 * @author Timm Felden
 */
abstract class AbstractBackEnd extends BackEnd {

  final def setIR(TC : OGFile) {
    types = asScalaIterator(TC.TypeContexts.iterator()).find { tc ⇒ tc.getProjectedTypeDefinitions && !tc.getProjectedInterfaces }.get
    flatTC = asScalaIterator(TC.TypeContexts.iterator()).find { tc ⇒ tc.getProjectedTypeDefinitions && tc.getProjectedInterfaces }.get
    IR = types.getClasses.asScala.to
    flatIR = flatTC.getClasses.asScala.to
    projected = flatIR.foldLeft(new HashMap[String, ClassDef])(
      (m, t) ⇒ { m(ogssname(t)) = t; m }
    )
    interfaces = types.getInterfaces.asScala.to
    enums = types.getEnums.asScala.to
  }

  var types : TypeContext = _
  var flatTC : TypeContext = _
  var IR : Array[ClassDef] = _
  var flatIR : Array[ClassDef] = _
  var interfaces : Array[InterfaceDef] = _
  var enums : Array[EnumDef] = _

  var projected : HashMap[String, ClassDef] = _

  lineLength = 120
  override def comment(d : UserDefinedType) : String = format(d.getComment, "/**\n", " * ", " */\n")
  override def comment(f : FieldLike) : String = format(f.getComment, "/**\n", "     * ", "     */\n    ")

  // container type mappings
  val ArrayTypeName = "java.util.ArrayList"
  val ListTypeName = "java.util.LinkedList"
  val SetTypeName = "java.util.HashSet"
  val MapTypeName = "java.util.HashMap"

  /**
   * Translate a type to its Java type name
   */
  def mapType(t : Type, boxed : Boolean = false) : String

  /**
   * id's given to fields
   */
  private val poolNameStore : HashMap[String, Int] = new HashMap()
  /**
   * The name of T's storage pool
   */
  protected def access(t : ClassDef) : String = this.synchronized {
    "P" + poolNameStore.getOrElseUpdate(ogssname(t), poolNameStore.size).toString
  }
  /**
   * The name of T's builder
   */
  protected def builder(target : ClassDef) : String = {
    val t = projected(ogssname(target))
    if (null != t.getSuperType && t.getFields.isEmpty) {
      builder(t.getSuperType)
    } else {
      this.synchronized {
        s"B${poolNameStore.getOrElseUpdate(ogssname(t), poolNameStore.size)}"
      }
    }
  }
  /**
   * The name of T's unknown sub pool
   */
  protected def subPool(t : Type) : String = this.synchronized {
    "S" + poolNameStore.getOrElseUpdate(ogssname(t), poolNameStore.size).toString
  }

  /**
   * id's given to fields
   */
  protected val fieldNameStore : HashMap[(String, String), Int] = new HashMap()
  /**
   * Class name of the representation of a known field
   */
  protected def knownField(f : FieldLike) : String = this.synchronized {
    "f" + fieldNameStore.getOrElseUpdate((ogssname(f.getOwner), ogssname(f)), fieldNameStore.size).toString
  }

  /**
   * Assume a package prefix provider.
   */
  protected def packagePrefix() : String
  protected def packageName = packagePrefix.substring(0, packagePrefix.length - 1)

  /**
   * getter name
   */
  protected def getter(f : FieldLike) : String = s"get${escaped(capital(f.getName))}"
  /**
   * setter name
   */
  protected def setter(f : FieldLike) : String = s"set${escaped(capital(f.getName))}"

  /// options \\\

  /**
   * this string may contain a "@SuppressWarnings("all")\n", in order to suppress warnings in generated code;
   * the option can be enabled by "-O@java:SuppressWarnings=true"
   */
  protected var suppressWarnings = "";
}