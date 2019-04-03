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
package ogss.backend.cpp

import scala.collection.JavaConverters._
import ogss.oil.BuiltinType
import ogss.oil.Type
import ogss.oil.SetType
import ogss.oil.ArrayType
import ogss.oil.Field
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.ClassDef

trait FieldDeclarationsMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    makeHeader
    makeSource
  }
  private def makeHeader {

    // one file per base type
    for (base ← IR if null == base.getSuperType) {
      val out = files.open(s"${name(base)}FieldDeclarations.h")

      out.write(s"""${beginGuard(s"${name(base)}_field_declarations")}
#include <ogss/fieldTypes/AnyRefType.h>
#include <ogss/api/File.h>

#include "TypesOf${name(base)}.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace internal {
""")

      out.write((for (t ← IR if base == t.getBaseType; f ← t.getFields.asScala) yield s"""
        /**
         * ${f.getType.getName.getOgss} ${capital(t.getName)}.${camel(f.getName)}
         */
        class ${knownField(f)} : public ::ogss::internal::${
        if (f.getIsTransient) "Auto"
        else "Data"
      }Field {
        public:
            ${knownField(f)}(
                    const ::ogss::fieldTypes::FieldType *const type, ${
        if (f.getIsTransient) ""
        else " ::ogss::TypeID index,"
      }
                    ::ogss::internal::AbstractPool *const owner);

            virtual bool check() const;

            virtual ::ogss::api::Box getR(const ::ogss::api::Object *i) {
                return ::ogss::api::box(((${mapType(t)})i)->${name(f)});
            }

            virtual void setR(::ogss::api::Object *i, ::ogss::api::Box v) {
                ((${mapType(t)})i)->${name(f)} = (${mapType(f.getType)})v.${unbox(f.getType)};
            }

    protected:
            void read(int i, int last, ::ogss::streams::MappedInStream &in) const final;

            bool write(int i, int last, ::ogss::streams::BufferedOutStream &out) const final;
        };""").mkString)

      out.write(s"""
    }
${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")

      out.close()
    }
  }

  private def makeSource {

    // one file per base type
    for (base ← IR if null == base.getSuperType) {
      val out = files.open(s"${name(base)}FieldDeclarations.cpp")

      out.write(s"""
#include "${name(base)}FieldDeclarations.h"
#include "Pools.h"
${
        (for (t ← IR if base == t.getBaseType; f ← t.getFields.asScala) yield {
          val tIsBaseType = t.getSuperType == null

          val fieldName = s"$packageName::internal::${knownField(f)}"
          val accessI = s"d[i]->${name(f)}"
          val readI = s"$accessI = ${readType(f.getType, accessI)};"
          s"""
$fieldName::${knownField(f)}(
        const ::ogss::fieldTypes::FieldType *const type,${
            if (f.getIsTransient) ""
            else """
        ::ogss::TypeID index,"""
          }
        ::ogss::internal::AbstractPool *const owner)
        : ${
            if (f.getIsTransient) "AutoField(type, name, SK_TODO, owner)"
            else "DataField(type, name, index, owner)"
          } {
}
${
            if (f.getIsTransient) ""
            else s"""
void $fieldName::read(int i, const int last, ::ogss::streams::MappedInStream &in) const {
    auto d = ((${access(t)} *) owner)->data;
    for (; i != last; i++) {
        $readI
    }
}

bool $fieldName::write(int i, const int last, ::ogss::streams::BufferedOutStream &out) const {
    ${mapType(t)}* d = ((${access(t)}*) owner)->data;
    bool drop = true;
    for (; i != last; i++) {
        ${writeCode(accessI, f)}
    }
    return drop;
}

"""
          }
bool $fieldName::check() const {
${
            val checks = "" /*(for (r ← f.getRestrictions)
              yield checkRestriction(f.getType, r)).mkString;*/

            s"""
    ${access(t)} *p = (${access(t)} *) owner;
    for (const auto& i : *p) {
        const auto v = i.${name(f)};
$checks
    }
"""
          }
    return true;
}
"""
        }).mkString
      }""")

      out.close()
    }
  }

  /**
   * choose a good parse expression
   *
   * @note accessI is only used to create inner maps correctly
   */
  private final def readType(t : Type, typ : String = "type") : String = t match {
    case t : BuiltinType ⇒ lowercase(t.getName) match {
      case "anyref" ⇒ s"$typ->read(in).anyRef"
      case "string" ⇒ s"$typ->read(in).string"
      case "bool"   ⇒ "in.boolean()"
      case t        ⇒ s"in.$t()"
    }

    case t : ArrayType ⇒ s"((ogss::fieldTypes::ArrayType*)$typ)->read<${mapType(t.getBaseType)}>(*in)"
    case t : ListType  ⇒ s"((ogss::fieldTypes::ListType*)$typ)->read<${mapType(t.getBaseType)}>(*in)"
    case t : SetType   ⇒ s"((ogss::fieldTypes::SetType*)$typ)->read<${mapType(t.getBaseType)}>(*in)"
    case t : MapType   ⇒ s"((ogss::fieldTypes::MapType*)$typ)->read<${mapType(t.getKeyType)}, ${mapType(t.getValueType)}>(*in)"

    //case t : UserType ⇒ s"    val t = this.t.asInstanceOf[${storagePool(t)}]"
    case _             ⇒ s"(${mapType(t)})$typ->read(in).${unbox(t)}"
  }

  private final def hex(t : Type, x : Long) : String = {
    val v : Long =
      if (x == Long.MaxValue || x == Long.MinValue) x >> (64 - t.getName.getOgss.substring(1).toInt);
      else x

    t.getName.getOgss match {
      case "I8"  ⇒ "0x%02X".format(v.toByte)
      case "I16" ⇒ "0x%04X".format(v.toShort)
      case "I32" ⇒ "0x%08X".format(v.toInt)
      case _     ⇒ "0x%016X".format(v)
    }
  }

  private def writeCode(accessI : String, f : Field) : String = f.getType match {
    case t : BuiltinType ⇒ lowercase(t.getName) match {
      case "anyref" | "string" ⇒ s"""const auto v = $accessI;
            if (v) {
                type->write(out, ::ogss::box(v));
                drop = false;
            } else
                out.i8(0);"""
      case "bool" ⇒ s"const bool v = $accessI;drop &= !v;out.boolean(v);"
      case n      ⇒ s"const auto v = $accessI;drop &= !v;out.$n(v);"
    }

    case t : ClassDef ⇒ s"""${mapType(t)} v = $accessI;
            if (v) {
                out->v64(ogssID(v));
                drop = false;
            } else
                out->i8(0);"""

    case t : ListType ⇒ s"drop &= ((ogss::fieldTypes::ListType*)type)->write<${mapType(t.getBaseType)}>(out, $accessI);"

    case _ ⇒ s"""auto b = ::ogss::box($accessI);
            drop &= type->write(out, b);"""
  }
}
