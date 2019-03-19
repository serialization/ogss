package ogss.frontend.skill

import ogss.oil.Comment
import scala.util.parsing.combinator.RegexParsers
import ogss.frontend.common.FrontEnd
import ogss.oil.Identifier
import ogss.util.IRUtils

/**
 * Parse rules used by multiple parsers.
 */
class CommonParseRules(self : FrontEnd) extends RegexParsers with IRUtils {

  /**
   * Usual identifiers including arbitrary unicode characters.
   */
  protected def id : Parser[Identifier] = """[a-zA-Z_\u007f-\uffff][\w\u007f-\uffff]*""".r ^^ self.toIdentifier
  protected def idText : Parser[String] = """[a-zA-Z_\u007f-\uffff][\w\u007f-\uffff]*""".r

  /**
   * Skill integer literals
   */
  protected def int : Parser[Long] = hexInt | generalInt
  protected def hexInt : Parser[Long] = "0x" ~> ("""[0-9a-fA-F]*""".r ^^ { i ⇒ java.lang.Long.parseLong(i, 16) })
  protected def generalInt : Parser[Long] = """-?[0-9]*\.*""".r >> { i ⇒
    try {
      success(java.lang.Long.parseLong(i))
    } catch {
      case e : Exception ⇒ failure("not an int")
    }
  }

  /**
   * Floating point literal, as taken from the JavaTokenParsers definition.
   *
   * @note if the target can be an integer as well, the integer check has to come first
   */
  def floatingPointNumber : Parser[Double] = """-?(\d+(\.\d*)?|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r ^^ { _.toDouble }

  /**
   * We use string literals to encode paths. If someone really calls a file ", someone should beat him hard.
   */
  protected def string = "\"" ~> """[^"]*""".r <~ "\""

  /**
   * Match a comment and turn it into IR
   */
  protected def comment : Parser[Comment] = """/\*+""".r ~> ("""[\S\s]*?\*/""".r) ^^ self.toComment

  /**
   * Match a comment, but do not turn it into IR, yet.
   */
  protected def commentText : Parser[String] = ("""/\*+""".r ~ ("""[\S\s]*?\*/""".r)) ^^ { case l ~ r ⇒ l + r }

  /**
   * Matches Hints and Restrictions
   */
  protected def attributes : Parser[String] = rep(
    ("!" ~ "pragma" ~ idText ~ opt(("(" ~ """[^\)]*\)""".r) ^^ { case l ~ r ⇒ l + r })) ^^ {
      case h ~ p ~ n ~ a ⇒ s"$h$p $n ${a.getOrElse("")}"
    }
      |
      (("@" | "!") ~ idText ~ opt(("(" ~ """[^\)]*\)""".r) ^^ { case l ~ r ⇒ l + r })) ^^ {
        case h ~ n ~ a ⇒ s"$h$n ${a.getOrElse("")}"
      }
  ) ^^ { ts ⇒ ts.mkString("\n") }
}