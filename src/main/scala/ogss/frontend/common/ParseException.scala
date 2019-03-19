package ogss.frontend.common

/**
 * Front-ends shall propagate their error messages as instances of this class.
 */
class ParseException(msg : String) extends Exception(msg : String) {

}