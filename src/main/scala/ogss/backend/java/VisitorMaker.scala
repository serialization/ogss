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
package ogss.backend.java

trait VisitorMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    if (visited.size > 0) {
      val out = files.open(s"Visitor.java")
      //package & imports
      out.write(s"""package ${packageName};

/**
 * Base class of a distributed dispatching function ranging over specified types
 * implemented by the visitor pattern.
 * 
 * @author Timm Felden
 *
 * @param <_R>
 *            the result type
 * @param <_A>
 *            the argument type
 * @param <_E>
 *            the type of throws exception; use RuntimeException for nothrow
 */
public abstract class Visitor<_R, _A, _E extends Exception> {${
        (for (t ← IR if visited.contains(t.getName)) yield s"""
    public abstract _R visit(${mapType(t)} self, _A arg) throws _E;""").mkString
      }
}
""")

      out.close()
    }
  }
}
