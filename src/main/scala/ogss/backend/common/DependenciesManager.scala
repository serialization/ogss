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
package ogss.backend.common

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest
import scala.collection.mutable.HashMap

/**
 * creates copies of required dependencies in $outPath
 * @author Timm Felden
 */
object DependenciesMaker {
  def copyDeps(names : Seq[String], depsPath : File) {
    for (jar ← names) {
      this.getClass.synchronized({

        val out = new File(depsPath, jar);
        out.getParentFile.mkdirs();

        if (try {
          !out.exists() || !cachedSha(jar).equals(sha256(out.toPath()))
        } catch {
          case e : IOException ⇒
            false // just continue
        }) {
          Files.deleteIfExists(out.toPath)
          try {
            Files.copy(getClass().getResourceAsStream("/deps/" + jar), out.toPath)
          } catch {
            case e : NoSuchFileException ⇒
              throw new IllegalStateException(s"deps directory apparently inexistent.\nWas looking for $jar", e)
          }
        }
      })
    }
  }

  private val shaCache = new HashMap[String, String]
  private final def cachedSha(name : String) : String = shaCache.getOrElseUpdate(name, sha256ForRessource(name))

  @inline final def sha256ForRessource(name : String) : String = {
    val in = getClass().getResourceAsStream("/deps/" + name)
    val bytes = Stream.continually(in.read).takeWhile(_ != -1).map(_.toByte).toArray
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02X".format(_)).mkString
  }
  @inline final def sha256(path : Path) : String = {
    val bytes = Files.readAllBytes(path)
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02X".format(_)).mkString
  }
}
