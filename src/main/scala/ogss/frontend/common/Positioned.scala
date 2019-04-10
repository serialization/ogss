package ogss.frontend.common

import scala.util.parsing.input.Positional

trait Positioned extends Positional {
  val declaredInFile : String;
}