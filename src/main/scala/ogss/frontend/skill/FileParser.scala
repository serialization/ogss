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
package ogss.frontend.skill

import java.io.File

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.util.parsing.input.Positional

import ogss.frontend.common.FrontEnd
import ogss.oil.Comment
import ogss.oil.Identifier

/**
 * Parse a file into definitions. A three-pass approach allows us to create
 * ir.Objects using the parser.
 */
class FileParser(self : FrontEnd) extends CommonParseRules(self) {

  protected var currentFile : File = _
  protected val seen = new HashSet[File]

  val definitions = new HashMap[Identifier, Definition]

  /**
   * Files may start with an arbitrary of lines starting with '#'
   * Theses lines sereve as true comments and do not affect the specification.
   */
  protected def headComment = rep("""^#[^\r\n]*[\r\n]*""".r)

  /**
   * Includes are just strings containing relative paths to *our* path.
   */
  protected def includes = ("include" | "with") ~> rep(
    string ^^ { s ⇒ process(new File(currentFile.getParentFile, s).getAbsoluteFile) }
  );

  // content = (<type begin>) ([^{]{) (comment? [^;}];)* ([^;}]})
  protected def content : Parser[Definition] = opt(comment) ~ attributes ~ opt("enum" | "interface" | "typedef") ~ positioned(
    id ^^ { name ⇒ new Definition(name, currentFile) }
  ) ~! """[^;\{]*""".r ~ (
      (";" |
        ("{" ~>
          (
            rep(opt(commentText) ~ """[^;/}]*;""".r ^^ { case c ~ i ⇒ c.getOrElse("") + i }) ^^ { _.mkString(" ") }
          ) ~ """[^;/}]*\}""".r) ^^ {
              case fs ~ rem ⇒ s"$fs $rem"
            })
    ) ^^ {
              case c ~ attr ~ mod ~ decl ~ sup ~ body ⇒
                if (definitions.contains(decl.name)) {
                  val first = definitions(decl.name)
                  self.reportError(s"""duplicate definition: ${decl.name.getOgss}
first:
${first.file} ${first.pos}
${first.pos.longString}

second:
${decl.file} ${decl.pos}
${decl.pos.longString}""")
                }
                definitions(decl.name) = decl

                decl.comment = c.getOrElse(null)
                decl.superImage = s"${mod.getOrElse("")} $sup"
                decl.bodyImage = body
                decl
            }

  protected def file = opt(headComment) ~! rep(includes) ~! rep(content)

  def process(in : File) {
    // prevent duplicate processing
    if (seen.contains(in))
      return
    seen += in

    if (!in.exists() || !in.isFile()) {
      self.reportError(s"cannot import file $in")
    } else {

      currentFile = in;
      val lines = scala.io.Source.fromFile(in, "utf-8").getLines.mkString("\n")

      parseAll(file, lines) match {
        case Success(_, _) ⇒
        case f             ⇒ self.reportError(s"parsing failed in ${in.getName}: $f");
      }
    }
  }
}
