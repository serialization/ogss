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

import ogss.oil.{EnumDef, Field, FieldLike, InterfaceDef}
import ogss.util.IRUtils

trait TypesMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← IR) {
      val out = files.open(s"${name(t)}.java")

      // in java, we have to implement customizations from all super interfaces as well
      val customizations = {
        var cs = IRUtils.allCustoms(t).filter(_.language.equals("java"))
        if (null != t.superType) {
          cs --= IRUtils
            .allCustoms(t.superType)
            .filter(_.language.equals("java"))
        }
        cs
      }.toSet.toArray

      // package
      out.write(s"""package ${this.packageName};

import ogss.common.java.internal.EnumProxy;
${customizations
        .flatMap(
          _.options.find(_.name.equals("import")).toSeq.flatMap(_.arguments)
        )
        .map(i ⇒ s"import $i;\n")
        .mkString}
""")

      val fields = allFields(t)

      out.write(s"""
${comment(t)}${suppressWarnings}public class ${name(t)} extends ${if (null != t.superType) {
        name(t.superType)
      } else { "ogss.common.java.internal.Obj" }}${if (t.superInterfaces.isEmpty)
        ""
      else
        t.superInterfaces.map(name(_)).mkString(" implements ", ", ", "")} {

    /**
     * Create a new unmanaged ${ogssname(t)}. Allocation of objects without using the
     * access factory method is discouraged.
     */
    public ${name(t)}() {
        super(0);
    }

    /**
     * Used for internal construction only!
     */
    ${name(t)}(int ID) {
        super(ID);
    }

    @Override
    public int stid() {
        return ${t.stid};
    }
""")

      if (visited.contains(t.name)) {
        out.write(s"""
    public <_R, _A, _E extends Exception> _R accept(Visitor<_R, _A, _E> v, _A arg) throws _E {
        return v.visit(this, arg);
    }
""")
      }

      var implementedFields = t.fields
      def addFields(i: InterfaceDef) {
        implementedFields ++= i.fields
        for (t ← i.superInterfaces)
          addFields(t)
      }
      t.superInterfaces.foreach(addFields)

      ///////////////////////
      // getters & setters //
      ///////////////////////
      for (f ← implementedFields) {
        def makeField: String =
          s"""
    protected ${if (f.isTransient) "transient "
          else ""}${if (f.`type`.isInstanceOf[EnumDef])
            s"Object ${name(f)};// = $packagePrefix${name(f.`type`)}.${capital(
              f.`type`.asInstanceOf[EnumDef].values.head.name)};"
          else s"${mapType(f.`type`)} ${name(f)};"}
"""

        def makeGetterImplementation: String = s"return ${name(f)};"
        def makeSetterImplementation: String = s"this.${name(f)} = ${name(f)};"

        if (f.`type`.isInstanceOf[EnumDef]) {
          val nameF = name(f)
          val enumF = packagePrefix + name(f.`type`)
          val typeF = mapType(f.`type`)
          out.write(s"""$makeField
    ${comment(f)}final public $typeF ${getter(f)}() {
        if ($nameF instanceof EnumProxy<?>)
            return ($typeF) $nameF;
        throw new IllegalStateException("$nameF is currently not a proxy: " + $nameF);
    }
    ${comment(f)}final public $enumF ${getter(f)}AsEnum() {
        if (null == $nameF)
            return ($enumF) ($nameF = $enumF.${capital(
            f.`type`
              .asInstanceOf[EnumDef]
              .values
              .sortWith(IRUtils.ogssLess)
              .head
              .name)});
        if ($nameF instanceof EnumProxy<?>)
            return (($typeF) $nameF).target;
        return ($enumF) $nameF;
    }

    ${comment(f)}final public void ${setter(f)}($typeF $nameF) {
        this.$nameF = $nameF;
    }
    ${comment(f)}final public void ${setter(f)}($packagePrefix${name(f.`type`)} $nameF) {
        if (null != this.$nameF && this.$nameF instanceof EnumProxy<?>)
            this.$nameF = (($typeF) this.$nameF).owner.proxy($nameF);
        else
            this.$nameF = $nameF;
    }
""")
        } else
          out.write(s"""$makeField
    ${comment(f)}public ${mapType(f.`type`)} ${getter(f)}() {
        $makeGetterImplementation
    }

    ${comment(f)}final public void ${setter(f)}(${mapType(f.`type`)} ${name(f)}) {
        $makeSetterImplementation
    }
""")
      }

      // custom fields
      for (c ← customizations) {
        val opts = c.options

        val mod = opts
          .find(_.name.equals("modifier"))
          .map(_.arguments.head)
          .getOrElse("public")

        val default = opts
          .find(_.name.equals("default"))
          .map(_.arguments(0))
          .orNull

        out.write(s"""
    ${comment(c)}$mod ${c.typename} ${name(c)}${if (null == default) ""
        else s" = $default"};
""")
        // realize accessor methods from interface fields
        if (c.owner != t) {
          out.write(s"""
    ${comment(c)}@Override
    ${mod.replace("transient", "")} ${c.typename} ${name(c)}() {return ${name(
            c)};}
    ${comment(c)}@Override
    ${mod.replace("transient", "")} void ${name(c)}(${c.typename} ${name(c)}) {this.${name(
            c)} = ${name(c)};}
""")
        }
      }

      // views
      for (v ← t.views) {
        // just redirect to the actual field so it's way simpler than getters & setters
        val fieldName = name(v)
        val target = name(v.target)
        val vt = mapType(v.`type`)

        // filter non-feasible views (in Java)
        if (!v.`type`.isInstanceOf[InterfaceDef]) {
          out.write(s"""
	${comment(v)}${if (v.name == v.target.name) " @Override\n    "
          else ""}public $vt ${getter(v)}() { return ($vt) $target; }
""")
        }
      }

      // fix toAnnotation
      if (!t.superInterfaces.isEmpty)
        out.write(s"""
    @Override
    public ${name(t)} self() {
        return this;
    }
""")

      out.write("""
}
""");
      out.close()
    }
  }
}
