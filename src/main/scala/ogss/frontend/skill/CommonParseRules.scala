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

import scala.util.parsing.combinator.RegexParsers

import ogss.frontend.common.FrontEnd
import ogss.oil.Attribute
import ogss.oil.Comment
import ogss.oil.Identifier
import ogss.util.IRUtils
import scala.collection.mutable.ArrayBuffer

/**
 * Parse rules used by multiple parsers.
 */
abstract class CommonParseRules(self : FrontEnd) extends RegexParsers with IRUtils {

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
   *
   * TODO sane argument rules!
   */
  protected def attributes : Parser[ArrayBuffer[Attribute]] = rep(
    ("!" ~ "pragma" ~! idText ~ opt(("(" ~ """[^\)]*\)""".r) ^^ { case l ~ r ⇒ l + r })) ^^ {
      case h ~ p ~ n ~ a ⇒
        self.out.Attribute
          .build
          .isSerialized(false)
          .name(p.toLowerCase())
          .arguments(ArrayBuffer(n) ++ a)
          .make
    }
      |
      (("@" | "!") ~! idText ~ opt(("(" ~ """[^\)]*\)""".r) ^^ { case l ~ r ⇒ l + r })) ^^ {
        case h ~ n ~ a ⇒
          self.out.Attribute
            .build
            .isSerialized("@".equals(h))
            .name(n.toLowerCase())
            .arguments(a.to)
            .make
      }
  ) ^^ { ts ⇒ ts.to }
}
