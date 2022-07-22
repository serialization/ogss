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

trait InterfacesMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← interfaces) {
      val out = files.open(s"${name(t)}.java")

      val customizations = t.customs.filter(_.language.equals("java")).toArray

      //package
      out.write(s"""package ${this.packageName};

import ogss.common.java.internal.EnumProxy;
import ogss.common.java.internal.Pool;
${customizations
        .flatMap(
          _.options.find(_.name.equals("import")).toSeq.flatMap(_.arguments)
        )
        .map(i ⇒ s"import $i;\n")
        .mkString}
""")

      val packageName =
        if (this.packageName.contains('.'))
          this.packageName.substring(this.packageName.lastIndexOf('.') + 1)
        else this.packageName;

      val fields = allFields(t)

      out.write(s"""
${comment(t)}${suppressWarnings}public interface ${name(t)} ${if (t.superInterfaces.isEmpty)
        ""
      else
        t.superInterfaces.map(name(_)).mkString("extends ", ", ", "")} {

    /**
     * cast to concrete type
     */${if (!t.superInterfaces.isEmpty) """
    @Override"""
      else ""}
    public default ${if (null == t.superType) "Object"
      else mapType(t.superType)} self() {
        return ${if (null == t.superType) ""
      else s"(${mapType(t.superType)})"} this;
    }
${///////////////////////
      // getters & setters //
      ///////////////////////
      (
        for (f ← fields)
          yield s"""
    ${comment(f)}public ${mapType(f.`type`)} ${getter(f)}();

    ${comment(f)}public void ${setter(f)}(${mapType(f.`type`)} ${name(f)});
"""
      ).mkString}""");

      // custom field accessors
      for (c ← customizations) {
        val mod = c.options
          .find(_.name.equals("modifier"))
          .map(_.arguments.head)
          .getOrElse("public")

        out.write(s"""
    ${comment(c)}${mod.replace("transient", "")} ${c.typename} ${name(c)}();
    ${comment(c)}${mod.replace("transient", "")} void ${name(c)}(${c.typename} ${name(c)});
""")
      }
      out.write("""}
""");
      out.close()
    }
  }
}
