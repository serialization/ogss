/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
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
  private final def cachedSha(name : String) : String = shaCache.getOrElseUpdate(name, sha256(new File(name).toPath))

  @inline final def sha256(path : Path) : String = {
    val bytes = Files.readAllBytes(path)
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02X".format(_)).mkString
  }
}
