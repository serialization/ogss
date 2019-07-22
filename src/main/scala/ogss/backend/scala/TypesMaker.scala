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

import scala.collection.JavaConverters._
import ogss.oil.InterfaceDef
import ogss.oil.EnumDef
import ogss.oil.WithInheritance
import ogss.io.PrintWriter
import ogss.oil.ClassDef
import ogss.oil.Field
import ogss.oil.BuiltinType

trait TypesMaker extends AbstractBackEnd {

  @inline def introducesStateRef(t : WithInheritance) : Boolean = hasDistributedField(t) && (
    null == t.getSuperType() || !hasDistributedField(t.getSuperType)
  )

  abstract override def make {
    super.make

    // create one file for all base-less interfaces
    createAnnotationInterfaces;

    // create one file for each type hierarchy to help parallel builds
    for (base ← IR if null == base.getSuperType) {

      val out = files.open(s"TypesOf${base.getName.getOgss}.scala")

      //package
      out.write(s"""package ${this.packageName}

import ogss.common.scala.internal.EnumProxy;
${
        (for (
          t ← (IR ++ interfaces) if t.getBaseType == base;
          c ← t.getCustoms.asScala if c.getLanguage.equals("scala");
          i ← c.getOptions.asScala.find(_.getName.equals("import")).toSeq.flatMap(_.getArguments.asScala)
        ) yield s"import $i;\n").mkString
      }
""")

      for (t ← (IR ++ interfaces) if t.getBaseType == base) {
        val isInterface = t.isInstanceOf[InterfaceDef]

        val flatType = flatIR.find(_.getName == (if (isInterface) t.getSuperType.getName else t.getName)).get

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
          if (null != t.getSuperType()) name(t.getSuperType) else "ogss.common.scala.internal.Obj"
        }${
          if (isInterface) ""
          else s"(__ID${
            if (hasDistributedField(flatType.getSuperType)) ", __state"
            else ""
          })"
        }${
          (for (s ← t.getSuperInterfaces.asScala)
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

    override def STID : scala.Int = ${t.getStid};
"""
        }""")

        makeGetterAndSetter(out, t, flatType)

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

    if (interfaces.forall(_.getSuperType != null))
      return ;

    val out = files.open(s"TypesOfAnyRef.scala")

    //package
    out.write(s"""package ${this.packageName}

import ogss.common.scala.internal.EnumProxy;
${
      (for (
        t ← interfaces if null == t.getSuperType;
        c ← t.getCustoms.asScala if c.getLanguage.equals("scala");
        i ← c.getOptions.asScala.find(_.getName.equals("import")).toSeq.flatMap(_.getArguments.asScala)
      ) yield s"import $i;\n").mkString
    }""")

    for (t ← interfaces if null == t.getSuperType) {
      out.write(s"""
${
        comment(t)
      }trait ${name(t)} extends ogss.common.scala.internal.Obj${
        (for (s ← t.getSuperInterfaces.asScala)
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
    val packageName = if (this.packageName.contains('.')) this.packageName.substring(this.packageName.lastIndexOf('.') + 1)
    else this.packageName;

    for (f ← t.getFields.asScala) {

      if (isDistributed(f)) {
        t match {
          case t : InterfaceDef ⇒ out.write(s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.getType())};
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.getType())}) : scala.Unit;
""")
          case t ⇒ out.write(s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.getType())} = ${makeGetterImplementation(t, f)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.getType())}) : scala.Unit = ${makeSetterImplementation(t, f)}
