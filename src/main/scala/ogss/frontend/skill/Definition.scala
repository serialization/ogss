package ogss.frontend.skill

import java.io.File

import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.util.parsing.input.Positional

import ogss.frontend.common.FrontEnd
import ogss.oil.Comment
import ogss.oil.Identifier
import ogss.oil.UserDefinedType

class Definition(
  /**
   * the ogss name of this definition
   */
  val name : Identifier,
  val file : File
) extends Positional {
  var comment : Comment = _

  var superImage : String = null
  var bodyImage : String = null

  var IR : UserDefinedType = null
}