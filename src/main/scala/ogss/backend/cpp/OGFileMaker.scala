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
import ogss.oil.EnumDef
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.util.IRUtils

trait OGFileMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    makeHeader
    makeSource
  }
  private def makeHeader {
    val out = files.open("File.h")

    out.write(s"""${beginGuard("file")}
#include <ogss/api/File.h>
#include <ogss/fieldTypes/ArrayType.h>
#include <ogss/fieldTypes/ListType.h>
#include <ogss/fieldTypes/SetType.h>
#include <ogss/fieldTypes/MapType.h>
#include <ogss/internal/EnumPool.h>

${
      if (enums.isEmpty) ""
      else """#include "enums.h"
"""
    }#include "Pools.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace api {
        /**
         * An OGSS file that corresponds to your specification. Have fun!
         *
         * @author Timm Felden
         */
        struct File : public ::ogss::api::File {
${
      (for (t ← IR) yield s"""
            internal::${access(t)} *const ${name(t)};""").mkString
    }${
      (for (t ← types.containers) yield s"""
            ${containerType(t)} const ${containerName(t)};""").mkString
    }${
      (for (t ← types.enums) yield s"""
            ::ogss::internal::EnumPool<$packageName::${name(t)}>* const ${name(t)};""").mkString
    }

            /**
             * Reads a binary OGSS file and turns it into an instance of this class.
             */
            static File *open(const std::string &path, uint8_t mode = ::ogss::api::ReadMode::read | ::ogss::api::WriteMode::write);

        private:

            //! note: consumes init
            explicit File(::ogss::internal::StateInitializer* init);
        };${
      // create visitor implementation
      if (visited.size > 0) s"""

        // visitor implementation
        struct Visitor {${
        (for (t ← IR if visited(t.name)) yield s"""
            virtual void visit(${name(t)} *node) {}""").mkString
      }
        };
"""
      else ""
    }
}${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")

    out.close()
  }

  private def makeSource {
    val out = files.open("File.cpp")

    out.write(s"""
#include <cstddef>

#include <ogss/fieldTypes/ArrayType.h>
#include <ogss/fieldTypes/ListType.h>
#include <ogss/fieldTypes/SetType.h>
#include <ogss/fieldTypes/MapType.h>

#include <ogss/internal/EnumPool.h>
#include <ogss/internal/PoolBuilder.h>
#include <ogss/internal/StateInitializer.h>

#include "enums.h"
#include "File.h"
#include "StringKeeper.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace internal {
        StringKeeper SK;

        struct PB final : public ::ogss::internal::PoolBuilder {
            PB() : ::ogss::internal::PoolBuilder(${types.byName.size}) {}

            const ::ogss::internal::AbstractStringKeeper *getSK() const final {${
      (for ((n, i) ← allStrings.zipWithIndex) yield s"""
                static_assert(offsetof($packageName::internal::StringKeeper, ${escaped(adaStyle(n))}) == offsetof(::ogss::internal::AbstractStringKeeper, strings[$i]), "your compiler chose an ill-formed object layout");""").mkString
    }
                return &SK;
            }

            uint32_t kcc(int id) const final {
                ${
      if (types.containers.isEmpty) "return -1u;"
      else types.containers.zipWithIndex.map {
        case (ct, i) ⇒ f"""
                    case $i: return 0x${ct.kcc}%08x; // ${ogssname(ct)}"""
      }.mkString("""switch (id) {""", "", """
                    default: return -1;
                }""")
    }
            }

            ogss::fieldTypes::HullType *makeContainer(uint32_t kcc, ::ogss::TypeID tid,
                                                      ogss::fieldTypes::FieldType *kb1,
                                                      ogss::fieldTypes::FieldType *kb2) const final {
                ogss::fieldTypes::HullType * r;
                ${
      if (types.containers.isEmpty) "return nullptr;"
      else types.containers.map {
        case ct ⇒ f"""
                    case 0x${ct.kcc}%08x: r = ${
          ct match {
            case ct : ArrayType ⇒ s"new ogss::fieldTypes::ArrayType<${mapType(ct.baseType)}>(tid, kcc, kb1)"
            case ct : ListType  ⇒ s"new ogss::fieldTypes::ListType<${mapType(ct.baseType)}>(tid, kcc, kb1)"
            case ct : SetType   ⇒ s"new ogss::fieldTypes::SetType<${mapType(ct.baseType)}>(tid, kcc, kb1)"
            case ct : MapType   ⇒ s"new ogss::fieldTypes::MapType<${mapType(ct.keyType)}, ${mapType(ct.valueType)}>(tid, kcc, kb1, kb2)"
          }
        }; break;"""
      }.mkString("""switch (kcc) {""", "", """
                    default: r = nullptr;
                }""")
    }
                return r;
            }

            ogss::api::String name(int id) const final {
                ${
      if (IR.isEmpty) "return nullptr;"
      else IR.filter(_.superType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: return ${skName(t.name)};"""
      }.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }

            ogss::internal::AbstractPool* make(int id, ::ogss::TypeID index) const final {
                ${
      if (IR.isEmpty) "return nullptr;"
      else IR.filter(_.superType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: return new ${access(t)}(index, nullptr);"""
      }.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }

            ogss::api::String enumName(int id) const final {
                ${
      if (enums.isEmpty) "return nullptr;"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: return ${skName(t.name)};"""
      }.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }

            ogss::internal::AbstractEnumPool *enumMake(
                    int id, ogss::TypeID index, const std::vector<ogss::api::String> &foundValues) const final {
                ${
      if (enums.isEmpty) "return nullptr;"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: {
                        ogss::api::String names[] = {${t.values.sortWith(IRUtils.ogssLess).map(v ⇒ skName(v.name)).mkString(", ")}};
                        return new ::ogss::internal::EnumPool<$packageName::${name(t)}>(index, ${skName(t.name)}, foundValues, names, ${t.values.size});
                    }"""
      }.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }
        };
    }
${packageParts.map(_ ⇒ "}").mkString}

$packageName::api::File *$packageName::api::File::open(const std::string &path, uint8_t mode) {
    $packageName::internal::PB pb;
    return new $packageName::api::File(::ogss::internal::StateInitializer::make(path, pb, mode));
}

$packageName::api::File::File(::ogss::internal::StateInitializer *init)
        : ::ogss::api::File(init)${
      (for (t ← IR)
        yield s""",
        ${name(t)}((internal::${access(t)} *) init->SIFA[${t.stid}])""").mkString
    }${
      (for (t ← types.containers)
        yield s""",
        ${containerName(t)}((${containerType(t)}) init->SIFA[${t.stid}])""").mkString
    }${
      (for (t ← types.enums)
        yield s""",
        ${name(t)}((::ogss::internal::EnumPool<$packageName::${name(t)}> *) init->SIFA[${t.stid}])""").mkString
    } {${
      (for (t ← IR)
        yield s"""
    static_assert(offsetof($packageName::api::File, ${name(t)}) == offsetof(::ogss::api::File, SIFA[${t.stid}-10]), "your compiler chose an ill-formed object layout");""").mkString
    }
    delete init;${
      // initialize enums
      val enums = IR.flatMap(_.fields.filter(_.`type`.isInstanceOf[EnumDef]))
      if (enums.isEmpty) ""
      else enums.map(f ⇒ s"""
    for (auto &x : ${name(f.owner)}->all()) {
        if (nullptr == x.${name(f)})
            x.${name(f)} = ${name(f.`type`)}->get(${defaultValue(f)});
    }""").mkString
    }
}
""")

    out.close()
  }

  def containerType(t : Type) : String = t match {
    case t : ArrayType ⇒ s"::ogss::fieldTypes::ArrayType<${mapType(t.baseType)}>*"
    case t : ListType  ⇒ s"::ogss::fieldTypes::ListType<${mapType(t.baseType)}>*"
    case t : SetType   ⇒ s"::ogss::fieldTypes::SetType<${mapType(t.baseType)}>*"
    case t : MapType   ⇒ s"::ogss::fieldTypes::MapType<${mapType(t.keyType)}, ${mapType(t.valueType)}>*"
  }
  def containerName(t : Type) : String = t match {
    case t : ArrayType ⇒ "Array_" + containerName(t.baseType)
    case t : ListType  ⇒ "List_" + containerName(t.baseType)
    case t : SetType   ⇒ "Set_" + containerName(t.baseType)
    case t : MapType   ⇒ s"Map_${containerName(t.keyType)}_${containerName(t.valueType)}"

    case t             ⇒ capital(t.name)
  }
}
