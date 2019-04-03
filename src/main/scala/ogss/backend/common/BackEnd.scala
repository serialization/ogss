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
package ogss.backend.common

import ogss.oil.OGFile
import ogss.io.PrintingService
import ogss.util.HeaderInfo
import ogss.oil.FieldLike
import ogss.oil.Identifier
import ogss.oil.Type
import ogss.oil.UserDefinedType
import ogss.oil.Comment
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import ogss.util.IRUtils
import scala.collection.mutable.HashSet
import java.io.File
import ogss.oil.Field

/**
 * Base class of all back-ends.
 *
 * @author Timm Felden
 */
abstract class BackEnd extends IRUtils {

  /**
   * The human readable description of this back-end.
   */
  def description : String;

  /**
   * The name of this back-end as presented in the CLI.
   */
  def name : String

  /**
   * This string will be prepended to the output directory of files.
   * It is evaluated after setting the package and before creating files.
   * Its purpose is to allow back-ends to customize the output path based on the
   * package name set by the user, as languages like Java require it whereas Ada
   * does not even allow to do so.
   */
  def packageDependentPathPostfix : String = "";

  /**
   * Improved source printing to be used by a generator.
   * Instantiation is performed before invocation of the generator.
   */
  var files : PrintingService = _;

  /**
   * maximum line length in emitted output
   */
  var lineLength = 80

  /**
   * Create the header submitted to the PrintingService
   */
  def makeHeader(headerInfo : HeaderInfo) : String;

  /**
   * Base path of dependencies copied by this generator.
   */
  var depsPath : File = _;
  /**
   * request the code generator to skip copying of dependencies
   * @note this is useful, for instance, as part of code regeneration in a build
   * system where dependencies and specification are managed by the version control system
   */
  var skipDependencies = false;

  /**
   * Set the IR to be used by this back-end.
   */
  def setIR(IR : OGFile)

  /**
   * Set output package/namespace/...
   * This is a list of Strings, each denoting a package.
   *
   * This correpsonds to the -p option.
   */
  def setPackage(names : List[String]) : Unit;

  /**
   * Sets an option to a new value. The option is passed in lowercase. The value is as provided.
   */
  def setOption(option : String, value : String) : Unit;

  /**
   * Provide a descriptions for options supported by this back-end. Names are
   * matched case-insensitive.
   */
  def describeOptions : Seq[OptionDescription] = Seq()
  case class OptionDescription(val name : String, val values : String, val description : String);

  /**
   * Set a custom field manual. Override iff custom is supported by this language.
   *
   * @note the text returned may be multi line but shall be indented by two
   * spaces in each line
   */
  def customFieldManual : String = null

  /**
   * The clean mode preferred by this back-end.
   */
  def defaultCleanMode : String;

  /**
   * Names of all types that shall accept the generated visitor
   */
  val visited = new HashSet[Identifier]

  /**
   * A list of file names relative to jar-extras/deps.
   * Directories are copied recursively.
   */
  var dependencies : Seq[String] = Seq()

  /**
   * Ensure existence of dependencies
   */
  final def makeDeps {
    if (!skipDependencies) {
      DependenciesMaker.copyDeps(dependencies, depsPath)
    }
  }

  /**
   * Makes the output. Use trait stacking, i.e. traits must invoke super.make!!!
   *
   * This function is called after options have been set.
   */
  def make {}

  /**
   * Transform a comment of a user declaration into the language's comment system
   */
  protected def comment(d : UserDefinedType) : String;

  /**
   * Transform a comment of a field into the language's comment system
   */
  protected def comment(d : FieldLike) : String;

  /**
   * Creates a nicely formatted String with line breaks and a prefix for a code generators output.
   *
   * @note examples use ° instead of *
   * @param prefix
   *            Prefix of the comment, e.g. " /°°"
   * @param linePrefix
   *            Prefix of a line, e.g. " ° "
   * @param postfix
   *            Postfix of a comment, e.g. " °/"
   * @return a nicely formatted string, very similar to scala's mkString, except that it tries to fill lines
   */
  protected def format(c : Comment, prefix : String, linePrefix : String, postfix : String) : String = {
    if (null == c)
      return ""

    val sb = new StringBuilder(prefix);

    formatText(c.getText.asScala, linePrefix, sb, null);
    for (t ← c.getTags.asScala)
      formatText(t.getText.asScala, linePrefix, sb, t.getName);

    // finish comment
    sb.append(postfix);
    sb.toString();
  }

  /**
   * format a list of words
   */
  private def formatText(text : Seq[String], linePrefix : String, sb : StringBuilder, tag : String) {
    var line = new StringBuilder(linePrefix);

    if (null != tag)
      line.append(" @").append(tag).append(' ');

    for (w ← text) {
      if (line.length() + w.length() + 1 > lineLength) {
        // break line
        sb.append(line).append('\n');
        line = new StringBuilder(linePrefix);
      }
      line.append(' ').append(w);
    }
    sb.append(line).append('\n');
  }

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   * This escaping strategy assumes that the resulting identifier contains more
   * characters than the argument, e.g. s"get${escaped(capital(t.name))}"
   */
  def escaped(target : String) : String = escapedLonely(target)

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   */
  def escapedLonely(target : String) : String;

  /**
   * If the backend supports it, the given convention is used for the generation
   * of type names
   */
  var typeNameConvention = "capital"
  /**
   * If the backend supports it, the given convention is used for the generation
   * of field names
   */
  var fieldNameConvention = "camel"

  /**
   * Translation of a type to its representation in the source code
   */
  protected[backend] def name(t : Type) : String = escapedLonely(typeNameConvention match {
    case "ada"       ⇒ adaStyle(t.getName)
    case "c"         ⇒ cStyle(t.getName)
    case "camel"     ⇒ camel(t.getName)
    case "capital"   ⇒ capital(t.getName)
    case "lowercase" ⇒ lowercase(t.getName)
  })
  /**
   * Translation of a field to its representation in the source
   * code
   *
   * TODO default naming convention
   */
  protected def name(f : FieldLike) : String = escapedLonely(fieldNameConvention match {
    case "ada"       ⇒ adaStyle(f.getName)
    case "c"         ⇒ cStyle(f.getName)
    case "camel"     ⇒ camel(f.getName)
    case "capital"   ⇒ capital(f.getName)
    case "lowercase" ⇒ lowercase(f.getName)
  })

  /**
   * The default value used for initialization of a given field
   */
  protected def defaultValue(f : Field) : String = ???
}
