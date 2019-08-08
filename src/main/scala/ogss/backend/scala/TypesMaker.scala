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
package ogss.backend.scala

import ogss.io.PrintWriter
import ogss.oil.ClassDef
import ogss.oil.EnumDef
import ogss.oil.Field
import ogss.oil.InterfaceDef
import ogss.oil.WithInheritance
import ogss.oil.FieldLike

trait TypesMaker extends AbstractBackEnd {

  @inline def introducesStateRef(t : WithInheritance) : Boolean = hasDistributedField(t) && (
    null == t.superType || !hasDistributedField(t.superType)
  )

  abstract override def make {
    super.make

    // create one file for all base-less interfaces
    createAnnotationInterfaces;

    // create one file for each type hierarchy to help parallel builds
    for (base ← IR if null == base.superType) {

      val out = files.open(s"TypesOf${base.name.ogss}.scala")

      //package
      out.write(s"""package ${this.packageName}

import ogss.common.scala.internal.EnumProxy;
${
        (for (
          t ← (IR ++ interfaces) if t.baseType == base;
          c ← t.customs if c.language.equals("scala");
          i ← c.options.find(_.name.equals("import")).toSeq.flatMap(_.arguments)
        ) yield s"import $i;\n").mkString
      }
""")

      for (t ← (IR ++ interfaces) if t.baseType == base) {
        val isInterface = t.isInstanceOf[InterfaceDef]

        val flatType = flatIR.find(_.name == (if (isInterface) t.superType.name else t.name)).get

        val fields = allFields(t)

        out.write(s"""
${
          comment(t)
        }sealed ${
          if (isInterface) "trait" else "class"
        } ${name(t)}${
          if (isInterface) ""
          else s" (__ID : scala.Int${
            if (hasDistributedField(flatType))
              (", " + (
                if (introducesStateRef(t)) "val "
                else ""
              ) + "__state : api.OGFile")
            else ""
          })"
        } extends ${
          if (null != t.superType) name(t.superType) else "ogss.common.scala.internal.Obj"
        }${
          if (isInterface) ""
          else s"(__ID${
            if (hasDistributedField(flatType.superType)) ", __state"
            else ""
          })"
        }${
          (for (s ← t.superInterfaces)
            yield " with " + name(s)).mkString
        } {
${
          if (isInterface) ""
          else s"""
    /**
     * Create a new unmanaged ${ogssname(t)}. Allocation of objects without using the
     * access factory method is discouraged.
     */
    def this() {
        this(0);
    }

    override def STID : scala.Int = ${t.stid};
"""
        }""")

        makeGetterAndSetter(out, t, flatType)

        // views
        for (v ← t.views) {
          // just redirect to the actual field so it's way simpler than getters & setters
          val fieldName = name(v)
          val target =
            if (v.name == v.target.name) "super." + getter(v.target)
            else v.target match {
              case f : Field     ⇒ localFieldName(f)
              case f : FieldLike ⇒ getter(f)
            }

          val fieldAssignName = setter(v)

          val vt = mapType(v.`type`)

          out.write(s"""
	${comment(v)}${
            if (v.name == v.target.name) "override "
            else ""
          }def $fieldName : $vt = $target.asInstanceOf[$vt]
  ${comment(v)}def $fieldAssignName($fieldName : $vt) : scala.Unit = $target = $fieldName""")
        }

        out.write(s"""
}
""")

        if (!isInterface) out.write(s"""
final class ${subtype(t)}(val τp : ogss.common.scala.internal.Pool[_ <: ${name(t)}], _ID : scala.Int)
  extends ${name(t)}(_ID) with ogss.common.scala.internal.NamedObj {
  override def STID : scala.Int = -1
}
""")
      }

      out.close()
    }
  }

  /**
   * interfaces required to type fields
   *
   * interfaces created here inherit no regular type, i.e. they have no super class
   */
  private def createAnnotationInterfaces {

    if (interfaces.forall(_.superType != null))
      return ;

    val out = files.open(s"TypesOfAnyRef.scala")

    //package
    out.write(s"""package ${this.packageName}

import ogss.common.scala.internal.EnumProxy;
${
      (for (
        t ← interfaces if null == t.superType;
        c ← t.customs if c.language.equals("scala");
        i ← c.options.find(_.name.equals("import")).toSeq.flatMap(_.arguments)
      ) yield s"import $i;\n").mkString
    }""")

    for (t ← interfaces if null == t.superType) {
      out.write(s"""
${
        comment(t)
      }trait ${name(t)} extends ogss.common.scala.internal.Obj${
        (for (s ← t.superInterfaces)
          yield " with " + name(s)).mkString
      } {""")

      makeGetterAndSetter(out, t, null)

      out.write("""
}
""")
    }

    out.close
  }

  ///////////////////////
  // getters & setters //
  ///////////////////////

