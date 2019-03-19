/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.main.frontend

import java.io.File
import org.junit.runner.RunWith
import org.scalatest.exceptions.TestFailedException
import ogss.main.CommandLine
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import ogss.frontend.common.ParseException

/**
 * Contains generic parser tests based on src/test/resources/frontend directory.
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class GenericFrontendTest extends FunSuite {

  CommandLine.exit = { s ⇒ fail(s) }
  private def check(file : File) = CommandLine.main(Array[String](
    "build",
    file.getAbsolutePath,
    "-o", "/tmp/gen"
  ))

  def fail[E <: Exception](f : ⇒ Unit)(implicit manifest : scala.reflect.Manifest[E]) : E = try {
    f;
    fail(s"expected ${manifest.runtimeClass.getName()}, but no exception was thrown");
  } catch {
    case e : TestFailedException ⇒ throw e
    case e : E ⇒
      println(e.getMessage()); e
    case e : Throwable ⇒ e.printStackTrace(); assert(e.getClass() === manifest.runtimeClass); null.asInstanceOf[E]
  }

  def succeedOn(file : File) {
    test("succeed on " + file.getName()) { check(file) }
  }

  def failOn(file : File) {
    test("fail on " + file.getName()) {
      fail[ParseException] {
        check(file)
      }
    }
  }

  for (
    f ← new File("src/test/resources/frontend").listFiles() if f.isFile()
  ) succeedOn(f)
  for (
    f ← new File("src/test/resources/frontend/fail").listFiles() if f.isFile()
  ) failOn(f)
}
