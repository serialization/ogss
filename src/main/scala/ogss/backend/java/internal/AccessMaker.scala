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

import ogss.backend.java.AbstractBackEnd
import ogss.io.PrintWriter
import ogss.oil.ClassDef

trait AccessMaker extends AbstractBackEnd {
  final def makePools(out : PrintWriter) {

    for (t ← flatIR) {
      val isBasePool = (null == t.superType)
      val nameT = name(t)
      val subT = s"${this.packageName}.Sub$$$nameT"
      val typeT = mapType(t)
      val accessT = access(t)

      val isSingleton = !t.attrs.collect { case r if "singleton".equals(r.name) ⇒ r }.isEmpty

      // find all fields that belong to the projected version, but use the unprojected variant
      val fields = t.fields

      out.write(s"""
${
        comment(t)
      }public static final class $accessT extends Pool<$typeT>${
        if (isSingleton) s"""
        implements ogss.common.java.internal.SingletonPool<$typeT>"""
        else ""
      } {

    /**
     * Can only be constructed by the SkillFile in this package.
     */
    $accessT(int idx${
        if (isBasePool) ""
        else s", ${access(t.superType)} sup"
      }) {
        super(idx, "${ogssname(t)}", ${
        if (isBasePool) "null"
        else "sup"
      }, ${fields.count(_.isTransient)});
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
        case $i: return new ${knownField(f)}((FieldType)SIFA[${f.`type`.stid}], ${
              if (f.isTransient) ""
              else "nextFID, "
            }this);"""
          }.mkString
        }
        default: return null;
        }
    }"""
      }

    @Override
    protected void allocateInstances() {${
        if(isSingleton)
s"""
        int i = bpo;

        if (staticDataInstances > 1) {
            throw new OGSSException("class "+name+" is a singleton, but has "+staticDataInstances+" instances");
        }
        if (1 == staticDataInstances) {
            // create a new object, claiming that there is none in data
            staticDataInstances = 0;
            $typeT v = get();
            // instance is not a new object and make the object an object obtained from file
            this.newObjects.clear();
            staticDataInstances = 1;

            data[i] = v;
            setID(v, i + 1);
        }
    }

    /**
     * @return a new $nameT instance with default field values
     */
    @Override
    public $typeT make() {
        if(0 != size()) {
            throw new OGSSException("class "+name+" is a singleton with "+size()+" instance; use get to access the instance.");
        }

        $typeT rval = new $typeT(0);
        add(rval);
        return rval;
    }

    private $typeT instance;

    @Override
    public synchronized $typeT get() {
        if(null == instance){
            instance = make();
        }
        return instance;
    }
"""
        else s"""
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
        if (t.subTypes.isEmpty) ""
        else s"""
    @Override
    protected String nameSub(int id) {
        switch (id) {${
          t.subTypes.zipWithIndex.map {
            case (s, i) ⇒ s"""
        case $i: return "${ogssname(s)}";"""
          }.mkString
        }
        default: return null;
        }
    }"""}

    @Override
    protected Pool<? extends ${mapType(t)}> makeSub(int id, int idx) {
        switch (id) {${
          t.subTypes.collect { case t : ClassDef ⇒ t }.zipWithIndex.map {
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
