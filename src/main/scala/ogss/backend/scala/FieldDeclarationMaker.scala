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
package ogss.backend.scala

import scala.collection.mutable.ArrayBuffer

import ogss.io.PrintWriter
import ogss.oil.ArrayType
import ogss.oil.BuiltinType
import ogss.oil.ClassDef
import ogss.oil.EnumDef
import ogss.oil.Field
import ogss.oil.InterfaceDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type

trait FieldDeclarationMaker extends AbstractBackEnd {
  final def makeFields(out : PrintWriter) {

    for (t ← flatIR) {
      val autoFieldIndex : Map[Field, Int] = t.fields.filter(_.isTransient).zipWithIndex.toMap

      for (f ← t.fields) {
        // the field before interface projection
        val originalF = allFields(this.types.byName(ogssname(t)).asInstanceOf[ClassDef])
          .find(_.name == f.name).get

        // the type before the interface projection
        val fieldActualType = mapType(originalF.`type`)

        val tIsBaseType = t.superType == null

        val nameT = mapType(t)
        val nameF = knownField(f)

        // casting access to data array using index i
        val declareD = s"final ${mapType(t.baseType)}[] d = ((${access(t.baseType)}) owner.basePool()).data();"
        val fieldAccess = (
          if (null == t.superType) "d[i]"
          else s"((${mapType(t)})d[i])"
        ) + s".${name(f)}"

        out.write(s"""
  /**
   * ${ogssname(f.`type`)} ${ogssname(t)}.${ogssname(f)}
   */
  final class $nameF (_t : FieldType[_], ${if (f.isTransient) "" else "_ID : scala.Int, "}_owner : ${access(t)})
    extends ${
          if (f.isTransient) "AutoField"
          else "ogss.common.scala.internal.Field"
        }[$fieldActualType, ${mapType(t)}](_t.asInstanceOf[FieldType[$fieldActualType]], "${ogssname(f)}", ${
          if (f.isTransient) (-1 - autoFieldIndex(f)).toString else "_ID"
        }, _owner)${
          var interfaces = new ArrayBuffer[String]()

          // mark data fields as known
          if (!f.isTransient) interfaces += "KnownField"

          // mark interface fields
          if (f.`type`.isInstanceOf[InterfaceDef]) interfaces += "InterfaceField"

          if (interfaces.isEmpty) ""
          else interfaces.mkString(" with ", " with ", "")
        } {
${
          if (f.isTransient) ""
          else s"""
    override def read(begin : scala.Int, h : scala.Int, in : MappedInStream) {
      ${readCode(t, originalF)}
    }
    override def write(begin : scala.Int, h : scala.Int, out : BufferedOutStream) : scala.Boolean = {
      var drop = true;
      ${writeCode(t, originalF)}
      drop
    }
"""
        }
    override def get(ref : Obj) : $fieldActualType = ref.asInstanceOf[${mapType(t)}].${localFieldName(f)}${
          f.`type` match {
            case ft : EnumDef ⇒ s""" match {
        case null ⇒ owner.owner.asInstanceOf[OGFile].${name(ft)}.proxy(${defaultValue(f)})
        case x : ${mapType(ft)} ⇒ x
        case x : ${name(ft)}.Value ⇒ owner.owner.asInstanceOf[OGFile].${name(ft)}.proxy(x)
      }"""
            case _ ⇒ ""
          }
        }

    override def set(ref : Obj, value : $fieldActualType) {
      ref.asInstanceOf[${mapType(t)}].${localFieldName(f)} = value;
    }
  }
""")
      }
    }
  }

