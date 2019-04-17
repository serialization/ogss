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

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap

trait EnumMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    val out = files.open("enums.h")
    out.write(s"""${beginGuard("ENUMS")}
#include <ogss/common.h>

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}""")

    for (t ← enums) {
      // package
      out.write(s"""
    ${comment(t)}enum class ${name(t)} : ogss::EnumBase {
        UNKNOWN = (ogss::EnumBase)-1,
        ${
        // TODO comments!
        t.getValues.map(id ⇒ escaped(camel(id.getName))).zipWithIndex.map{case (s, i) ⇒ s"$s = $i"}.mkString("", ",\n        ", "")
      }
    };
""");
    }
    out.write(s"""
${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")
    out.close()
  }
}
