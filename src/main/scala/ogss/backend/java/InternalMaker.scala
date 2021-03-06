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

import ogss.backend.java.internal.AccessMaker
import ogss.backend.java.internal.FieldDeclarationMaker
import ogss.io.PrintWriter
import ogss.oil.EnumDef

/**
 * Create an internal class instead of a package. That way, the fucked-up Java
 * visibility model can be exploited to access fields directly.
 * As a side-effect, types that resided in that package must be inner classes of
 * internal.
 */
trait InternalMaker extends AbstractBackEnd
  with AccessMaker
  with FieldDeclarationMaker {

  abstract override def make {
    super.make

    val out = files.open(s"internal.java")

    out.write(s"""package ${this.packageName};

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.HashSet;

import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.AnyRefType;
import ogss.common.java.internal.EnumPool;
import ogss.common.java.internal.EnumProxy;
import ogss.common.java.internal.FieldDeclaration;
import ogss.common.java.internal.FieldType;
import ogss.common.java.internal.HullType;
import ogss.common.java.internal.InterfacePool;
import ogss.common.java.internal.Obj;
import ogss.common.java.internal.Pool;
import ogss.common.java.internal.StringPool;
import ogss.common.java.internal.SubPool;
import ogss.common.java.internal.UnrootedInterfacePool;
import ogss.common.java.internal.fieldDeclarations.AutoField;
import ogss.common.java.internal.fieldDeclarations.KnownField;
import ogss.common.java.internal.fieldTypes.*;
import ogss.common.java.restrictions.TypeRestriction;
import ogss.common.jvm.streams.BufferedOutStream;
import ogss.common.jvm.streams.FileInputStream;
import ogss.common.jvm.streams.MappedInStream;


${
      suppressWarnings
    }public final class internal {
    private internal() {}
""")

    makePB(out);
    makePools(out);
    makeBuilders(out);
    makeFields(out);

    out.write("""
}
""");

    out.close
  }

  final def makePB(out : PrintWriter) {
    out.write(s"""
    public static final class PB extends ogss.common.java.internal.PoolBuilder {
        PB() {
            super(${flatTC.byName.size});
        }

        @Override
        protected String[] literals() {
            return new String[]${
      allStrings.map(_.ogss).mkString("{\"", "\", \"", "\"}")
    };
        }

        @Override
        protected int kcc(int id) {
            switch (id) {${
      // predefine known containers
      flatTC.containers.zipWithIndex.map {
        case (ct, i) ⇒
          f"""
            case $i: return 0x${ct.kcc}%08x; // ${ogssname(ct)}"""
      }.mkString
    }
            default: return -1;
            }
        }

        @Override
        protected String name(int id) {
            ${
      if (IR.isEmpty) "return null;"
      else IR.filter(_.superType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
            case $i: return "${ogssname(t)}";"""
      }.mkString("switch (id) {", "", """
            default: return null;
            }""")
    }
        }

        @Override
        protected Pool<?> make(int id, int idx) {
            ${
      if (IR.isEmpty) "return null;"
      else IR.filter(_.superType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
            case $i: return new ${access(t)}(idx);"""
      }.mkString("""switch (id) {""", "", """
            default: return null;
            }""")
    }
        }

        @Override
        protected String enumName(int id) {
            ${
      if (enums.isEmpty) "return null;"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
            case $i: return "${t.name.ogss}";"""
      }.mkString("switch (id) {", "", """
            default: return null;
            }""")
    }
        }

        @Override
        protected Enum<?>[] enumMake(int id) {
            ${
      if (enums.isEmpty) "return null;"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
            case $i: return $packagePrefix${name(t)}.values();"""
      }.mkString("""switch (id) {""", "", """
            default: return null;
            }""")
    }
        }
    }
""")
  }

  final def makeBuilders(out : PrintWriter) {

    for (t ← IR) {
      val realT = projected(ogssname(t))
      if (null == realT.superType || !realT.fields.isEmpty) {

        val nameT = name(t)
        val typeT = mapType(t)

        // find all fields that belong to the projected version, but use the unprojected variant
        val flatIRFieldNames = flatIR.find(_.name == t.name).get.fields.map(ogssname).toSet
        val fields = allFields(t).filter(f ⇒ flatIRFieldNames.contains(ogssname(f)))
        val projectedField = flatIR.find(_.name == t.name).get.fields.map {
          case f ⇒ fields.find(ogssname(_).equals(ogssname(f))).get -> f
        }.toMap

        val isSingleton = !t.attrs.collect { case r if "singleton".equals(r.name) ⇒ r }.isEmpty

        if (!isSingleton)
          out.write(s"""
    /**
     * Builder for new $nameT instances.
     *
     * @author Timm Felden
     */
    public static class ${builder(t)}<T extends $typeT, B extends ${builder(t)}<T, B>> extends ${
          if (null == t.superType) s"ogss.common.java.internal.Builder<T>"
          else s"${builder(t.superType)}<T, B>"
        } {

        protected ${builder(t)}(Pool<T> pool, T self) {
            super(pool, self);
        }${
          (for (f ← fields)
            yield s"""${
              if(f.`type`.isInstanceOf[EnumDef])s"""

        public final B ${name(f)}($packagePrefix${name(f.`type`)} ${name(f)}) {
            self.${name(f)} = ((OGFile)p.owner()).${name(f.`type`)}.proxy(${name(f)});
            return (B)this;
        }""" else ""
            }

        public final B ${name(f)}(${mapType(f.`type`)} ${name(f)}) {
            self.${name(f)} = ${name(f)};
            return (B)this;
        }""").mkString
        }
    }
""")
      }
    }
  }
}
