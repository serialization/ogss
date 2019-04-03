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
/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.java

import java.io.PrintWriter
import scala.collection.JavaConversions._
import scala.collection.mutable.HashSet

trait InterfacesMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← interfaces) {
      val out = files.open(s"${name(t)}.java")

      //package
      out.write(s"""package ${this.packageName};

import ogss.common.java.api.FieldDeclaration;
import ogss.common.java.internal.Pool;
""")

      val packageName =
        if (this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.') + 1)
        else this.packageName;

      val fields = allFields(t)

      out.write(s"""
${
        comment(t)
      }${
        suppressWarnings
      }public interface ${name(t)} ${
        if (t.getSuperInterfaces.isEmpty) ""
        else
          t.getSuperInterfaces.map(name(_)).mkString("extends ", ", ", "")
      } {

    /**
     * cast to concrete type
     */${
        if (!t.getSuperInterfaces.isEmpty()) """
    @Override"""
        else ""
      }
    public default ${mapType(t.getSuperType)} self() {
        return (${mapType(t.getSuperType)}) this;
    }
${
        ///////////////////////
        // getters & setters //
        ///////////////////////
        (
          for (f ← fields)
            yield s"""
    ${comment(f)}public ${mapType(f.getType())} ${getter(f)}();

    ${comment(f)}public void ${setter(f)}(${mapType(f.getType())} ${name(f)});
"""

        ).mkString
      }
}
""");
      out.close()
    }
  }
}
