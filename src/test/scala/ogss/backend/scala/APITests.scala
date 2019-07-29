/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.scala

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

import scala.collection.JavaConverters.asScalaIteratorConverter

import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import ogss.backend.common.GenericAPITests
import ogss.main.CommandLine
import ogss.oil.BuiltinType
import ogss.oil.Field
import ogss.oil.ListType
import ogss.oil.MapType
import ogss.oil.SeqType
import ogss.oil.SetType
import ogss.oil.Type
import ogss.oil.TypeContext
import ogss.util.IRUtils

/**
 * Generic API tests built for Scala
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class APITests extends GenericAPITests with IRUtils {

  override val language = "scala"

  val generator = new Main
  import generator._

  override def deleteOutDir(out : String) {
  }

  override def callMainFor(name : String, source : String, options : Seq[String]) {
    CommandLine.main(Array[String](
      "build",
      source,
      "--debug-header",
      "-L", "scala",
      "-p", name,
      "-d", "testsuites/scala/lib",
      "-o", "testsuites/scala/src/main/scala"
    ) ++ options)
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter = {
    generator.setPackage(List(packagePath))

    val f = new File(s"testsuites/scala/src/test/scala/$packagePath/APITest.generated.scala")
    f.getParentFile.mkdirs
    if (f.exists)
      f.delete
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""package $packagePath

import java.nio.file.Path

import org.junit.Assert

import ogss.common.scala.api.Write
import ogss.common.scala.api.Create

import common.CommonTest

/**
 * Tests the file reading capabilities.
 */
class GenericAPITest extends CommonTest {
""")
    rval
  }

  def closeTestFile(out : java.io.PrintWriter) {
    out.write("""
}
""")
    out.close
  }

  def makeSkipTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean) {
    out.write(s"""
    test("API test - $kind $name skipped : ${testName}") {${
      if (accept) ""
      else """
         fail("The test was skipped by the test generator.");"""
    }
    }
""")
  }

  def makeRegularTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean, tc : TypeContext, obj : JSONObject) {
    out.write(s"""
    test("API test - $kind $name ${if (accept) "acc" else "fail"} : ${testName}") (${
      if (accept) ""
      else "try"
    }{
        val sf = OGFile.open(tmpFile("$testName.sf"), Create, Write);

        // create objects${createObjects(obj, tc, name)}
        
        // set fields${setFields(obj, tc)}

        sf.close${
      if (accept) ""
      else """

        fail("expected failure, but nothing happended")
    } catch {
      case e : Exception ⇒"""
    }
    })
""")
  }

  private def typ(tc : TypeContext, name : String) : String = {
    val n = name.toLowerCase()
    try {
      escaped(capital((tc.classes ++ tc.interfaces).filter(d ⇒ lowercase(d.name).equals(n)).head.name))
    } catch {
      case e : NoSuchElementException ⇒ fail(s"Type '$n' does not exist, fix your test description!")
    }
  }

  private def field(tc : TypeContext, typ : String, field : String) = {
    val tn = typ.toLowerCase()
    val t = tc.classes.find(d ⇒ lowercase(d.name).equals(tn)).get
    val fn = field.toLowerCase()

    val fs = allFields(t)
    fs.find(d ⇒ lowercase(d.name).equals(fn)).getOrElse(
      fail(s"Field '$fn' does not exist, fix your test description!")
    )
  }

  private def value(v : Any, f : Field) : String = value(v, f.`type`)

  private def value(v : Any, t : Type) : String = t match {
    case t : BuiltinType ⇒
      lowercase(t.name) match {
        case "string"      ⇒ s""""${v.toString()}""""
        case "i8"          ⇒ v.toString() + ".toByte"
        case "i16"         ⇒ v.toString() + ".toShort"
        case "f32"         ⇒ v.toString() + ".toFloat"
        case "f64"         ⇒ v.toString()
        case "v64" | "i64" ⇒ v.toString() + "L"
        case _             ⇒ v.toString()
      }

    case t : SeqType ⇒ v match {
      case null | JSONObject.NULL ⇒ "null"
      case v : JSONArray ⇒ v.iterator().asScala.toArray.map(value(_, t.baseType)).mkString(t match {
        case t : ListType ⇒ "list("
        case t : SetType  ⇒ "set("
        case _            ⇒ "array("
      }, ", ", ")").replace("java.util.", "")
    }

    case t : MapType if v != null ⇒ valueMap(v.asInstanceOf[JSONObject], t.keyType, t.valueType)

    case _                        ⇒ v.toString()
  }

  private def valueMap(obj : JSONObject, k : Type, v : Type, suffix : String = "") : String = {
    var rval = "map"

    // https://docs.scala-lang.org/overviews/collections/maps.html#operations-in-class-map
    // ms put (k, v) Adds mapping from key k to value v to ms and returns any value previously associated with k as an option.
    for (name ← JSONObject.getNames(obj)) {
      rval = s"put($rval, ${value(name, k)}, ${value(obj.get(name), v)})"
    }

    rval
  }

  private def createObjects(obj : JSONObject, tc : TypeContext, packagePath : String) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);

        s"""
        val $name = sf.${typeName}.make;"""
      }

      rval.mkString
    }
  }

  private def setFields(obj : JSONObject, tc : TypeContext) : String = {
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;
        val fs = x.getJSONObject(t);

        if (null == JSONObject.getNames(fs))
          ""
        else {
          val assignments = for (fieldName ← JSONObject.getNames(fs).toSeq) yield {
            val f = field(tc, t, fieldName)
            s"""
        $name.${generator.getter(f)} = ${value(fs.get(fieldName), f)};"""
          }

          assignments.mkString
        }
      }

      rval.mkString("\n")
    }
  }
}
