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
import scala.collection.mutable.ArrayBuffer
import ogss.oil.Type
import ogss.oil.ClassDef
import ogss.oil.InterfaceDef

trait OGFileMaker extends AbstractBackEnd {
  abstract override def make {
    super.make
    val out = files.open(s"OGFile.java")

    // reflection has to know projected definitions

    val classes = flatIR.to;

    //package & imports
    out.write(s"""package ${packageName};

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import ogss.common.java.api.Mode;
import ogss.common.java.api.OGSSException;
import ogss.common.java.internal.KCC;
import ogss.common.java.internal.Pool;
import ogss.common.java.internal.StateInitializer;

/**
 * An abstract OGSS file that is hiding all the dirty implementation details
 * from you.
 *
 * @note Type access fields start with a capital letter to avoid collisions and to match type names.
 *
 * @author Timm Felden
 */
${
      suppressWarnings
    }public final class OGFile extends ogss.common.java.internal.State {

    /**
     * Create a new OGSS file based on argument path and mode.
     *
     * @throws IOException
     *             on IO and mode related errors
     * @throws OGSSException
     *             on file or specification consistency errors
     */
    public static OGFile open(String path, Mode... mode) throws IOException, OGSSException {
        return new OGFile(StateInitializer.make(Paths.get(path), new internal.PB(), mode));
    }

    /**
     * Create a new OGSS file based on argument path and mode.
     *
     * @throws IOException
     *             on IO and mode related errors
     * @throws OGSSException
     *             on file or specification consistency errors
     */
    public static OGFile open(File path, Mode... mode) throws IOException, OGSSException {
        return new OGFile(StateInitializer.make(path.toPath(), new internal.PB(), mode));
    }

    /**
     * Create a new OGSS file based on argument path and mode.
     *
     * @throws IOException
     *             on IO and mode related errors
     * @throws OGSSException
     *             on file or specification consistency errors
     */
    public static OGFile open(Path path, Mode... mode) throws IOException, OGSSException {
        return new OGFile(StateInitializer.make(path, new internal.PB(), mode));
    }${
      (for (t ← IR) yield s"""

    /**
     * Access for all ${name(t)}s in this file
     */
    final public internal.${access(t)} ${name(t)}s;""").mkString("")
    }${
      (for (t ← this.types.getInterfaces.asScala) yield s"""

    /**
     * Access for all ${name(t)}s in this file
     */
    final public ${interfacePool(t)} ${name(t)}s;""").mkString("")
    }

    private OGFile(StateInitializer init) {
        super(init);
${
      (for (t ← classes)
        yield s"""
        ${name(t)}s = (internal.${access(t)}) init.SIFA[${t.getStid}];""").mkString("")
    }${
      (for (t ← types.getInterfaces.asScala)
        yield s"""
        ${name(t)}s = new ${interfacePool(t)}("${ogssname(t)}", ${
        if (null == t.getSuperType) "anyRefType"
        else name(t.getSuperType) + "s";
      }${
        val realizations = collectRealizationNames(t);
        if (realizations.isEmpty) ""
        else realizations.mkString(",", ",", "")
      });""").mkString("")
    }

        init.awaitResults();
    }
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

    IR.filter(reaches).map(name(_) + "s").toSeq
  }

  /**
   * the name of an interface field type that acts as its pool
   */
  protected final def interfacePool(t : InterfaceDef) : String =
    if (null == t.getSuperType)
      s"ogss.common.java.internal.UnrootedInterfacePool<${mapType(t)}>"
    else
      s"ogss.common.java.internal.InterfacePool<${mapType(t)}, ${mapType(t.getBaseType)}>"
}
