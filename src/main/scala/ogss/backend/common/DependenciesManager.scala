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
import java.security.MessageDigest
import java.util.jar.JarFile

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

/**
 * creates copies of required dependencies in $outPath
 * @author Timm Felden
 */
object DependenciesMaker {

  /**
   * Copy a list of dependencies from /deps/ to the target output directory
   */
  def copyDeps(names : Seq[String], depsPath : File) {
    for (source ← names) {
      this.getClass.synchronized({
        val out = new File(depsPath, source);
        out.getParentFile.mkdirs();

        // check if the target resource is identical, otherwise replace it entirely
        if (try {
          !out.exists() || !cachedSha(source).equals(mkString(sha256(out)))
        } catch {
          case e : IOException ⇒
            false // just continue
        }) {
          copyResource("/deps/" + source, out)
        }
      })
    }
  }

  private val shaCache = new HashMap[String, String]
  private final def cachedSha(name : String) : String = shaCache.getOrElseUpdate(name, sha256ForRessource(name))

  @inline final def sha256ForRessource(name : String) : String = {
    val resourceName = "/deps/" + name
    // check for folders
    if (resourceIsDirectory(resourceName)) {

      val ress = getClass().getResource(resourceName)

      // check if resource is a file
      if (null != ress && "file".equals(ress.getProtocol)) {
        return mkString(sha256(new File(ress.getFile)))
      } else {
        // it is inside a jar (because we do not support other resources here)
        // @note we assume the ogss.jar to reside on the file system
        val jarFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getFile)
        if (jarFile.isDirectory()) {
          // the jar is not packaged, hence we can simply append the path
          return mkString(sha256(new File(jarFile.getPath + resourceName)))

        } else {
          // @note in case of directories copied from jars, we do not need to perform the operation recursively, as the
          // jar will list all files located in subdirectories
          val jar = new JarFile(jarFile)
          val baseEntry = jar.getEntry(resourceName.substring(1))
          if (null == baseEntry) {
            throw new IllegalStateException(s"resource $resourceName is not located in jar ${jarFile.getPath}")
          }
          val base = baseEntry.getName
          val es = jar.entries();
          val data = new ArrayBuffer[Array[Byte]]

          // add base name
          {
            val ns = base.split('/')
            // last name is empty, so the file name is the string before it
            data += ns(ns.size - 1).getBytes
          }

          // collect other bytes influencing the sum
          while (es.hasMoreElements()) {
            val entry = es.nextElement()
            val entryName = entry.getName
            if (entryName != base && entryName.startsWith(base)) {
              if (entry.isDirectory()) {
                val ns = entry.getName.split('/')
                // last name is empty, so the file name is the string before it
                data += ns(ns.size - 1).getBytes
              } else {
                val in = getClass().getResourceAsStream("/" + entryName)
                data += Stream.continually(in.read).takeWhile(_ != -1).map(_.toByte).toArray
              }
            }
          }
          val sums = data.map(bytes ⇒ MessageDigest.getInstance("SHA-256").digest(bytes))
          return mkString(sums.reduce[Array[Byte]] { case (l, r) ⇒ l.zip(r).map { case (l, r) ⇒ (l ^ r).toByte } })
        }
      }

    } else {
      // a flat file
      val in = getClass().getResourceAsStream(resourceName)
      val bytes = Stream.continually(in.read).takeWhile(_ != -1).map(_.toByte).toArray

      return MessageDigest.getInstance("SHA-256").digest(bytes).map("%02X".format(_)).mkString
    }
  }

  final def sha256(target : File) : Array[Byte] = {
    if (target.isDirectory()) {
      val r = MessageDigest.getInstance("SHA-256").digest(target.getName.getBytes)
      // fold bytes with ^ because it is insensitive to order!
      target.listFiles().map(sha256).foldLeft(r) { case (l, r) ⇒ l.zip(r).map { case (l, r) ⇒ (l ^ r).toByte } }

    } else {
      MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target.toPath()))
    }
  }

  final def mkString(shaSum : Array[Byte]) : String = shaSum.map("%02X".format(_)).mkString

  private def resourceIsDirectory(name : String) : Boolean = {
    val ress = getClass().getResource(name)

    // check if resource is a file
    if (null != ress && "file".equals(ress.getProtocol)) {
      new File(ress.getFile).isDirectory()
    } else {
      // it is inside a jar (because we do not support other resources here)
      // @note we assume the ogss.jar to reside on the file system
      val jarFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getFile)
      if (jarFile.isDirectory()) {
        // the jar is not packaged, hence we can simply append the path
        val target = new File(jarFile.getPath + name)
        if (!target.exists()) {
          throw new IllegalStateException(s"resource ${target.getAbsolutePath} is required but does not exist")
        }
        target.isDirectory()

      } else {
        val jar = new JarFile(jarFile)
        val entry = jar.getEntry(name.substring(1))
        if (null == entry) {
          throw new IllegalStateException(s"resource $name is not located in jar ${jarFile.getPath}")
        }
        entry.isDirectory()
      }
    }
  }

  private def copyResource(name : String, target : File) {
    if (resourceIsDirectory(name)) {
      copyResourceDirectory(name, target)

    } else {
      copyResourceFile(name, target)
    }
  }

  private def copyResourceFile(name : String, target : File) {
    target.delete()
    // ensure that parent directory exists; this may not be the case if copy originates from a jar
    target.getParentFile.mkdirs()
    try {
      Files.copy(getClass().getResourceAsStream(name), target.toPath)
    } catch {
      case e : NoSuchFileException ⇒
        throw new IllegalStateException(s"deps directory apparently inexistent.\n  I was looking for $name", e)
    }
  }

  private def copyResourceDirectory(name : String, target : File) {
    deleteDir(target)
    target.mkdir();

    val ress = getClass().getResource(name)

    // check if resource is a file
    if (null != ress && "file".equals(ress.getProtocol)) {
      for (child ← new File(ress.getFile).listFiles()) {
        copyResource(s"$name/${child.getName}", new File(target, child.getName))
      }
    } else {
      // it is inside a jar (because we do not support other resources here)
      // @note we assume the ogss.jar to reside on the file system
      val jarFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getFile)
      if (jarFile.isDirectory()) {
        // the jar is not packaged, hence we can simply append the path
        for (child ← new File(jarFile.getPath + name).listFiles()) {
          copyResource(s"$name/${child.getName}", new File(target, child.getName))
        }

      } else {
        // @note in case of directories copied from jars, we do not need to perform the operation recursively, as the
        // jar will list all files located in subdirectories
        val jar = new JarFile(jarFile)
        val baseEntry = jar.getEntry(name.substring(1))
        if (null == baseEntry) {
          throw new IllegalStateException(s"resource $name is not located in jar ${jarFile.getPath}")
        }
        val base = baseEntry.getName
        val es = jar.entries();
        while (es.hasMoreElements()) {
          val entry = es.nextElement()
          if (!entry.isDirectory()) {
            val entryName = entry.getName
            if (entryName != base && entryName.startsWith(base)) {
              copyResourceFile("/" + entryName, new File(target, entryName.substring(base.length())))
            }
          }
        }
      }
    }
  }

  /**
   * Ensure that a directory does not exist
   */
  private def deleteDir(target : File) {
    if (!target.exists()) {
      return
    }

    if (target.isDirectory()) {
      target.listFiles().foreach(deleteDir)
    }
    target.delete();
  }
}
