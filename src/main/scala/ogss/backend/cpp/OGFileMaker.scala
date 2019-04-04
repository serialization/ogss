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

#include "Pools.h"

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
    }

            /**
             * Reads a binary OGSS file and turns it into an instance of this class.
             */
            static File *open(const std::string &path, int mode = ::ogss::api::ReadMode::read | ::ogss::api::WriteMode::write);

        private:

            //! note: consumes init
            explicit File(::ogss::internal::StateInitializer* init);
        };${
      // create visitor implementation
      if (visited.size > 0) s"""

        // visitor implementation
        struct Visitor {${
        (for (t ← IR if visited(t.getName)) yield s"""
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

#include <ogss/internal/PoolBuilder.h>
#include <ogss/internal/StateInitializer.h>

#include "File.h"
#include "StringKeeper.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace internal {
        struct PB final : public ::ogss::internal::PoolBuilder {
            PB() : ::ogss::internal::PoolBuilder(${types.getByName.size}) {}

            void initialize(::ogss::internal::StringPool *pool) const final {${
      (for (s ← allStrings; name = skName(s)) yield s"""
                pool->addLiteral($name);""").mkString
    }
            }

            uint32_t kcc(int id) const final {
                ${
      if (types.getContainers.isEmpty) "return -1u;"
      else types.getContainers.asScala.zipWithIndex.map {
        case (ct, i) ⇒ f"""
                    case $i: return 0x${ct.getKcc()}%08x; // ${ogssname(ct)}"""
      }.mkString("""switch (id) {""", "", """
                    default: return -1;
                }""")
    }
            }

            ogss::fieldTypes::HullType *makeContainer(uint32_t kcc, ::ogss::TypeID tid,
                                                      ogss::fieldTypes::FieldType *kb1,
                                                      ogss::fieldTypes::FieldType *kb2) const final {
                ${
      if (types.getContainers.isEmpty) "return nullptr;"
      else types.getContainers.asScala.map {
        case ct ⇒ s"""
                    case ${ct.getKcc()}: SK_TODO;"""
      }.mkString.mkString("""switch (kcc) {""", "", """
                    default: return nullptr;
                }""")
    }
            }

            ogss::api::String name(int id) const final {
                ${
      if (IR.isEmpty) "return nullptr;"
      else IR.filter(_.getSuperType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: return ${skName(t.getName)};"""
      }.mkString.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }

            ogss::internal::AbstractPool* make(int id, ::ogss::TypeID index) const final {
                ${
      if (IR.isEmpty) "return nullptr;"
      else IR.filter(_.getSuperType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: return new ${access(t)}(index, nullptr);"""
      }.mkString.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }

            ogss::api::String enumName(int id) const final {
                ${
      if (enums.isEmpty) "return nullptr;"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
                    case $i: return ${skName(t.getName)};"""
      }.mkString.mkString("""switch (id) {""", "", """
                    default: return nullptr;
                }""")
    }
            }
        };
    }
${packageParts.map(_ ⇒ "}").mkString}

$packageName::api::File *$packageName::api::File::open(const std::string &path, int mode) {
    $packageName::internal::PB pb;
    return new $packageName::api::File(::ogss::internal::StateInitializer::make(path, pb, mode));
}

$packageName::api::File::File(::ogss::internal::StateInitializer *init)
        : ::ogss::api::File(init)${
      (for (t ← IR)
        yield s""",
        ${name(t)}((internal::${access(t)} *) init->SIFA[${t.getStid}])""").mkString
    } {${
      (for (t ← IR)
        yield s"""
    static_assert(offsetof($packageName::api::File, ${name(t)}) == offsetof(::ogss::api::File, SIFA[${t.getStid}-10]), "your compiler chose an ill-formed object layout");""").mkString
    }
}
""")

    out.close()
  }
}
