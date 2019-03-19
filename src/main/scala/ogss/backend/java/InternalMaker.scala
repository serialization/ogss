/*   ____  _____________                                                      *\
**  / __ \/ ___/ __/ __/  The OGSS Code Generator                             **
** / /_/ / (_\_\ \_\ \    (c) 2013-19 University of Stuttgart                 **
** \____/\___/___/___/    see LICENSE                                         **
\*                                                                            */

package ogss.backend.java

import scala.collection.JavaConverters._

import scala.collection.mutable.ArrayBuffer
import ogss.backend.java.internal.FieldDeclarationMaker
import ogss.backend.java.internal.AccessMaker
import ogss.io.PrintWriter
import ogss.oil.Type

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

import ogss.common.java.api.SkillException;
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
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.MappedInStream;


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
            super(${types.getByName.size});
        }

        @Override
        protected int kcc(int id) {
            switch (id) {${
      // predefine known containers
      flatTC.getContainers.asScala.zipWithIndex.map {
        case (ct, i) ⇒
          f"""
            case $i: return 0x${ct.getKcc()}%08x; // ${ogssname(ct)}"""
      }.mkString
    }
            default: return -1;
            }
        }

        @Override
        protected String name(int id) {
            ${
      if (IR.isEmpty) "return null;"
      else IR.filter(_.getSuperType == null).zipWithIndex.map {
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
      else IR.filter(_.getSuperType == null).zipWithIndex.map {
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
            case $i: return "${t}";"""
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
      if (null == realT.getSuperType || !realT.getFields.isEmpty()) {

        val nameT = name(t)
        val typeT = mapType(t)

        // find all fields that belong to the projected version, but use the unprojected variant
        val flatIRFieldNames = flatIR.find(_.getName == t.getName).get.getFields.asScala.map(ogssname).toSet
        val fields = allFields(t).filter(f ⇒ flatIRFieldNames.contains(ogssname(f)))
        val projectedField = flatIR.find(_.getName == t.getName).get.getFields.asScala.map {
          case f ⇒ fields.find(ogssname(_).equals(ogssname(f))).get -> f
        }.toMap

        out.write(s"""
    /**
     * Builder for new $nameT instances.
     *
     * @author Timm Felden
     */
    public static class ${builder(t)}<T extends $typeT, B extends ${builder(t)}<T, B>> extends ${
          if (null == t.getSuperType) s"ogss.common.java.internal.Builder<T>"
          else s"${builder(t.getSuperType)}<T, B>"
        } {

        protected ${builder(t)}(Pool<T> pool, T self) {
            super(pool, self);
        }${
          (for (f ← fields)
            yield s"""

        public final B ${name(f)}(${mapType(f.getType)} ${name(f)}) {
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