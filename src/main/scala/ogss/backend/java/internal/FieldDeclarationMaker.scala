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
package ogss.backend.java.internal

import scala.collection.mutable.ArrayBuffer

import ogss.backend.java.AbstractBackEnd
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
        val fieldActualType = mapType(originalF.`type`, true)
        val fieldActualTypeUnboxed = mapType(originalF.`type`, false)

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
public static final class $nameF extends ${
          if (f.isTransient) "AutoField"
          else "FieldDeclaration"
        }<$fieldActualType, ${mapType(t)}>${
          var interfaces = new ArrayBuffer[String]()

          // mark data fields as known
          if (!f.isTransient) interfaces += "KnownField"

          // mark ignored fields as ignored; read function is inherited
          // TODO remove? if (f.isIgnored()) interfaces += "IgnoredField"

          // mark interface fields
          if (f.`type`.isInstanceOf[InterfaceDef]) interfaces += "InterfaceField"

          if (interfaces.isEmpty) ""
          else interfaces.mkString(" implements ", ", ", "")
        } {
${
          if (f.isTransient) s"""
    $nameF(FieldType<$fieldActualType> type, ${access(t)} owner) {
        super(type, "${ogssname(f)}", ${-1 - autoFieldIndex(f)}, owner);
    }"""
          else s"""
    $nameF(FieldType<$fieldActualType> type, int ID, ${access(t)} owner) {
        super(type, "${ogssname(f)}", ID, owner);
        // TODO insert known restrictions?
    }"""
        }
${
          if (f.isTransient) ""
          else s"""
    @Override
    protected final void read(int i, final int h, MappedInStream in) {
        ${readCode(t, originalF)}
    }
    @Override
    protected final boolean write(int i, final int h, BufferedOutStream out) throws IOException {
        boolean drop = true;
        ${writeCode(t, originalF)}
        return drop;
    }
"""
        }
    @Override
    public $fieldActualType get(Obj ref) {
        return ${
          if (f.`type`.isInstanceOf[EnumDef]) s"(${mapType(f.`type`)})"
          else ""
        }((${mapType(t)}) ref).${name(f)};
    }

    @Override
    public void set(Obj ref, $fieldActualType value) {
        ((${mapType(t)}) ref).${name(f)} = value;
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
        final AnyRefType t = (AnyRefType) type;"""
      case "String" ⇒ """
        final StringPool t = (StringPool) type;"""

      case _ ⇒ ""
    }

    case t : ArrayType ⇒ s"""
        final ogss.common.java.internal.fieldTypes.ArrayType<${mapType(t.baseType, true)}> type = (ogss.common.java.internal.fieldTypes.ArrayType<${mapType(t.baseType, true)}>) this.type;"""
    case t : ListType ⇒ s"""
        final ogss.common.java.internal.fieldTypes.ListType<${mapType(t.baseType, true)}> type = (ogss.common.java.internal.fieldTypes.ListType<${mapType(t.baseType, true)}>) this.type;"""
    case t : SetType ⇒ s"""
        final ogss.common.java.internal.fieldTypes.SetType<${mapType(t.baseType, true)}> type = (ogss.common.java.internal.fieldTypes.SetType<${mapType(t.baseType, true)}>) this.type;"""
    case t : MapType ⇒
      locally {
        val mt = s"ogss.common.java.internal.fieldTypes.MapType<${mapType(t.keyType, true)}, ${mapType(t.valueType, true)}>"
        s"""
        final $mt type = ($mt) this.type;
        final FieldType keyType = type.keyType;
        final FieldType valueType = type.valueType;"""
      }

    case t : InterfaceDef if t.superType != null ⇒ s"""
        final ${access(t.superType)} t = (${access(t.superType)})
                FieldDeclaration.<${mapType(t.superType)},${mapType(t)}>cast(type);"""

    case t : InterfaceDef ⇒ s"""
        final AnyRefType t = (AnyRefType) FieldDeclaration.<Obj,${mapType(t)}>cast(type);"""

    case t : EnumDef ⇒ s"""
        final EnumPool<?> type = (EnumPool<?>) this.type;"""

    case t : ClassDef ⇒ s"""
        final ${access(t)} t = ((${access(t)}) type);"""
    case _ ⇒ ""
  }

  /**
   * creates code to read all field elements
   */
  private final def readCode(t : ClassDef, f : Field) : String = {
    val declareD = s"final Obj[] d = ((${access(t.baseType)}) owner${
      if (null == t.superType) ""
      else ".basePool"
    }).data();"
    val fieldAccess = s"((${mapType(t)})d[i]).${name(f)}"

    val pre = prelude(f.`type`)

    val code = f.`type` match {
      case t : BuiltinType ⇒ ogssname(t) match {
        case "AnyRef" ⇒ "t.r(in)"
        case "String" ⇒ "t.get(in.v32())"
        case s        ⇒ s"""in.${s.toLowerCase}()"""
      }

      case t : InterfaceDef if t.superType != null ⇒ s"(${mapType(t)}) t.get(in.v32())"
      case t : InterfaceDef ⇒ s"(${mapType(t)}) t.r(in)"

      case t : ClassDef ⇒ "t.get(in.v32())"
      case _ ⇒ "type.r(in)"
    }

    s"""$declareD$pre
        for (; i != h; i++) {
            $fieldAccess = $code;
        }
"""
  }

  /**
   * creates code to write exactly one field element
   */
  private final def writeCode(t : ClassDef, f : Field) : String = {
    val declareD = s"final Obj[] d = ((${access(t.baseType)}) owner${
      if (null == t.superType) ""
      else ".basePool"
    }).data();"
    val fieldAccess = s"((${mapType(t)})d[i]).${name(f)}"

    f.`type`.stid match {
      case 0 ⇒ s"""$declareD
        final ogss.common.jvm.streams.BoolOutWrapper wrap = new ogss.common.jvm.streams.BoolOutWrapper(out);
        for (; i != h; i++) {
            final boolean v = $fieldAccess;
            drop &= !v;
            wrap.bool(v);
        }
        wrap.unwrap();
"""
      case _ ⇒ {

        val pre = prelude(f.`type`)

        val code = writeCode(f.`type`, fieldAccess)

        s"""$declareD$pre
        for (; i != h; i++) {
            $code;
        }
"""
      }
    }
  }

  private final def writeCode(t : Type, fieldAccess : String) : String = t match {
    case t : BuiltinType ⇒ ogssname(t) match {
      case "AnyRef" ⇒ s"drop &= t.w($fieldAccess, out)"
      case "String" ⇒ s"drop &= t.w($fieldAccess, out)"
      case "Bool"   ⇒ s"""${mapType(t)} v=$fieldAccess;drop&=!v;out.bool(v)"""
      case s        ⇒ s"""${mapType(t)} v=$fieldAccess;drop&=0==v;out.${s.toLowerCase}(v)"""
    }

    case t : InterfaceDef if t.superType != null ⇒ s"""Obj v = (Obj)$fieldAccess;
            final int id = (null == v ? 0 : v.ID());
            if(0 == id)
                out.i8((byte)0);
            else {
                drop = false;
                out.v64(id);
            }"""

    case t : InterfaceDef ⇒ s"drop &= t.w((Obj)$fieldAccess, out)"

    case t : ClassDef ⇒ s"""${mapType(t)} v = $fieldAccess;
            final int id = (null == v ? 0 : v.ID());
            if(0 == id)
                out.i8((byte)0);
            else {
                drop = false;
                out.v64(id);
            }"""
    case _ ⇒ s"drop &= type.w($fieldAccess, out)"
  }
}
