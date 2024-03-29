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

trait EnumMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    if (!enums.isEmpty) {
      val out = files.open("src/enums.h")

      for (t ← enums) {
        // package
        out.write(s"""
${comment(t)}enum class ${name(t)} {
  ${t.values
          .map(id ⇒ comment(id) + escaped(camel(id.name)))
          .zipWithIndex
          .map { case (s, i) ⇒ s"$s = $i" }
          .mkString("", ",\n  ", "")}
};
""");
      }

      out.close()
    }
  }
}
