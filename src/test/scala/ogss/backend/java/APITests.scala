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
 * Generic API tests built for Java.
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class APITests extends GenericAPITests with IRUtils {

  override val language = "java"

  override def deleteOutDir(out: String) {}

  /**
   * Back-end-specific operations. The back-end is reset for each specification
   * to allow caching
   */
  var gen: Main = _
  def escaped(s: String) = gen.escaped(s)

  override def callMainFor(name: String, source: String, options: Seq[String]) {
    CommandLine.main(
      Array[String](
        "build",
        source,
        "--debug-header",
        "-c",
        "-L",
        "java",
        "-p",
        name,
        "-Ojava:SuppressWarnings=true",
        "-d",
        "testsuites/java/lib",
        "-o",
        "testsuites/java/src/main/java/"
      ) ++ options)
  }

  def newTestFile(packagePath: String, name: String): PrintWriter = {
    gen = new Main

    val f = new File(
      s"testsuites/java/src/test/java/$packagePath/Generic${name}Test.java")
    f.getParentFile.mkdirs
    if (f.exists)
      f.delete
    f.createNewFile
    val rval = new PrintWriter(
      new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""package $packagePath;

import org.junit.Assert;
import org.junit.Test;

import $packagePath.OGFile;

import ogss.common.java.api.Mode;
import ogss.common.java.api.OGSSException;

/**
 * Tests the file reading capabilities.
 */
@SuppressWarnings("static-method")
public class Generic${name}Test extends common.CommonTest {
""")
    rval
  }

  def closeTestFile(out: java.io.PrintWriter) {
    out.write("""
}
""")
    out.close
  }

  def makeSkipTest(out: PrintWriter,
                   kind: String,
                   name: String,
                   testName: String,
                   accept: Boolean) {
    out.write(s"""
    @Test
    public void APITest_${escaped(kind)}_${name}_skipped_${escaped(testName)}() {${if (accept)
      ""
    else """
         Assert.fail("The test was skipped by the test generator.");"""}
    }
""")
  }

  def makeRegularTest(out: PrintWriter,
                      kind: String,
                      name: String,
                      testName: String,
                      accept: Boolean,
                      tc: TypeContext,
                      obj: JSONObject) {
    out.write(s"""
    @Test${if (accept) "" else "(expected = OGSSException.class)"}
    public void APITest_${escaped(kind)}_${name}_${if (accept) "acc" else "fail"}_${escaped(
      testName)}() throws Exception {
        OGFile sf = OGFile.open(tmpFile("$testName.sg"), Mode.Create, Mode.Write);

        // create objects${createObjects(obj, tc, name)}
        // set fields${setFields(obj, tc)}
        sf.close();

        // read back and assert correctness
        try (OGFile sf2 = OGFile.open(sf.currentPath(), Mode.Read, Mode.ReadOnly)) {
            // check count per Type${createCountChecks(obj, tc, name)}
            // create objects from file${createObjects2(obj, tc, name)}
            // assert fields${assertFields(obj, tc)}
        } catch (AssertionError e) {
            throw new OGSSException(e);
        }
    }
""")
  }

  private def typ(tc: TypeContext, name: String): String = {
    val n = name.toLowerCase()
    try {
      escaped(
        capital(
          (tc.classes ++ tc.interfaces)
            .filter(d ⇒ lowercase(d.name).equals(n))
            .head
            .name))
    } catch {
      case e: NoSuchElementException ⇒
        fail(s"Type '$n' does not exist, fix your test description!")
    }
  }

  private def isSingleton(tc: TypeContext, name: String): Boolean = {
    val n = name.toLowerCase()
    try {
      return !((tc.classes ++ tc.interfaces)
        .filter { d ⇒
          lowercase(d.name).equals(n)
        }
        .head
        .attrs
        .find { a ⇒
          "singleton".equals(a.name.toLowerCase)
        }
        .isEmpty)
    } catch {
      case e: NoSuchElementException ⇒
        fail(s"Type '$n' does not exist, fix your test description!")
    }
  }

  private def field(tc: TypeContext, typ: String, field: String) = {
    val tn = typ.toLowerCase()
    val t = tc.classes.find(d ⇒ lowercase(d.name).equals(tn)).get
    val fn = field.toLowerCase()

    val fs = allFields(t)
    fs.find(d ⇒ lowercase(d.name).equals(fn))
      .getOrElse(
        fail(s"Field '$fn' does not exist, fix your test description!")
      )
  }

  private def equalValue(left: String, v: Any, f: Field): String =
    equalValue(left, v, f.`type`)

  private def equalValue(left: String, v: Any, t: Type): String = t match {
    case t: BuiltinType ⇒
      t.name.ogss match {
        case _ if JSONObject.NULL == v ⇒ s"$left == null"
        case "String" if null != v ⇒
          s"""$left != null && $left.equals("${v.toString()}")"""
        case "I8" ⇒ s"$left == (byte)" + v.toString()
        case "I16" ⇒ s"$left == (short)" + v.toString()
        case "I32" ⇒ s"$left == " + v.toString()
        case "F32" ⇒ s"$left == (float)" + v.toString()
        case "F64" ⇒ s"$left == (double)" + v.toString()
        case "V64" | "I64" ⇒ s"$left == " + v.toString() + "L"
        case _
            if null != v && !v.toString().equals("null") && !v
              .toString()
              .equals("true") && !v.toString().equals("false") ⇒
          s"$left == " + v.toString() + "_2"
        case _ ⇒ s"$left == " + v.toString()
      }

    case t: SeqType ⇒
      v match {
        case null | JSONObject.NULL ⇒ s"$left == null"
        case v: JSONArray ⇒
          v.iterator()
            .asScala
            .toArray
            .map(value(_, t.baseType, "_2"))
            .mkString(
              t match {
                case t: ListType ⇒ s"$left != null && $left.equals(list("
                case t: SetType ⇒ s"$left != null && $left.equals(set("
                case _ ⇒ s"$left != null && $left.equals(array("
              },
              ", ",
              "))"
            )
            .replace("java.util.", "")
      }

    case t: MapType if v != null ⇒
      s"$left != null && $left.equals(" + valueMap(
        v.asInstanceOf[JSONObject],
        t.keyType,
        t.valueType,
        "_2") + ")"

    case _ ⇒
      if (v == null || v.toString().equals("null"))
        s"$left == (${gen.mapType(t)}) null"
      else s"$left == ${v.toString()}_2"
  }

  //private def value(v : Any, f : Field, suffix : String = "") : String = value(v, f.getType, suffix)

  private def value(v: Any, t: Type, suffix: String = ""): String =
    if (null == v || JSONObject.NULL == v)
      "null"
    else
      t match {
        case t: BuiltinType ⇒
          t.name.ogss match {
            case "String" if null != v ⇒ s""""${v.toString()}""""
            case "I8" ⇒ "(byte)" + v.toString()
            case "I16" ⇒ "(short)" + v.toString()
            case "I32" ⇒ v.toString()
            case "F32" ⇒ "(float)" + v.toString()
            case "F64" ⇒ "(double)" + v.toString()
            case "V64" | "I64" ⇒ v.toString() + "L"
            case _ if null != v ⇒ v.toString() + suffix
            case _ ⇒ v.toString()
          }

        case t: SeqType ⇒
          v.asInstanceOf[JSONArray]
            .iterator()
            .asScala
            .toArray
            .map(value(_, t.baseType, suffix))
            .mkString(t match {
              case t: ListType ⇒ "list("
              case t: SetType ⇒ "set("
              case _ ⇒ "array("
            }, ", ", ")")
            .replace("java.util.", "")

        case t: MapType if v != null ⇒
          valueMap(v.asInstanceOf[JSONObject], t.keyType, t.valueType, suffix)

        case _ ⇒
          if (v == null || v.toString().equals("null"))
            s"(${gen.mapType(t)}) null"
          else v.toString() + suffix
      }

  private def valueMap(obj: JSONObject,
                       k: Type,
                       v: Type,
                       suffix: String = ""): String = {
    var rval = s"map()"

    // https://docs.scala-lang.org/overviews/collections/maps.html#operations-in-class-map
    // ms put (k, v) Adds mapping from key k to value v to ms and returns any value previously associated with k as an option.
    for (name ← JSONObject.getNames(obj)) {
      rval =
        s"put($rval, ${value(name, k, suffix)}, ${value(obj.get(name), v, suffix)})"
    }

    rval
  }

  private def createCountChecks(obj: JSONObject,
                                tc: TypeContext,
                                packagePath: String): String =
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val objCountPerType = scala.collection.mutable.Map[String, Int]()
      for (name ← JSONObject.getNames(obj)) {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);
        if (!objCountPerType.contains(typeName)) {
          objCountPerType(typeName) = 0
        }
        objCountPerType(typeName) =
          if (isSingleton(tc, t)) 1
          else objCountPerType(typeName) + 1
      }

      val rval = for ((typeName, objCount) ← objCountPerType) yield {
        s"""
            Assert.assertEquals($objCount, sf.${typeName}s.staticSize());"""
      }

      rval.mkString
    }

  private def createObjects2(obj: JSONObject,
                             tc: TypeContext,
                             packagePath: String): String =
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);

        if (isSingleton(tc, t)) s"""
            $packagePath.$typeName ${name}_2 = sf2.${typeName}s.get();"""
        else s"""
            $packagePath.$typeName ${name}_2 = sf2.${typeName}s.get($name.ID());"""
      }

      rval.mkString
    }

  private def createObjects(obj: JSONObject,
                            tc: TypeContext,
                            packagePath: String): String =
    if (null == JSONObject.getNames(obj)) {
      ""
    } else {

      val rval = for (name ← JSONObject.getNames(obj)) yield {
        val x = obj.getJSONObject(name)
        val t = JSONObject.getNames(x).head;

        val typeName = typ(tc, t);

        if (isSingleton(tc, t)) s"""
        $packagePath.$typeName $name = sf.${typeName}s.get();"""
        else s"""
        $packagePath.$typeName $name = sf.${typeName}s.make();"""
      }

      rval.mkString
    }

  private def assertFields(obj: JSONObject, tc: TypeContext): String =
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
          val assignments = for (fieldName ← JSONObject.getNames(fs).toSeq)
            yield {
              val f = field(tc, t, fieldName)
              val getter = escaped("get" + capital(f.name))

              // do not check auto fields as they cannot obtain the stored value from file
              if (f.isTransient) ""
              else
                s"""
            Assert.assertTrue(${equalValue(
                  s"${name}_2.$getter()",
                  fs.get(fieldName),
                  f)});"""
            }

          assignments.mkString
        }
      }

      rval.mkString("\n")
    }

  private def setFields(obj: JSONObject, tc: TypeContext): String =
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
          val assignments = for (fieldName ← JSONObject.getNames(fs).toSeq)
            yield {
              val f = field(tc, t, fieldName)
              val setter = escaped("set" + capital(f.name))

              s"""
        $name.$setter(${value(fs.get(fieldName), f.`type`)});"""
            }

          assignments.mkString
        }
      }

      rval.mkString("\n")
    }
}
