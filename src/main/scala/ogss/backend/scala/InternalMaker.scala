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

import scala.collection.mutable.ArrayBuffer
import ogss.backend.java.internal.AccessMaker
import ogss.io.PrintWriter
import ogss.oil.Type
import ogss.oil.ClassDef

/**
 * Create an internal class instead of a package. That way, the fucked-up Java
 * visibility model can be exploited to access fields directly.
 * As a side-effect, types that resided in that package must be inner classes of
 * internal.
 */
trait InternalMaker extends AbstractBackEnd with FieldDeclarationMaker {

  abstract override def make {
    super.make

    val out = files.open(s"Internal.scala")

    out.write(s"""package ${this.packageName};

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.HashSet;

import scala.language.existentials

import ogss.common.scala.api.OGSSException;
import ogss.common.scala.internal.AnyRefType;
import ogss.common.scala.internal.EnumPool;
import ogss.common.scala.internal.EnumProxy;
import ogss.common.scala.internal.FieldType;
import ogss.common.scala.internal.HullType;
import ogss.common.scala.internal.Obj;
import ogss.common.scala.internal.Pool;
import ogss.common.scala.internal.StringPool;
import ogss.common.scala.internal.SubPool;
import ogss.common.scala.internal.fields.AutoField
import ogss.common.scala.internal.fields.KnownField
import ogss.common.streams.BufferedOutStream;
import ogss.common.streams.FileInputStream;
import ogss.common.streams.MappedInStream;

object internal {
""")

    makePB(out);
    makePools(out);
    makeBuilders(out);
    makeFields(out);

    out.write("""}
""");

    out.close
  }

  private final def makePB(out : PrintWriter) {
    out.write(s"""
  object PB extends ogss.common.scala.internal.PoolBuilder(${flatTC.getByName.size}) {

    override def literals : Array[String] = Array(${
      allStrings.map(_.getOgss).mkString("\"", "\", \"", "\"")
    })

    override def kcc(ID : scala.Int) : scala.Int = ID match {${
      // predefine known containers
      flatTC.getContainers.asScala.zipWithIndex.map {
        case (ct, i) ⇒
          f"""
      case $i ⇒ 0x${ct.getKcc()}%08x; // ${ogssname(ct)}"""
      }.mkString
    }
      case _ ⇒ -1
    }

    override def name(ID : scala.Int) : String = ${
      if (IR.isEmpty) "null"
      else IR.filter(_.getSuperType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
      case $i ⇒ "${ogssname(t)}";"""
      }.mkString("ID match {", "", """
      case _ ⇒ null
    }""")
    }

    override def make(ID : scala.Int, index : scala.Int) : Pool[_  <: Obj] = ${
      if (IR.isEmpty) "null"
      else IR.filter(_.getSuperType == null).zipWithIndex.map {
        case (t, i) ⇒ s"""
      case $i ⇒ new ${access(t)}(index);"""
      }.mkString("ID match {", "", """
      case _ ⇒ null
    }""")
    }

    override def enumName(ID : scala.Int) : String = ${
      if (enums.isEmpty) "null"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
      case $i ⇒ "${t.getName.getOgss}""""
      }.mkString("ID match {", "", """
      case _ ⇒ null
    }""")
    }

    override def enumMake(ID : scala.Int) : Enumeration = ${
      if (enums.isEmpty) "null"
      else enums.zipWithIndex.map {
        case (t, i) ⇒ s"""
      case $i ⇒ $packagePrefix${name(t)}"""
      }.mkString("ID match {", "", """
      case _ ⇒ null
    }""")
    }
  }
""")
  }

  private final def makePools(out : PrintWriter) {
    for (t ← flatIR) {
      val isBasePool = (null == t.getSuperType)
      val nameT = name(t)
      val subT = s"${this.packageName}.Sub$$$nameT"
      val typeT = mapType(t)
      val accessT = access(t)

      // find all fields that belong to the projected version, but use the unprojected variant
      val fields = t.getFields.asScala

      out.write(s"""
  final class $accessT (_idx : scala.Int${
        if (isBasePool) ""
        else s", _sup : ${access(t.getSuperType)}"
      }) extends Pool[$typeT](_idx, "${ogssname(t)}", ${
        if (isBasePool) "null"
        else "_sup"
      }, ${fields.count(_.getIsTransient)}) {${
        // export data for sub pools
        if (isBasePool) s"""

    private[internal] def _data = data"""
        else ""
      }${
        if (fields.isEmpty) ""
        else s"""

    override def KFN(ID : scala.Int) : String = ID match {${
          fields.zipWithIndex.map {
            case (f, i) ⇒ s"""
      case $i ⇒ "${ogssname(f)}""""
          }.mkString
        }
      case _ ⇒ null
    }

    override def KFC(ID : scala.Int, SIFA : Array[FieldType[_]], nextFID : scala.Int) : ogss.common.scala.internal.Field[_, $typeT] = ID match {${
          fields.zipWithIndex.map {
            case (f, i) ⇒ s"""
      case $i ⇒ new ${knownField(f)}(SIFA(${f.getType.getStid}), ${
              if (f.getIsTransient) ""
              else "nextFID, "
            }this)"""
          }.mkString
        }
      case _ ⇒ null
    }"""
      }

    override def allocateInstances {
      var i = bpo
      var j = 0
      val high = i + staticDataInstances
      while (i < high) {
        j = (i + 1)
        data(i) = new $typeT(j)
        i = j
      }
    }

    override def typeCheck(x : Any) : scala.Boolean = x.isInstanceOf[$typeT]

    /**
     * @return a new $nameT instance with default field values
     */
    override def make = {
      val r = new $typeT(0)
      add(r);
      r
    }

    def build : (${builder(t)}[$typeT, B] forSome { type B <: ${builder(t)}[$typeT, B] }) =
      new ${builder(t)}(this, new $typeT(0))
${
        if (t.getSubTypes.isEmpty) ""
        else s"""
    override def nameSub(ID : scala.Int) : String = ID match {${
          t.getSubTypes.asScala.zipWithIndex.map {
            case (s, i) ⇒ s"""
      case $i ⇒ "${ogssname(s)}""""
          }.mkString
        }
      case _ ⇒ null
    }

    override def makeSub(ID : scala.Int, index : scala.Int) : Pool[_ <: ${mapType(t)}] = ID match {${
          t.getSubTypes.asScala.collect { case t : ClassDef ⇒ t }.zipWithIndex.map {
            case (s, i) ⇒ s"""
      case $i ⇒ new ${access(s)}(index, this)"""
          }.mkString
        }
      case _ ⇒ null
    }
"""
      }

    override def makeSub(index : scala.Int, name : String) : Pool[_ <: ${mapType(t)}] = {
      new SubPool[${this.packageName}.${subtype(t)}](index, name, classOf[${this.packageName}.${subtype(t)}], this);
    }
  }
""")
    }
  }

  private final def makeBuilders(out : PrintWriter) {

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
  class ${builder(t)}[T <: $typeT, B <: ${builder(t)}[T, B]] protected[internal] (_pool : Pool[T], _self : T)
    extends ${
          if (null == t.getSuperType) s"ogss.common.scala.internal.Builder[T]"
          else s"${builder(t.getSuperType)}[T, B]"
        }(_pool, _self) {${
          (for (f ← fields)
            yield s"""

    def ${name(f)}(${name(f)} : ${mapType(f.getType)}) : B = {
      self.${localFieldName(f)} = ${name(f)};
      this.asInstanceOf[B]
    }""").mkString
        }
  }
""")
      }
    }
  }
}
