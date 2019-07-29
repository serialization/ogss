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
package ogss.backend.doxygen

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

import ogss.oil.ClassDef
import ogss.oil.Field
import ogss.oil.WithInheritance
import scala.collection.mutable.ListBuffer

/**
 * creates header and implementation for all class and interface definitions
 *
 * @author Timm Felden
 */
trait TypesMaker extends AbstractBackEnd {

  abstract override def make {
    super.make

    // one header per base type
    for (t ← types.classes ++ types.interfaces) {
      val out = files.open(s"src/${name(t)}.h")

      //includes package
      out.write(s"""
// user type doxygen documentation
#include <string>
#include <list>
#include <set>
#include <map>
#include <stdint.h>

${
        comment(t)
      }class ${name(t)} ${
        val ts = new ListBuffer[WithInheritance]() ++ t.superInterfaces
        if (null != t.superType)
          ts.prepend(t.superType)

        if (ts.isEmpty) ""
        else ts.map(name).mkString(": virtual protected ", ", virtual protected ", "")
      }{
  public:${
        (
          for (f ← t.fields)
            yield s"""

${
            comment(f)
          }${mapType(f.`type`)} ${name(f)};"""
        ).mkString
      }
};
""")

      out.close()
    }
  }
}
