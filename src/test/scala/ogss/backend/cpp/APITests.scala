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
package ogss.backend.cpp

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

import scala.collection.JavaConverters._
import scala.io.Source

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import ogss.oil.TypeContext
import ogss.oil.SetType
import ogss.oil.Field
import ogss.main.CommandLine
import ogss.oil.Type
import ogss.oil.MapType
import ogss.backend.common.GenericAPITests
import ogss.oil.BuiltinType
import ogss.oil.SeqType

/**
 * Generic API tests built for C++.
 *
 * @author Timm Felden
 */
@RunWith(classOf[JUnitRunner])
class APITests extends GenericAPITests {

  override val language = "cpp"

  var gen = new Main

  override def preferredTypeContext : TypeContext = IR.TypeContexts.asScala.find(tc ⇒ tc.getProjectedTypeDefinitions && tc.getProjectedInterfaces).get

  override def deleteOutDir(out : String) {
  }

  override def callMainFor(name : String, source : String, options : Seq[String]) {
    CommandLine.main(Array[String](
      "build",
      source,
      "--debug-header",
      "-c",
      "-L", "cpp",
      "-p", name,
      "-Ocpp:revealID=true",
      "-o", "testsuites/cpp/src/" + name
    ) ++ options)
  }

  def newTestFile(packagePath : String, name : String) : PrintWriter = {
    val packageName = packagePath.split("/").map(EscapeFunction.apply).mkString("::")
    gen = new Main
    gen.setPackage(List(packagePath))

    val f = new File(s"testsuites/cpp/test/$packagePath/generic${name}Test.cpp")
    f.getParentFile.mkdirs
    if (f.exists)
      f.delete
    f.createNewFile
    val rval = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8")))

    rval.write(s"""#include <gtest/gtest.h>
#include "../common/utils.h"
#include "../../src/$packagePath/File.h"

using ::$packageName::api::File;
using namespace common;
""")
    rval
  }

  def closeTestFile(out : java.io.PrintWriter) {
    out.write("""
""")
    out.close
  }

  def makeSkipTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean) {
    out.write(s"""
TEST(${name.capitalize}_APITest, ${gen.escaped(kind)}_skipped_${gen.escaped(testName)}) {${
      if (accept) ""
      else """
    GTEST_FAIL() << "The test was skipped by the test generator.";"""
    }
}
""")
  }

  override def makeRegularTest(out : PrintWriter, kind : String, name : String, testName : String, accept : Boolean, TC : TypeContext, obj : JSONObject) {
    out.write(s"""
TEST(${name.capitalize}_APITest, ${if (accept) "Acc" else "Fail"}_${gen.escaped(testName)}) {
    try {
        auto sf = common::tempFile<File>();

        // create objects${createObjects(obj, TC, name)}
        
        // set fields${setFields(obj, TC)}

        sf->close();

        auto sf2 = File::open(sf->currentPath());
        sf2->check();
    } catch (ogss::Exception& e) {${
      if (accept) """
        GTEST_FAIL() << "an exception was thrown:" << std::endl << e.what();
    }
    GTEST_SUCCEED();"""
      else """
        GTEST_SUCCEED();
        return;
    }
    GTEST_FAIL() << "expected an exception, but none was thrown.";"""
    }
}
""")
  }

  private def typ(tc : TypeContext, name : String) : String = {
    val n = name.toLowerCase()
    try {
      gen.name((tc.getClasses.asScala ++ tc.getInterfaces.asScala).filter(c ⇒ lowercase(c.getName).equals(n)).head)
    } catch {
      case e : NoSuchElementException ⇒ fail(s"Type '$n' does not exist, fix your test description!")
    }
  }

  private def field(tc : TypeContext, typ : String, field : String) = {
    val tn = typ.toLowerCase()
    val t = tc.getClasses.asScala.find(c ⇒ lowercase(c.getName).equals(tn)).get
    val fn = field.toLowerCase()
    try {
      allFields(t).find(f ⇒ lowercase(f.getName).equals(fn)).get
    } catch {
      case e : NoSuchElementException ⇒ fail(s"Field '$fn' does not exist, fix your test description!")
    }
  }

  private def value(v : Any, f : Field) : String = value(v, f.getType)

  private def value(v : Any, t : Type) : String = t match {
    case t : BuiltinType ⇒ t.getName.getOgss match {
      case "String" if null != v ⇒ s"""sf->strings->add("${v.toString()}")"""
      case "I8"                  ⇒ "(int8_t)" + v.toString()
      case "I16"                 ⇒ "(short)" + v.toString()
      case "F32"                 ⇒ "(float)" + v.toString()
      case "F64"                 ⇒ "(double)" + v.toString()
      case "V64" | "I64"         ⇒ v.toString() + "L"
      case _ ⇒
        if (null == v || v.toString().equals("null"))
          "nullptr"
        else
          v.toString()
    }

    case t : SeqType ⇒
      locally {
        var rval = t match {
          case t : SetType ⇒ s"set<${gen.mapType(t.getBaseType)}>()"
          case _           ⇒ s"array<${gen.mapType(t.getBaseType)}>()"
        }
        for (x ← v.asInstanceOf[JSONArray].iterator().asScala) {
          rval = s"put<${gen.mapType(t.getBaseType)}>($rval, ${value(x, t.getBaseType)})"
        }
        rval
      }

    case t : MapType if v != null ⇒ valueMap(v.asInstanceOf[JSONObject], t.getKeyType, t.getValueType)

    case _ ⇒
      if (null == v || v.toString().equals("null"))
        "nullptr"
      else
        v.toString()
  }

  private def valueMap(obj : JSONObject, k : Type, v : Type) : String = {
    var rval = s"map<${gen.mapType(k)}, ${gen.mapType(v)}>()"

    for (name ← JSONObject.getNames(obj)) {
      rval = s"put<${gen.mapType(k)}, ${gen.mapType(v)}>($rval, ${value(name, k)}, ${value(obj.get(name), v)})"
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
        auto $name = sf->${typeName}->make();"""
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
            val setter = gen.setter(f)
            s"""
        $name->$setter(${value(fs.get(fieldName), f)});"""
          }

          assignments.mkString
        }
      }

      rval.mkString("\n")
    }
  }
}