""")
        }
      } else {
        out.write(s"""
  ${
          if (f.getIsTransient) "@transient " else ""
        }final protected[${this.packageName}] var ${localFieldName(f)} : ${
          if (f.getType.isInstanceOf[EnumDef]) "AnyRef" else mapType(f.getType())
        } = ${defaultValue(f)}${
          if (f.getType.isInstanceOf[EnumDef]) {
            val et = s"$packagePrefix${name(f.getType())}.Value"
            s"""
  ${comment(f)}def ${escaped(capital(f.getName) + "Enum")} : $et = ${makeGetterImplementation(t, f)} match {
    case null ⇒ ${defaultValue(f)}
    case v : $et ⇒ v
    case v : ${mapType(f.getType)} ⇒ v.target
  }
  ${comment(f)}def ${getter(f)} : ${mapType(f.getType())} = ${makeGetterImplementation(t, f)} match {
    case v : ${mapType(f.getType())} ⇒ v
    case _ ⇒ null
  }
  ${comment(f)}def ${setter(f)}(${name(f)} : $et) : scala.Unit = ${localFieldName(f)} = ${localFieldName(f)} match {
    case v : ${mapType(f.getType)} ⇒ v.owner.proxy(${name(f)})
    case v ⇒ v
  }
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.getType())}) : scala.Unit = ${makeSetterImplementation(t, f)}
"""
          } else {
            s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.getType())} = ${makeGetterImplementation(t, f)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.getType())}) : scala.Unit = ${makeSetterImplementation(t, f)}"""
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
          val name = f.getName
          isDistributed(f) &&
            (null == t.getSuperType() || !allFields(t.getSuperType).exists(_ == f)) &&
            !t.getFields.asScala.exists(_.getName.equals(name))
      }

      for (f ← fields) {
        val thisF = flatType.getFields.asScala.find(_.getName.equals(f.getName)).get;
        val thisT = flatType;
        if (thisF.getType.isInstanceOf[EnumDef]) {
          val et = s"$packagePrefix${name(f.getType())}.Value"
          out.write(s"""
  ${comment(f)}def ${escaped(capital(f.getName) + "Enum")} : $et = ${makeGetterImplementation(thisT, thisF)} match {
    case null ⇒ ${defaultValue(f)}
    case v : $et ⇒ v
    case v : ${mapType(f.getType)} ⇒ v.target
  }
  ${comment(f)}def ${getter(f)} : ${mapType(f.getType())} = ${makeGetterImplementation(thisT, thisF)} match {
    case v : ${mapType(f.getType())} ⇒ v
    case _ ⇒ null
  }
  ${comment(f)}def ${setter(f)}(${name(f)} : $et) : scala.Unit = ${makeSetterImplementation(thisT, thisF)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.getType())}) : scala.Unit = ${makeSetterImplementation(thisT, thisF)}
""")
        } else {
          out.write(s"""
  ${comment(f)}def ${getter(f)} : ${mapType(f.getType())} = ${makeGetterImplementation(thisT, thisF)}
  ${comment(f)}def ${setter(f)}(${name(f)} : ${mapType(f.getType())}) : scala.Unit = ${makeSetterImplementation(thisT, thisF)}
""")
        }
      }
    }

    // custom fields
    for (c ← t.getCustoms.asScala if c.getLanguage.equals("scala")) {
      val opts = c.getOptions.asScala
      val mod = opts.find(_.getName.equals("modifier")).map(_.getArguments.get(0) + " ").getOrElse("")
      val default = opts.find(_.getName.equals("default")).map(_.getArguments.get(0)).getOrElse("_")

      out.write(s"""
  ${comment(c)}${mod}var ${name(c)} : ${c.getTypename} = $default; 
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
        //        if (f.getType().isInstanceOf[BuiltinType]) {
        //          if (isInteger(f.getType)) {
        //            f.getRestrictions.asScala.collect { case r : IntRangeRestriction ⇒ r }.map { r ⇒ s"""require(${r.getLow}L <= ${name(f)} && ${name(f)} <= ${r.getHigh}L, "${name(f)} has to be in range [${r.getLow};${r.getHigh}]"); """ }.mkString("")
        //          } else if ("F32".equals(f.getType.getName.getOgss)) {
        //            f.getRestrictions.asScala.collect { case r : FloatRangeRestriction ⇒ r }.map { r ⇒ s"""require(${r.getLowFloat}f <= ${name(f)} && ${name(f)} <= ${r.getHighFloat}f, "${name(f)} has to be in range [${r.getLowFloat};${r.getHighFloat}]"); """ }.mkString("")
        //          } else if ("F64".equals(f.getType.getName.getOgss)) {
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
