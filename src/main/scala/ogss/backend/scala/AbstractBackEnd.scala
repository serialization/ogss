package ogss.backend.scala

import ogss.oil.OGFile
import ogss.oil.TypeContext
import ogss.backend.common.BackEnd
import ogss.oil.FieldLike
import ogss.oil.EnumDef
import ogss.oil.InterfaceDef
import ogss.oil.UserDefinedType
import scala.collection.mutable.HashMap
import ogss.oil.ClassDef

import scala.collection.JavaConverters._
import ogss.oil.Type
import ogss.oil.Identifier
import ogss.oil.Field
import ogss.oil.EnumConstant
import ogss.oil.WithInheritance

/**
 * Abstract Scala back-end
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
  override def comment(f : FieldLike) : String = format(f.getComment, "/**\n", "   * ", "     */\n  ")
  def comment(v : EnumConstant) : String = format(v.getComment, "/**\n", "   * ", "     */\n  ")
  def name(v : EnumConstant) : String = escaped(capital(v.getName))

  protected def subtype(t : WithInheritance) = escaped("sub " + capital(t.getName))

  protected def localFieldName(f : Field) = escaped("_" + camel(f.getName))

  val ArrayTypeName = "scala.collection.mutable.ArrayBuffer"
  val ListTypeName = "scala.collection.mutable.ListBuffer"
  val SetTypeName = "scala.collection.mutable.HashSet"
  val MapTypeName = "scala.collection.mutable.HashMap"

  /**
   * Translate a type to its Java type name
   */
  def mapType(t : Type) : String

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
  protected def packageLastName = {
    val name = packageName
    name.lastIndexOf('.') match {
      case -1 ⇒ name
      case n  ⇒ name.substring(n + 1, name.length())
    }
  }

  /**
   * all string literals used in type and field names
   */
  protected lazy val allStrings : Array[Identifier] = (flatIR.map(_.getName).toSet ++
    flatIR.flatMap(_.getFields.asScala).map(_.getName).toSet ++
    flatTC.getEnums.asScala.map(_.getName).toSet ++
    flatTC.getEnums.asScala.flatMap(_.getValues.asScala).map(_.getName).toSet).toArray.sortBy(_.getOgss)

  /**
   * getter name
   */
  protected[scala] def getter(f : FieldLike) : String = escaped(camel(f.getName))
  /**
   * setter name
   */
  protected[scala] def setter(f : FieldLike) : String = escaped(s"${camel(f.getName)}_=")

  override protected def defaultValue(f : Field) : String = {
    f.getType match {
      case t : EnumDef ⇒ s"$packagePrefix${name(t)}.${name(t.getValues.get(0))}"
      case t ⇒
        val stid = t.getStid
        if (stid < 0 | 8 <= stid)
          "null"
        else if (0 == stid)
          "false"
        else if (stid < 6)
          "0"
        else
          "0.0f"
    }
  }

  /// options \\\

  /**
   * this string may contain a "@SuppressWarnings("all")\n", in order to suppress warnings in generated code;
   * the option can be enabled by "-O@java:SuppressWarnings=true"
   */
  protected var suppressWarnings = "";
}