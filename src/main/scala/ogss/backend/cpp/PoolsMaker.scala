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
import ogss.oil.SetType
import ogss.oil.Field
import ogss.oil.ListType
import ogss.oil.Type
import ogss.oil.MapType
import ogss.oil.BuiltinType
import ogss.oil.ClassDef
import ogss.oil.ArrayType

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
      IR.filter(_.getSuperType == null).map(base ⇒ s"""
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
      val fields = t.getFields.asScala

      // create the builder, if required
      if (null == t.getSuperType || !fields.isEmpty)
        out.write(s"""
    struct ${access(t)};

    template<class T, class B>
    struct ${builder(t)} : public ${
          if (null == t.getSuperType) s"::ogss::api::Builder"
          else s"${builder(t.getSuperType)}<T, B>"
        } {${
          (for (f ← fields)
            yield s"""

        B* ${name(f)}(${mapType(f.getType)} ${name(f)}) {
            ((T)self)->${name(f)} = ${name(f)};
            return (B*)this;
        }""").mkString
        }
    protected:
        ${builder(t)}(::ogss::api::Object* self) : ${
          if (null == t.getSuperType) s"::ogss::api::Builder"
          else s"${builder(t.getSuperType)}<T, B>"
        } (self) {}
    };
    template<class T>
    struct ${builder(t)}_IMPL final : public ${builder(t)}<T, ${builder(t)}_IMPL<T> > {
    protected:
        ${builder(t)}_IMPL(::ogss::api::Object* self) : ${builder(t)}<T, ${builder(t)}_IMPL<T> > (self) {}
        friend struct ${access(t)};
    };
""")

      // create the very pool
      out.write(s"""
    struct ${access(t)} final : public ::ogss::internal::Pool<$typeName> {
        ${access(t)}(::ogss::TypeID typeID${
        if (t.getSuperType == null) ""
        else s", AbstractStoragePool *superPool"
      }, ::std::unordered_set<::ogss::restrictions::TypeRestriction *> *restrictions)
                : ::ogss::internal::Pool<$typeName>(typeID, ${
        if (t.getSuperType == null) "nullptr"
        else s"superPool"
      }, ${skName(t.getName)}, restrictions, ${fields.count(_.getIsTransient)}) { }

    ${builder(t)}_IMPL<$typeT>* build() {
        return new ${builder(t)}_IMPL<$typeT>(make());
    }

    protected:
${
        if (fields.isEmpty) ""
        else s"""
        ::ogss::api::String KFN(int id) override {
            switch (id) {${
          fields.zipWithIndex.map {
            case (f, i) ⇒ s"""
                case $i: return ${skName(f.getName)};"""
          }.mkString
        }
                default: return nullptr;
            }
        }

        ::ogss::internal::FieldDeclaration *KFC(int id, FieldType **SIFA, ::ogss::TypeID nextFID) override {
            switch (id) {${
          fields.zipWithIndex.map {
            case (f, i) ⇒ s"""
                case $i: return new ${knownField(f)}(SIFA[${f.getType.getStid}], ${
              if (f.getIsTransient) ""
              else "nextFID, "
            }this);"""
          }.mkString
        }
                default: return nullptr;
            }
        }"""
      }${
        if (t.getSubTypes.isEmpty) ""
        else s"""

        String* nameSub() override {
            switch (id) {${
          t.getSubTypes.asScala.zipWithIndex.map {
            case (s, i) ⇒ s"""
            case $i: return "${ogssname(s)}";"""
          }.mkString
        }
            default: return null;
            }
        }
        ::ogss::internal::AbstractPool* makeSub(int id, ::ogss::TypeID idx) override {
            switch (id) {${
          t.getSubTypes.asScala.collect { case t : ClassDef ⇒ t }.zipWithIndex.map {
            case (s, i) ⇒ s"""
            case $i: return new ${access(s)}(idx, this);"""
          }.mkString
        }
            default: return null;
            }
        }
"""
      }
        ::ogss::internal::AbstractPool *makeSub(::ogss::TypeID typeID, ::ogss::api::String name,
                                     ::std::unordered_set<::ogss::restrictions::TypeRestriction *> *restrictions) {
            return new ::ogss::internal::SubPool<${typeName}_UnknownSubType>(
                    typeID, this, name, restrictions);
        }
    };""")
    }

    out.write(s"""
}${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")
    out.close()
  }

  /**
   * escaped name for field classes
   */
  private final def clsName(f : Field) : String = escaped("Cls" + camel(f.getName))

  protected def mapFieldDefinition(t : Type) : String = t match {
    case t : BuiltinType ⇒ t.getName.getOgss match {
      case "String" ⇒ "state->strings"
      case "AnyRef" ⇒ "state->getAnnotationType()"
      case "Bool"   ⇒ "&::skill::fieldTypes::BoolType"
      case n        ⇒ "&::skill::fieldTypes::" + n.capitalize
    }
    case t : ClassDef  ⇒ s"state->${name(t)}"

    case t : ArrayType ⇒ s"new ::skill::fieldTypes::ArrayType(${mapFieldDefinition(t.getBaseType)})"
    case t : ListType  ⇒ s"new ::skill::fieldTypes::ListType(${mapFieldDefinition(t.getBaseType)})"
    case t : SetType   ⇒ s"new ::skill::fieldTypes::SetType(${mapFieldDefinition(t.getBaseType)})"
    case t : MapType   ⇒ s"new ::skill::fieldTypes::MapType(${mapFieldDefinition(t.getKeyType)}, ${mapFieldDefinition(t.getValueType)})"

    case _             ⇒ "???"
  }
}
