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

/**
 * a string keeper takes care of all known strings
 *
 * @author Timm Felden
 */
trait StringKeeperMaker extends AbstractBackEnd {

  abstract override def make {
    super.make

    val out = files.open(s"StringKeeper.h")

    //includes package
    out.write(s"""${beginGuard("String_Keeper")}
#include <ogss/api/String.h>
#include <ogss/internal/AbstractStringKeeper.h>

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace internal {
        struct StringKeeper : public ::ogss::internal::AbstractStringKeeper {
            StringKeeper() : AbstractStringKeeper(${allStrings.size}) {}${
      allStrings.map(n ⇒ s"""
            const ::ogss::api::String ${escaped(adaStyle(n))} = new std::string(u8"${n.ogss}");""").mkString
    }
        };
        extern StringKeeper SK;
    }
${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")
    out.close;
  }
}
