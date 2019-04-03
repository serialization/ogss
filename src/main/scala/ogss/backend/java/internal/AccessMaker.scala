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
package ogss.backend.java.internal

import scala.collection.JavaConverters._
import ogss.backend.java.AbstractBackEnd
import ogss.io.PrintWriter
import ogss.oil.ClassDef

trait AccessMaker extends AbstractBackEnd {
  final def makePools(out : PrintWriter) {

    for (t ← flatIR) {
      val isBasePool = (null == t.getSuperType)
      val nameT = name(t)
      val subT = s"${this.packageName}.Sub$$$nameT"
      val typeT = mapType(t)
      val accessT = access(t)

      // find all fields that belong to the projected version, but use the unprojected variant
      val fields = t.getFields.asScala

      out.write(s"""
${
        comment(t)
      }public static final class $accessT extends Pool<$typeT> {

    /**
     * Can only be constructed by the SkillFile in this package.
     */
    $accessT(int idx${
        if (isBasePool) ""
        else s", ${access(t.getSuperType)} sup"
      }) {
        super(idx, "${ogssname(t)}", ${
        if (isBasePool) "null"
        else "sup"
      }, ${fields.count(_.getIsTransient)});
    }${
        // export data for sub pools
        if (isBasePool) s"""

    final Obj[] data() {
        return data;
    }"""
        else ""
      }${
        if (fields.isEmpty) ""
        else s"""

    @Override
    protected String KFN(int id) {
        switch (id) {${
          fields.zipWithIndex.map {
            case (f, i) ⇒ s"""
        case $i: return "${ogssname(f)}";"""
          }.mkString
        }
        default: return null;
        }
    }

    @Override
    protected FieldDeclaration<?, $typeT> KFC(int id, FieldType<?>[] SIFA, int nextFID) {
        switch (id) {${
          fields.zipWithIndex.map {
            case (f, i) ⇒ s"""
        case $i: return new ${knownField(f)}((FieldType)SIFA[${f.getType.getStid}], ${
              if (f.getIsTransient) ""
              else "nextFID, "
            }this);"""
          }.mkString
        }
        default: return null;
        }
    }"""
      }

    @Override
    protected void allocateInstances() {
        int i = bpo, j;
        final int high = i + staticDataInstances;
        while (i < high) {
            data[i] = new $typeT(j = (i + 1));
            i = j;
        }
    }

    /**
     * @return a new $nameT instance with default field values
     */
    @Override
    public $typeT make() {
        $typeT rval = new $typeT(0);
        add(rval);
        return rval;
    }

    public ${builder(t)}<$typeT, ?> build() {
        return new ${builder(t)}<>(this, new $typeT(0));
    }${
        if (t.getSubTypes.isEmpty) ""
        else s"""
    @Override
    protected String nameSub(int id) {
        switch (id) {${
          t.getSubTypes.asScala.zipWithIndex.map {
            case (s, i) ⇒ s"""
        case $i: return "${ogssname(s)}";"""
          }.mkString
        }
        default: return null;
        }
    }

    @Override
    protected Pool<? extends ${mapType(t)}> makeSub(int id, int idx) {
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

    @Override
    protected Pool<? extends ${mapType(t)}> makeSub(int index, String name) {
        return new SubPool<>(index, name, ${this.packageName}.Sub$$${name(t)}.class, this);
    }
}""")
    }
  }
}
