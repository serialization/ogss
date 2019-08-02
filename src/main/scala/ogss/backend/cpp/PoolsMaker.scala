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

import ogss.oil.ArrayType
import ogss.oil.BuiltinType
import ogss.oil.ClassDef
import ogss.oil.Field
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.EnumDef

trait PoolsMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    // one header file per base type
    val out = files.open(s"Pools.h")

    //prefix
    out.write(s"""${beginGuard("POOLS")}
#include <ogss/api/String.h>
#include <ogss/internal/Pool.h>
#include <ogss/internal/SubPool.h>
${
      IR.filter(_.superType == null).map(base ⇒ s"""
#include "TypesOf${name(base)}.h"
#include "${name(base)}FieldDeclarations.h"""").mkString
    }

#include "StringKeeper.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
namespace internal {
""")

    for (t ← IR) {
      val typeName = packageName + "::" + name(t)
      val typeT = mapType(t)
      val isSingleton = false //!t.getRestrictions.collect { case r : SingletonRestriction ⇒ r }.isEmpty
      val fields = t.fields

      // create the builder, if required
      if (null == t.superType || !fields.isEmpty)
        out.write(s"""
    struct ${access(t)};

    template<class T, class B>
    struct ${builder(t)} : public ${
          if (null == t.superType) s"::ogss::api::Builder"
          else s"${builder(t.superType)}<T, B>"
        } {${
          // regular fields
          (for (f ← fields)
            yield s"""

        B* ${name(f)}(${mapType(f.`type`)} ${name(f)}) {
            ((T)this->self)->${name(f)} = ${name(f)};
            return (B*)this;
        }""").mkString
        }${
          // enum fields get a initialization by constant in addition
          (for (
            f ← fields if f.`type`.isInstanceOf[EnumDef];
            ft = f.`type`.asInstanceOf[EnumDef]
          ) yield s"""

        B* ${name(f)}(${name(ft)} ${name(f)}) {
            ((T)this->self)->${setter(f)}(${name(f)});
            return (B*)this;
        }""").mkString
        }${
          if (null == t.superType) """
            /**
             * destroy the builder
             */
            virtual T make() {
                T rval = (T)self;
                delete this;
                return rval;
            }"""
          else ""
        }
    protected:
        ${builder(t)}(::ogss::api::Object* self) : ${
          if (null == t.superType) s"::ogss::api::Builder"
          else s"${builder(t.superType)}<T, B>"
        } (self) {}
    };
    template<class T>
    struct ${builder(t)}_IMPL final : public ${builder(t)}<T, ${builder(t)}_IMPL<T> > {
        ${builder(t)}_IMPL(::ogss::api::Object* self) : ${builder(t)}<T, ${builder(t)}_IMPL<T> > (self) {}
    };
""")

      // create the very pool
      out.write(s"""
    struct ${access(t)} final : public ::ogss::internal::Pool<$typeName> {
        ${access(t)}(::ogss::TypeID typeID${
        if (t.superType == null) ""
        else s", AbstractPool *superPool"
      }, ::std::unordered_set<::ogss::restrictions::TypeRestriction *> *restrictions)
                : ::ogss::internal::Pool<$typeName>(typeID, ${
        if (t.superType == null) "nullptr"
        else s"superPool"
      }, ${skName(t.name)}, restrictions, ${fields.count(_.isTransient)}) { }

        ${builder(t)}_IMPL<$typeT>* build() final {
            return new ${builder(t)}_IMPL<$typeT>(make());
        }${
        val enums = allFields(t).filter(_.`type`.isInstanceOf[EnumDef])
        if (enums.isEmpty) ""
        else s"""

        ${mapType(t)} make() final;"""
      }