  /**
   * create local variables holding type representants with correct type to help
   * the compiler
   */
  private final def prelude(t : Type) : String = t match {
    case t : BuiltinType ⇒ ogssname(t) match {
      case "AnyRef" ⇒ s"""
      val t = this.t.asInstanceOf[AnyRefType]"""
      case "String" ⇒ """
      val t = this.t.asInstanceOf[StringPool]"""

      case _ ⇒ ""
    }

    case t : ArrayType ⇒ s"""
      val t = this.t.asInstanceOf[ogss.common.scala.internal.fieldTypes.ArrayType[${mapType(t.baseType)}]]"""
    case t : ListType ⇒ s"""
      val t = this.t.asInstanceOf[ogss.common.scala.internal.fieldTypes.ListType[${mapType(t.baseType)}]]"""
    case t : SetType ⇒ s"""
      val t = this.t.asInstanceOf[ogss.common.scala.internal.fieldTypes.SetType[${mapType(t.baseType)}]]"""
    case t : MapType ⇒
      locally {
        val mt = s"ogss.common.scala.internal.fieldTypes.MapType[${mapType(t.keyType)}, ${mapType(t.valueType)}]"
        s"""
      val t = this.t.asInstanceOf[$mt]
      val keyType = t.keyType;
      val valueType = t.valueType;"""
      }

    case t : InterfaceDef if t.superType != null ⇒ s"""
      val t = this.t.asInstanceOf[${access(t.superType)}]"""

    case t : InterfaceDef ⇒ s"""
      val t = this.t.asInstanceOf[AnyRefType]"""

    case t : EnumDef ⇒ s"""
      val t = this.t.asInstanceOf[EnumPool[_]]"""

    case t : ClassDef ⇒ s"""
      val t = this.t.asInstanceOf[${access(t)}]"""
    case _ ⇒ ""
  }

  /**
   * creates code to read all field elements
   */
  private final def readCode(t : ClassDef, f : Field) : String = {
    val declareD = s"val d = owner${
      if (null == t.superType) ""
      else ".basePool"
    }.asInstanceOf[${access(t.baseType)}]._data"
    val fieldAccess = s"d(i).asInstanceOf[${mapType(t)}].${localFieldName(f)}"

    val pre = prelude(f.`type`)

    val code = f.`type` match {
      case t : BuiltinType ⇒ ogssname(t) match {
        case "AnyRef" ⇒ "t.r(in)"
        case "String" ⇒ "t.get(in.v32())"
        case s        ⇒ s"""in.${s.toLowerCase}()"""
      }

      case t : InterfaceDef if t.superType != null ⇒ s"t.get(in.v32()).asInstanceOf[${mapType(t)}]"
      case t : InterfaceDef                        ⇒ s"t.r(in).asInstanceOf[${mapType(t)}]"

      case t : ClassDef                            ⇒ "t.get(in.v32())"
      case _                                       ⇒ "t.r(in)"
    }

    s"""$declareD$pre
      var i = begin
      while (i != h) {
        $fieldAccess = $code;
        i += 1
      }
"""
  }

  /**
   * creates code to write exactly one field element
   */
  private final def writeCode(t : ClassDef, f : Field) : String = {
    val declareD = s"val d = owner${
      if (null == t.superType) ""
      else ".basePool"
    }.asInstanceOf[${access(t.baseType)}]._data"
    val fieldAccess = s"d(i).asInstanceOf[${mapType(t)}].${localFieldName(f)}"

    f.`type`.stid match {
      case 0 ⇒ s"""$declareD
      val wrap = new ogss.common.streams.BoolOutWrapper(out)
      var i = begin
      while (i != h) {
        val v = $fieldAccess;
        drop &= !v;
        wrap.bool(v);
        i += 1
      }
      wrap.unwrap();
"""
      case _ ⇒ {

        val pre = prelude(f.`type`)

        val code = writeCode(f.`type`, fieldAccess)

        s"""$declareD$pre
      var i = begin
      while (i != h) {
        $code;
        i += 1
      }
"""
      }
    }
  }

  private final def writeCode(t : Type, fieldAccess : String) : String = t match {
    case t : BuiltinType ⇒ ogssname(t) match {
      case "AnyRef" ⇒ s"drop &= t.w($fieldAccess, out)"
      case "String" ⇒ s"drop &= t.w($fieldAccess, out)"
      case "Bool"   ⇒ s"""val v=$fieldAccess;drop&= !v;out.bool(v)"""
      case s        ⇒ s"""val v=$fieldAccess;drop&=0==v;out.${s.toLowerCase}(v)"""
    }

    case t : InterfaceDef if t.superType != null ⇒ s"""val v = $fieldAccess;
        val id = if(null == v) 0 else v.ID
        if(0 == id)
          out.i8(0);
        else {
          drop = false;
          out.v64(id);
        }"""

    case t : ClassDef ⇒ s"""val v = $fieldAccess;
        val id = if(null == v) 0 else v.ID
        if(0 == id)
          out.i8(0);
        else {
          drop = false;
          out.v64(id);
        }"""
    case _ ⇒ s"drop &= t.w($fieldAccess, out)"
  }
}
