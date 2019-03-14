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
  protected def content : Parser[Definition] = opt(comment) ~ opt("interface" | "typedef") ~ positioned(
    id ^^ { name ⇒ new Definition(name, currentFile) }
  ) ~ """[^\{]*\{""".r ~ (
      rep(opt(commentText) ~ """[^;/}]*;""".r ^^ { case c ~ i ⇒ c + i }) ^^ { _.mkString(" ") }
    ) ~ """[^;/}]*\}""".r ^^ {
        case c ~ mod ~ decl ~ sup ~ fs ~ rem ⇒
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
          decl.image = s"${mod.getOrElse("")} $sup $fs $rem"
          decl
      }

  protected def file = opt(headComment) ~! rep(includes) ~! rep(content)

  def process(in : File) {
    // prevent duplicate processing
    if (seen.contains(in))
      return
    seen += in

    currentFile = in;
    val lines = scala.io.Source.fromFile(in, "utf-8").getLines.mkString("\n")

    parseAll(file, lines) match {
      case Success(_, _) ⇒
      case f             ⇒ self.reportError(s"parsing failed in ${in.getName}: $f");
    }
  }
}

class Definition(
  /**
   * the ogss name of this definition
   */
  val name : Identifier,
  val file : File
) extends Positional {
  var comment : Comment = _
  var image : String = _
}