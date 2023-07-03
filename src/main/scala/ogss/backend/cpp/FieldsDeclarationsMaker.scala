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

import ogss.oil.{ArrayType, BuiltinType, ClassDef, EnumDef, Field, ListType, MapType, SetType, Type}

trait FieldDeclarationsMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    makeHeader
    makeSource
  }
  private def makeHeader {

    // one file per base type
    for (base ← IR if null == base.superType) {
      val out = files.open(s"${name(base)}FieldDeclarations.h")

      out.write(s"""${beginGuard(s"${name(base)}_field_declarations")}
#include <ogss/api/File.h>
#include <ogss/fieldTypes/AnyRefType.h>
#include <ogss/internal/AutoField.h>
#include <ogss/internal/DataField.h>
#include <ogss/internal/KnownEnumField.h>

#include "TypesOf${name(base)}.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace internal {
""")

      out.write((for (t ← IR if base == t.baseType; f ← t.fields) yield s"""
        /**
         * ${f.`type`.name.ogss} ${capital(t.name)}.${camel(f.name)}
         */
        class ${knownField(f)} : public ::ogss::internal::${
        if(f.`type`.isInstanceOf[EnumDef]) "KnownEnum"
        else if (f.isTransient) "Auto"
        else "Data"
      }Field {
        public:
            ${knownField(f)}(
                    const ::ogss::fieldTypes::FieldType *const type, ${
        if (f.isTransient) ""
        else " ::ogss::TypeID index,"
      }
                    ::ogss::internal::AbstractPool *const owner);

            virtual ::ogss::api::Box getR(const ::ogss::api::Object *i) {
                return ::ogss::api::box(((${mapType(t)})i)->${name(f)});
            }

            virtual void setR(::ogss::api::Object *i, ::ogss::api::Box v) {
                ((${mapType(t)})i)->${name(f)} = (${mapType(f.`type`)})v.${unbox(f.`type`)};
            }
${
        if (f.isTransient) ""
        else """
            virtual bool check() const;

    protected:
            void read(int i, int last, ::ogss::streams::MappedInStream &in) const final;

            bool write(int i, int last, ::ogss::streams::BufferedOutStream *out) const final;
"""
      }
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
    for (base ← IR if null == base.superType) {
      val out = files.open(s"${name(base)}FieldDeclarations.cpp")

      out.write(s"""
#include <ogss/fieldTypes/ArrayType.h>
#include <ogss/fieldTypes/ListType.h>
#include <ogss/fieldTypes/SetType.h>
#include <ogss/fieldTypes/MapType.h>

#include "Pools.h"
#include "${name(base)}FieldDeclarations.h"
${
        (for (t ← IR if base == t.baseType; f ← t.fields) yield {
          val autoFieldIndex : Map[Field, Int] = t.fields.filter(_.isTransient).zipWithIndex.toMap

          val tIsBaseType = t.superType == null

          val fieldName = s"$packageName::internal::${knownField(f)}"
          val accessI = s"d[i++]->${name(f)}"
          val readI = s"$accessI = ${readType(f.`type`)};"
          s"""
$fieldName::${knownField(f)}(
        const ::ogss::fieldTypes::FieldType *const type,${
            if (f.isTransient) ""
            else """
        ::ogss::TypeID index,"""
          }
        ::ogss::internal::AbstractPool *const owner)
        : ${
            if (f.isTransient) s"AutoField(type, ${skName(f.name)}, ${-1 - autoFieldIndex(f)}, owner)"
            else {
              (if (f.`type`.isInstanceOf[EnumDef]) "KnownEnum"
              else "Data") + s"Field(type, ${skName(f.name)}, index, owner)"
            }
          } {
}
${
            if (f.isTransient) ""
            else s"""
void $fieldName::read(int i, const int last, ::ogss::streams::MappedInStream &in) const {
    auto d = ((${access(t)} *) owner)->data;
    while (i != last) {
        $readI
    }
}

bool $fieldName::write(int i, const int last, ::ogss::streams::BufferedOutStream *out) const {
    ${mapType(t)}* d = ((${access(t)}*) owner)->data;
    bool drop = true;
    while (i != last) {
        ${writeCode(accessI, f)}
    }
    return drop;
}

bool $fieldName::check() const {
${
              val checks = "" /*(for (r ← f.getRestrictions)
              yield checkRestriction(f.`type`, r)).mkString;*/

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
          }"""
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
  private final def readType(t : Type) : String = t match {
    case t : BuiltinType ⇒ lowercase(t.name) match {
      case "anyref" ⇒ s"type->r(in).anyRef"
      case "string" ⇒ s"type->r(in).string"
      case "bool"   ⇒ "in.boolean()"
      case t        ⇒ s"in.$t()"
    }

    case t : ArrayType ⇒ s"((ogss::fieldTypes::ArrayType<${mapType(t.baseType)}>*)type)->read(in)"
    case t : ListType  ⇒ s"((ogss::fieldTypes::ListType<${mapType(t.baseType)}>*)type)->read(in)"
    case t : SetType   ⇒ s"((ogss::fieldTypes::SetType<${mapType(t.baseType)}>*)type)->read(in)"
    case t : MapType   ⇒ s"((ogss::fieldTypes::MapType<${mapType(t.keyType)}, ${mapType(t.valueType)}>*)type)->read(in)"

    case _             ⇒ s"(${mapType(t)})type->r(in).${unbox(t)}"
  }

  private final def hex(t : Type, x : Long) : String = {
    val v : Long =
      if (x == Long.MaxValue || x == Long.MinValue) x >> (64 - t.name.ogss.substring(1).toInt);
      else x

    t.name.ogss match {
      case "I8"  ⇒ "0x%02X".format(v.toByte)
      case "I16" ⇒ "0x%04X".format(v.toShort)
      case "I32" ⇒ "0x%08X".format(v.toInt)
      case _     ⇒ "0x%016X".format(v)
    }
  }

  private def writeCode(accessI : String, f : Field) : String = f.`type` match {
    case t : BuiltinType ⇒ lowercase(t.name) match {
      case "anyref" | "string" ⇒ s"""const auto v = $accessI;
            if (v) {
                type->w(::ogss::api::box(v), out);
                drop = false;
            } else
                out->i8(0);"""
      case "bool" ⇒ s"const bool v = $accessI;drop &= !v;out->boolean(v);"
      case n      ⇒ s"const auto v = $accessI;drop &= !v;out->$n(v);"
    }

    case t : ClassDef ⇒ s"""${mapType(t)} v = $accessI;
        if (v) {
            out->v64(objectID(v));
            drop = false;
        } else
            out->i8(0);"""

    case t : ListType ⇒ s"drop &= ((ogss::fieldTypes::ListType<${mapType(t.baseType)}>*)type)->w(::ogss::api::box($accessI), out);"

    case _            ⇒ s"""drop &= type->w(::ogss::api::box($accessI), out);"""
  }
}
