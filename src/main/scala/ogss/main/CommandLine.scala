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
package ogss.main

import java.io.File

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import ogss.BuildInfo
import ogss.common.scala.api.Create
import ogss.common.scala.api.Read
import ogss.common.scala.api.ReadOnly
import ogss.common.scala.api.Write
import ogss.io.PrintingService
import ogss.oil.OGFile
import ogss.util.HeaderInfo
import ogss.util.IRChecks
import ogss.util.IRUtils

object CommandLine {

  /**
   * configurable exit method called on error
   * @note the purpose of changing this is to replace it with a failure method in unit tests
   */
  var exit : String ⇒ Unit = { s ⇒ System.err.println(s); System.exit(0) }

  /**
   * print an error message and quit
   */
  def error(msg : String) : Nothing = {
    exit(msg)
    ???
  }

  def main(args : Array[String]) : Unit = {
    if (args.isEmpty)
      CLIParser.parse(Array("-h"), CLIConfig())
    else
      CLIParser.parse(args, CLIConfig()).getOrElse(error("")).process
  }

  case class CLIConfig(
    command :         String                                         = null,
    target :          File                                           = null,
    outdir :          File                                           = new File("."),
    var depsdir :     File                                           = null,
    var skipDeps :    Boolean                                        = false,
    clean :           Boolean                                        = false,
    cleanMode :       String                                         = null,
    header :          HeaderInfo                                     = new HeaderInfo(),
    var languages :   Set[String]                                    = Set(),
    languageOptions : HashMap[String, ArrayBuffer[(String, String)]] = new HashMap(),
    packageName :     Seq[String]                                    = Seq[String]("generated"),
    visitors :        Seq[String]                                    = Seq[String]()
  ) {
    def process = command match {
      case null    ⇒
      case "build" ⇒ build
      case "cfm"   ⇒ printCFM
      case "list"  ⇒ list
    }

    def list {
      println("Front-Ends:")
      println("[extension] [name]   [description]")
      for (t ← KnownFrontEnds.all)
        println(f"  *${t.extension}%-8s ${t.name}%-8s ${t.description}")

      println("\nBack-Ends:")
      println("  [name]   [description]")
      for (t ← KnownBackEnds.all)
        println(f"  ${t.name}%-8s ${t.description}")
    }

    def printCFM {
      println("These back-ends support custom fields:")
      for (t ← KnownBackEnds.all) {
        val cfm = t.customFieldManual
        if (null != cfm)
          println(s"\n${t.name}:\n${cfm}")
      }
    }

    def build {
      // find and execute correct frond-end
      val frontEnd = KnownFrontEnds.forFile(target)

      if (null == depsdir)
        depsdir = outdir

      // write IR to temporary file, so we do not have to care about misbehaving back-ends
      val tmpPath = File.createTempFile("ogss", ".oil")
      tmpPath.deleteOnExit()

      frontEnd.out = OGFile.open(tmpPath, Create, Write)
      frontEnd.run(target)
      val IR = frontEnd.out

      try {
        IRChecks.check(IR)
      } catch {
        case e : IllegalStateException ⇒ error("IR check failed: " + e.getMessage)
      }

      // ensure that we do not modify an existing file accidentally
      if (IR.currentPath != tmpPath.toPath()) {
        IR.changePath(tmpPath.toPath())
        IR.close()
      }

      val failures = HashMap[String, Exception]()
      for (lang ← languages.par) {
        val outIR = OGFile.open(IR.currentPath, Read, ReadOnly)
        val backEnd = KnownBackEnds.forLanguage(lang)

        val pathPostfix =
          // if we process a single language only, the outdir is the target for the language. Otherwise, languages get
          // their own dirs in a subdirectory
          if (1 == languages.size) ""
          else "/generated/" + lang

        // set options
        for ((k, v) ← languageOptions.getOrElse(lang, new ArrayBuffer())) try {
          backEnd.setOption(k.toLowerCase, v)
        } catch {
          case e : RuntimeException ⇒
            println(s"""Warning ($lang): ignored option
$k = $v
Valid options are:
${
              backEnd.describeOptions.map(opt ⇒ f"""
    ${opt.name}%-18s ${opt.values}%-16s ${opt.description}""").mkString
            }
""")
        }

        backEnd.setIR(outIR)
        backEnd.setPackage(packageName.toList)
        val printService = new PrintingService(
          new File(new File(outdir, pathPostfix), backEnd.packageDependentPathPostfix),
          backEnd.makeHeader(header.copy())
        )
        backEnd.files = printService
        backEnd.depsPath = new File(depsdir, pathPostfix)
        backEnd.skipDependencies = skipDeps

        for (id ← outIR.Identifier)
          if (visitors.contains(IRUtils.lowercase(id)))
            backEnd.visited += id

        if (clean) {
          (if (null == cleanMode) backEnd.defaultCleanMode
          else cleanMode) match {
            case "none" ⇒ // done
            case "file" ⇒ printService.deleteForeignFiles(false)
            case "dir"  ⇒ printService.deleteForeignFiles(true)
            case "wipe" ⇒ printService.wipeOutPath()
            case s      ⇒ throw new IllegalStateException("unknown clean mode: " + s)
          }
        }

        try {
          backEnd.make
          backEnd.makeDeps
          println("-done-")
        } catch {
          case e : Exception ⇒ println(s"-FAILED- ($lang)"); this.synchronized(failures(lang)= e)
        }
      }

      // report failures
      if (!failures.isEmpty) {
        if (1 == failures.size) {
          //rethrow
          throw failures.head._2
        } else {
          error((
            for ((lang, err) ← failures) yield {
              err.printStackTrace();
              s"$lang failed with message: ${err.getMessage}}"
            }
          ).mkString("\n"))
        }
      }
    }
  }
  val CLIParser = new scopt.OptionParser[CLIConfig]("ogss") {
    head("ogss", BuildInfo.version)

    help("help").abbr("h").text("prints this usage text")
    note("")

    cmd("list")
      .action((_, c) ⇒ c.copy(command = "list"))
      .text("list available front- and back-ends")
      .children(
        note("")
      )

    cmd("custom-field-manual")
      .abbr("cfm")
      .action((_, c) ⇒ c.copy(command = "cfm"))
      .text("explain custom fields for back-ends that support them")
      .children(
        note("")
      )

    KnownBackEnds.all.filterNot(_.describeOptions.isEmpty).foldLeft(
      cmd("build")
        .action((_, c) ⇒ c.copy(command = "build"))
        .text("build code out of a specification")
        .children(

          arg[File]("<target>")
            .required()
            .action((x, c) ⇒ c.copy(target = x))
            .text("the target specification"),

          opt[File]('o', "outdir").optional().action(
            (p, c) ⇒ c.copy(outdir = p)
          ).text("set the output directory"),

          opt[Unit]('c', "clean").optional().action(
            (p, c) ⇒ c.copy(clean = true)
          ).text("clean output directory after creating source files"),

          opt[String]("clean-mode").optional().action(
            (p, c) ⇒ c.copy(cleanMode = p)
          ).text("""possible modes are:
     (unspecified)   the back-end use their defaults
              none   no cleaning at all (pointless on cli)
              file   files are deleted in every folder that contains files directly
              dir    everything is deleted in every folder containing files directly
              wipe   the output directory is wiped from foreign files recursively
"""),

          opt[File]('d', "depsdir").optional().action(
            (p, c) ⇒ c.copy(depsdir = p)
          ).text("set the dependency directory (libs, common sources)"),

          opt[Unit]("skip-dependencies").optional().action(
            (p, c) ⇒ c.copy(skipDeps = true)
          ).text("do not copy dependencies"),

          opt[String]('p', "package").optional().action(
            (s, c) ⇒ c.copy(packageName = s.split('.'))
          ).text("set a package name used by all emitted code"),

          opt[String]('F', "front-end").optional().unbounded().validate(
            lang ⇒
              if (KnownFrontEnds.all.map(_.name.toLowerCase).contains(lang.toLowerCase)) success
              else failure(s"Language $lang is not known and can therefore not be used!")
          ).action((lang, c) ⇒ lang match {
              case "all" ⇒ c.copy(languages = c.languages ++ KnownFrontEnds.all.map(_.name.toLowerCase).to)
              case lang  ⇒ c.copy(languages = c.languages + lang.toLowerCase)
            }).text("force usage of a specific front-end irrespective of <target>s extension"),

          opt[String]('L', "language").optional().unbounded().validate(
            lang ⇒
              if (KnownBackEnds.all.map(_.name.toLowerCase).contains(lang.toLowerCase)) success
              else failure(s"Language $lang is not known and can therefore not be used!")
          ).action((lang, c) ⇒ lang match {
              case "all" ⇒ c.copy(languages = c.languages ++ KnownBackEnds.all.map(_.name.toLowerCase).to)
              case lang  ⇒ c.copy(languages = c.languages + lang.toLowerCase)
            }),

          opt[Seq[String]]('v', "visitors").optional().action(
            (v, c) ⇒ c.copy(visitors = v)
          ).text("types to generate visitors for"),

          note(""),

          opt[String]("header1").abbr("h1").optional().action {
            (s, c) ⇒ c.header.line1 = Some(s); c
          }.text("overrides the content of the respective header line"),
          opt[String]("header2").abbr("h2").optional().action {
            (s, c) ⇒ c.header.line2 = Some(s); c
          }.text("overrides the content of the respective header line"),
          opt[String]("header3").abbr("h3").optional().action {
            (s, c) ⇒ c.header.line3 = Some(s); c
          }.text("overrides the content of the respective header line"),

          opt[String]('u', "user-name").optional().action {
            (s, c) ⇒ c.header.userName = Some(s); c
          }.text("set a user name"),
          opt[String]("date").optional().action {
            (s, c) ⇒ c.header.date = Some(s); c
          }.text("set a custom date"),
          opt[String]("license").optional().action {
            (s, c) ⇒ c.header.license = Some(s); c
          }.text("set a license text"),

          opt[Unit]("debug-header").action {
            (s, c) ⇒
              c.header.userName = Some("<<some developer>>")
              c.header.line2 = Some("<<debug>>")
              c
          }.text("set debugging and diff friendly header content"),

          note("")
        )
    ) {
        case (cmd, lang) ⇒
          val name = lang.name.toLowerCase()
          cmd.children(
            opt[(String, String)](s"set-$name-option").abbr(s"O$name").optional().unbounded().action {
              (p, c) ⇒ c.languageOptions.getOrElseUpdate(name, new ArrayBuffer()).append(p); c
            }.text(lang.describeOptions.map(opt ⇒ f"""
    ${opt.name}%-18s ${opt.values}%-16s ${opt.description}""").mkString + "\n")
          )
      }

    override def terminate(s : Either[String, Unit]) {
      s.fold(exit, identity)
    }
  }
}
