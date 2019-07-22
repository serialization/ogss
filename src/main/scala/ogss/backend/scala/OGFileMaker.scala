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
import ogss.oil.Type
import ogss.oil.ClassDef
import ogss.oil.InterfaceDef

trait OGFileMaker extends AbstractBackEnd {
  abstract override def make {
    super.make
    val out = files.open(s"OGFile.scala")

    // reflection has to know projected definitions

    val classes = flatIR.to;

    //package & imports
    out.write(s"""package ${packageName};

import java.io.File;
import java.nio.file.Path;

import ogss.common.scala.api.Create
import ogss.common.scala.api.Mode;
import ogss.common.scala.api.OGSSException;
import ogss.common.scala.api.ReadMode
import ogss.common.scala.api.Read
import ogss.common.scala.api.WriteMode
import ogss.common.scala.api.Write
import ogss.common.scala.internal.StateInitializer;

/**
 * An abstract OGSS file that is hiding all the dirty implementation details
 * from you.
 *
 * @note Type access fields start with a capital letter to avoid collisions and to match type names.
 *
 * @author Timm Felden
 */
final class OGFile private (_init : StateInitializer)
  extends ogss.common.scala.internal.State(_init : StateInitializer) {
${
      (for (t ← classes) yield s"""
  /**
   * Access for all ${name(t)}s in this file
   */
  val ${name(t)} = _init.SIFA(${t.getStid}).asInstanceOf[internal.${access(t)}]""").mkString("")
    }${
      (for (t ← types.getInterfaces.asScala)
        yield s"""
  /**
   * Access for all ${name(t)}s in this file
   */
  val ${name(t)} = new ${interfacePool(t)}("${ogssname(t)}", ${
        if (null == t.getSuperType) "anyRefType"
        else name(t.getSuperType)
      }${
        val realizations = collectRealizationNames(t);
        if (realizations.isEmpty) ""
        else realizations.mkString(", ", ", ", "")
      });""").mkString("")
    }${
      (for (t ← enums) yield s"""
  /**
   * Access for all ${name(t)} proxies in this file
   */
  val ${name(t)} = _init.SIFA(${t.getStid}).asInstanceOf[ogss.common.scala.internal.EnumPool[$packagePrefix${name(t)}.type]]""").mkString("")
    }

  _init.awaitResults
}

/**
 * @author Timm Felden
 */
object OGFile {
  /**
   * Reads a binary OGSS file and turns it into an OGSS state.
   */
  def open(path : String, read : ReadMode = Read, write : WriteMode = Write) : OGFile = {
    val f = new File(path)
    if (!f.exists())
      f.createNewFile()
    readFile(f.toPath, read, write)
  }
  /**
   * Reads a binary OGSS file and turns it into an OGSS state.
   */
  def open(file : File, read : ReadMode, write : WriteMode) : OGFile = {
    if (!file.exists())
      file.createNewFile()
    readFile(file.toPath, read, write)
  }
  /**
   * Reads a binary OGSS file and turns it into an OGSS state.
   */
  def open(path : Path, read : ReadMode, write : WriteMode) : OGFile = readFile(path, read, write)

  /**
   * same as open(create)
   */
  def create(path : Path, write : WriteMode = Write) : OGFile = readFile(path, Create, write)

  /**
   * same as open(read)
   */
  def read(path : Path, write : WriteMode = Write) : OGFile = readFile(path, Read, write)

  private def readFile(path : Path, read : ReadMode, write : WriteMode) : OGFile =
    new OGFile(StateInitializer(path, internal.PB, Seq(read, write)))
}
""")

    out.close()
  }

  private def collectRealizationNames(target : InterfaceDef) : Seq[String] = {
    def reaches(t : Type) : Boolean = t match {
      case t : ClassDef     ⇒ t.getSuperInterfaces.contains(target) || t.getSuperInterfaces.asScala.exists(reaches)
      case t : InterfaceDef ⇒ t.getSuperInterfaces.contains(target) || t.getSuperInterfaces.asScala.exists(reaches)
      case _                ⇒ false
    }

    IR.filter(reaches).map(name).toSeq
  }

  /**
   * the name of an interface field type that acts as its pool
   */
  protected final def interfacePool(t : InterfaceDef) : String =
    if (null == t.getSuperType)
      s"ogss.common.scala.internal.UnrootedInterfacePool[${mapType(t)}]"
    else
      s"ogss.common.scala.internal.InterfacePool[${mapType(t)}, ${mapType(t.getSuperType)}]"
}
