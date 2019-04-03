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

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap


trait SubTypesMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← IR) {
      val out = files.open(s"Sub$$${name(t)}.java")

      // package
      out.write(s"""package ${this.packageName};

import ogss.common.java.internal.Pool;
import ogss.common.java.internal.NamedObj;

${
        suppressWarnings
      }public final class Sub$$${name(t)} extends ${name(t)} implements NamedObj {
    transient public final Pool<?> τp;

     public Sub$$${name(t)}(Pool<?> τp, int ID) {
        super(ID);
        this.τp = τp;
    }

    @Override
    public int stid() {
        return -1;
    }

    @Override
    public Pool<?> τp() {
        return τp;
    }
}""");
      out.close()
    }
  }
}