    protected:
${
        if (fields.isEmpty) ""
        else s"""
        ::ogss::api::String KFN(uint32_t id) const override;
        ::ogss::internal::FieldDeclaration *KFC(uint32_t id, FieldType **SIFA, ::ogss::TypeID nextFID) override;"""
      }${
        if (t.subTypes.isEmpty) ""
        else s"""
        ::ogss::api::String nameSub(uint32_t id) const override;
        ::ogss::internal::AbstractPool* makeSub(uint32_t id, ::ogss::TypeID index,
                                                std::unordered_set<::ogss::restrictions::TypeRestriction *> *restrictions) override;
"""
      }
        ::ogss::internal::AbstractPool *makeSub(::ogss::TypeID typeID, ::ogss::api::String name,
                                                ::std::unordered_set<::ogss::restrictions::TypeRestriction *> *restrictions) override {
            return new ::ogss::internal::SubPool<${typeName}_UnknownSubType>(
                    typeID, this, name, restrictions);
        }
    };""")
    }

    out.write(s"""
}${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")
    out.close()

    makeSource
  }

  private def makeSource {
    // we only need a source file, if there is a field or a subtype
    if (IR.forall(_.fields.isEmpty) && IR.forall(_.superType == null))
      return ;

    val out = files.open(s"Pools.cpp")

    out.write(s"""
#include "File.h"
#include "Pools.h"
#include "StringKeeper.h"
${
      (for (t ← IR) yield {
        val poolName = packageName + "::internal::" + access(t)
        val typeT = mapType(t)
        val fields = t.fields

        s"""${
          if (fields.isEmpty) ""
          else s"""
::ogss::api::String ($poolName::KFN)(uint32_t id) const {
    ::ogss::api::String r;
    switch (id) {${
            fields.zipWithIndex.map {
              case (f, i) ⇒ s"""
        case $i: r = $packageName::${skName(f.name)}; break;"""
            }.mkString
          }
        default: r = nullptr;
    }
    return r;
}

::ogss::internal::FieldDeclaration *$poolName::KFC(uint32_t id, ::ogss::fieldTypes::FieldType **SIFA, ::ogss::TypeID nextFID) {
    ::ogss::internal::FieldDeclaration *r;
    switch (id) {${
            fields.zipWithIndex.map {
              case (f, i) ⇒ s"""
        case $i: r = new $packageName::internal::${knownField(f)}(SIFA[${f.`type`.stid}], ${
                if (f.isTransient) ""
                else "nextFID, "
              }this); break;"""
            }.mkString
          }
        default: r = nullptr;
    }
    return r;
}"""
        }${
          if (t.subTypes.isEmpty) ""
          else s"""

::ogss::api::String ($poolName::nameSub)(uint32_t id) const {
    ::ogss::api::String r;
    switch (id) {${
            t.subTypes.zipWithIndex.map {
              case (s, i) ⇒ s"""
        case $i: r = $packageName::${skName(s.name)}; break;"""
            }.mkString
          }
        default: r = nullptr;
    }
    return r;
}
::ogss::internal::AbstractPool* $poolName::makeSub(uint32_t id, ::ogss::TypeID index,
                                          std::unordered_set<::ogss::restrictions::TypeRestriction *> *restrictions) {
    ::ogss::internal::AbstractPool *r;
    switch (id) {${
            t.subTypes.collect { case t : ClassDef ⇒ t }.zipWithIndex.map {
              case (s, i) ⇒ s"""
        case $i: r = new $packageName::internal::${access(s)}(index, this, restrictions); break;"""
            }.mkString
          }
        default: r = nullptr;
    }
    return r;
}
"""
        }${
          val enums = allFields(t).filter(_.`type`.isInstanceOf[EnumDef])
          if (enums.isEmpty) ""
          else enums.map { f ⇒
            s"""
    r->${name(f)} = (($packageName::api::File*)owner)->${name(f.`type`)}->get(${defaultValue(f)});"""
          }.mkString(s"""

${mapType(t)} $poolName::make() {
    const auto r = ::ogss::internal::Pool<$packageName::${name(t)}>::make();""", "", """
    return r;
}
""")
        }"""
      }).mkString
    }""")

    out.close()
  }

  /**
   * escaped name for field classes
   */
  private final def clsName(f : Field) : String = escaped("Cls" + camel(f.name))

  protected def mapFieldDefinition(t : Type) : String = t match {
    case t : BuiltinType ⇒ t.name.ogss match {
      case "String" ⇒ "state->strings"
      case "AnyRef" ⇒ "state->getAnnotationType()"
      case "Bool"   ⇒ "&::skill::fieldTypes::BoolType"
      case n        ⇒ "&::skill::fieldTypes::" + n.capitalize
    }
    case t : ClassDef  ⇒ s"state->${name(t)}"

    case t : ArrayType ⇒ s"new ::skill::fieldTypes::ArrayType(${mapFieldDefinition(t.baseType)})"
    case t : ListType  ⇒ s"new ::skill::fieldTypes::ListType(${mapFieldDefinition(t.baseType)})"
    case t : SetType   ⇒ s"new ::skill::fieldTypes::SetType(${mapFieldDefinition(t.baseType)})"
    case t : MapType   ⇒ s"new ::skill::fieldTypes::MapType(${mapFieldDefinition(t.keyType)}, ${mapFieldDefinition(t.valueType)})"

    case _             ⇒ "???"
  }
}
