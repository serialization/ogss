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

import scala.collection.JavaConverters._
import ogss.oil.InterfaceDef
import ogss.oil.EnumDef

trait TypesMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← IR) {
      val out = files.open(s"${name(t)}.java")

      val customizations = t.getCustoms.asScala.filter(_.getLanguage.equals("java")).toArray

      // package
      out.write(s"""package ${this.packageName};

import ogss.common.java.internal.EnumProxy;
${
        customizations.flatMap(
          _.getOptions.asScala.find(_.getName.equals("import")).toSeq.flatMap(_.getArguments.asScala)
        ).map(i ⇒ s"import $i;\n").mkString
      }
""")

      val fields = allFields(t)

      out.write(s"""
${
        comment(t)
      }${
        suppressWarnings
      }public class ${name(t)} extends ${
        if (null != t.getSuperType()) { name(t.getSuperType) }
        else { "ogss.common.java.internal.Obj" }
      }${
        if (t.getSuperInterfaces.isEmpty) ""
        else
          t.getSuperInterfaces.asScala.map(name(_)).mkString(" implements ", ", ", "")
      } {

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
        return ${t.getStid};
    }
""")

      if (visited.contains(t.getName)) {
        out.write(s"""
    public <_R, _A, _E extends Exception> _R accept(Visitor<_R, _A, _E> v, _A arg) throws _E {
        return v.visit(this, arg);
    }
""")
      }

      var implementedFields = t.getFields.asScala
      def addFields(i : InterfaceDef) {
        implementedFields ++= i.getFields.asScala
        for (t ← i.getSuperInterfaces.asScala)
          addFields(t)
      }
      t.getSuperInterfaces.asScala.foreach(addFields)

      ///////////////////////
      // getters & setters //
      ///////////////////////
      for (f ← implementedFields) {
        def makeField : String = s"""
    protected ${
          if (f.getIsTransient) "transient "
          else ""
        }${
          if (f.getType.isInstanceOf[EnumDef]) s"Object ${name(f)};// = $packagePrefix${name(f.getType)}.${
            capital(f.getType.asInstanceOf[EnumDef].getValues.get(0).getName)
          };"
          else s"${mapType(f.getType())} ${name(f)};"
        }
"""

        def makeGetterImplementation : String = s"return ${name(f)};"
        def makeSetterImplementation : String = s"this.${name(f)} = ${name(f)};"

        if (f.getType.isInstanceOf[EnumDef]) {
          val nameF = name(f)
          val enumF = packagePrefix + name(f.getType)
          val typeF = mapType(f.getType())
          out.write(s"""$makeField
    ${comment(f)}final public $typeF ${getter(f)}() {
        if ($nameF instanceof EnumProxy<?>)
            return ($typeF) $nameF;
        throw new IllegalStateException("$nameF is currently not a proxy: " + $nameF);
    }
    ${comment(f)}final public $enumF ${getter(f)}AsEnum() {
        if (null == $nameF)
            return ($enumF) ($nameF = $enumF.${capital(f.getType.asInstanceOf[EnumDef].getValues.get(0).getName)});
        if ($nameF instanceof EnumProxy<?>)
            return (($typeF) $nameF).target;
        return ($enumF) $nameF;
    }

    ${comment(f)}final public void ${setter(f)}($typeF $nameF) {
        this.$nameF = $nameF;
    }
    ${comment(f)}final public void ${setter(f)}($packagePrefix${name(f.getType)} $nameF) {
        if (null != this.$nameF && this.$nameF instanceof EnumProxy<?>)
            this.$nameF = (($typeF) this.$nameF).owner.proxy($nameF);
        else
            this.$nameF = $nameF;
    }
""")
        } else
          out.write(s"""$makeField
    ${comment(f)}final public ${mapType(f.getType())} ${getter(f)}() {
        $makeGetterImplementation
    }

    ${comment(f)}final public void ${setter(f)}(${mapType(f.getType())} ${name(f)}) {
        $makeSetterImplementation
    }
""")
      }

      // custom fields
      for (c ← customizations) {
        val mod = c.getOptions.asScala.find(_.getName.equals("modifier")).map(_.getArguments.get(0)).getOrElse("public")

        out.write(s"""
    ${comment(c)}$mod ${c.getTypename} ${name(c)}; 
""")
      }

      // fix toAnnotation
      if (!t.getSuperInterfaces.isEmpty())
        out.write(s"""
    @Override
    public ${name(t)} self() {
        return this;
    }
""")

      out.write(s"""
}
""");
      out.close()
    }
  }
}