  private def makeGetterAndSetter(out : PrintWriter, t : WithInheritance, flatType : ClassDef) {
    val packageName = packageLastName

    for (f ← t.fields) {
      if (isDistributed(f)) {
        t match {
          case t : InterfaceDef ⇒ out.write(s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.`type`)};
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.`type`)}) : scala.Unit;
""")
          case t ⇒ out.write(s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.`type`)} = ${makeGetterImplementation(t, f)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.`type`)}) : scala.Unit = ${makeSetterImplementation(t, f)}
""")
        }
      } else {
        out.write(s"""
  ${
          if (f.isTransient) "@transient " else ""
        }final protected[$packageName] var ${localFieldName(f)} : ${
          if (f.`type`.isInstanceOf[EnumDef]) "AnyRef" else mapType(f.`type`)
        } = ${defaultValue(f)}${
          if (f.`type`.isInstanceOf[EnumDef]) {
            val et = s"$packagePrefix${name(f.`type`)}.Value"
            s"""
  ${comment(f)}def ${escaped(capital(f.name) + "Enum")} : $et = ${makeGetterImplementation(t, f)} match {
    case null ⇒ ${defaultValue(f)}
    case v : $et ⇒ v
    case v : ${mapType(f.`type`)} ⇒ v.target
  }
  ${comment(f)}def ${getter(f)} : ${mapType(f.`type`)} = ${makeGetterImplementation(t, f)} match {
    case v : ${mapType(f.`type`)} ⇒ v
    case _ ⇒ null
  }
  ${comment(f)}def ${setter(f)}(${name(f)} : $et) : scala.Unit = ${localFieldName(f)} = ${localFieldName(f)} match {
    case v : ${mapType(f.`type`)} ⇒ v.owner.proxy(${name(f)})
    case v ⇒ v
  }
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.`type`)}) : scala.Unit = ${makeSetterImplementation(t, f)}
"""
          } else {
            s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.`type`)} = ${makeGetterImplementation(t, f)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.`type`)}) : scala.Unit = ${makeSetterImplementation(t, f)}"""
          }
        }
""")
      }
    }

    // create implementations of distributed fields inherited from interfaces
    if (null != flatType && hasDistributedField(flatType)) {
      // collect distributed fields that are not projected onto the super type but onto us
      val fields = allFields(t).filter {
        f ⇒
          val name = f.name
          isDistributed(f) &&
            (null == t.superType || !allFields(t.superType).exists(_ == f)) &&
            !t.fields.exists(_.name == name)
      }

      for (f ← fields) {
        val thisF = flatType.fields.find(_.name.equals(f.name)).get;
        val thisT = flatType;
        if (thisF.`type`.isInstanceOf[EnumDef]) {
          val et = s"$packagePrefix${name(f.`type`)}.Value"
          out.write(s"""
  ${comment(f)}def ${escaped(capital(f.name) + "Enum")} : $et = ${makeGetterImplementation(thisT, thisF)} match {
    case null ⇒ ${defaultValue(f)}
    case v : $et ⇒ v
    case v : ${mapType(f.`type`)} ⇒ v.target
  }
  ${comment(f)}def ${getter(f)} : ${mapType(f.`type`)} = ${makeGetterImplementation(thisT, thisF)} match {
    case v : ${mapType(f.`type`)} ⇒ v
    case _ ⇒ null
  }
  ${comment(f)}def ${setter(f)}(${name(f)} : $et) : scala.Unit = ${makeSetterImplementation(thisT, thisF)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.`type`)}) : scala.Unit = ${makeSetterImplementation(thisT, thisF)}
""")
        } else {
          out.write(s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.`type`)} = ${makeGetterImplementation(thisT, thisF)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.`type`)}) : scala.Unit = ${makeSetterImplementation(thisT, thisF)}
""")
        }
      }
    }

    // custom fields
    for (c ← t.customs if c.language.equals("scala")) {
      val opts = c.options
      val mod = opts.find(_.name.equals("modifier")).map(_.arguments(0) + " ").getOrElse("")
      val default = opts.find(_.name.equals("default")).map(_.arguments(0)).getOrElse("_")

      out.write(s"""
  ${comment(c)}${mod}var ${name(c)} : ${c.typename} = $default; 
""")
    }
  }

  def makeGetterImplementation(t : WithInheritance, f : Field) : String = {
    if (isDistributed(f)) {
      s"__state.${name(t)}.${knownField(f)}.getR(this)"
    } else {
      localFieldName(f)
    }
  }

  def makeSetterImplementation(t : WithInheritance, f : Field) : String = {
    if (isDistributed(f)) {
      s"__state.${name(t)}.${knownField(f)}.setR(this, ${name(f)})"
    } else {
      s"{ ${ //@range check
        ""
        //        if (f.`type`.isInstanceOf[BuiltinType]) {
        //          if (isInteger(f.`type`)) {
        //            f.getRestrictions.asScala.collect { case r : IntRangeRestriction ⇒ r }.map { r ⇒ s"""require(${r.getLow}L <= ${name(f)} && ${name(f)} <= ${r.getHigh}L, "${name(f)} has to be in range [${r.getLow};${r.getHigh}]"); """ }.mkString("")
        //          } else if ("F32".equals(f.`type`.name.ogss)) {
        //            f.getRestrictions.asScala.collect { case r : FloatRangeRestriction ⇒ r }.map { r ⇒ s"""require(${r.getLowFloat}f <= ${name(f)} && ${name(f)} <= ${r.getHighFloat}f, "${name(f)} has to be in range [${r.getLowFloat};${r.getHighFloat}]"); """ }.mkString("")
        //          } else if ("F64".equals(f.`type`.name.ogss)) {
        //            f.getRestrictions.asScala.collect { case r : FloatRangeRestriction ⇒ r }.map { r ⇒ s"""require(${r.getLowDouble} <= ${name(f)} && ${name(f)} <= ${r.getHighDouble}, "${name(f)} has to be in range [${r.getLowDouble};${r.getHighDouble}]"); """ }.mkString("")
        //          } else {
        //            ""
        //          }
        //        } else {
        //          ""
        //        }
      }${localFieldName(f)} = ${name(f)} }"
    }
  }
}
