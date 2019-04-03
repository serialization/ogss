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

/**
 * Base class for non-code-generating back-ends. Provides default
 * implementations for most abstract methods.
 *
 * @author Timm Felden
 */
abstract class DefaultBackEnd extends BackEnd {

  /**
   * This string will be prepended to the output directory of files.
   * It is evaluated after setting the package and before creating files.
   * Its purpose is to allow back-ends to customize the output path based on the
   * package name set by the user, as languages like Java require it whereas Ada
   * does not even allow to do so.
   */
  override def packageDependentPathPostfix : String = "";

  /**
   * Create the header submitted to the PrintingService
   */
  def makeHeader(headerInfo : HeaderInfo) : String = ""

  /**
   * Set output package/namespace/...
   * This is a list of Strings, each denoting a package.
   *
   * This correpsonds to the -p option.
   */
  def setPackage(names : List[String]) : Unit = {}

  /**
   * Sets an option to a new value.
   */
  def setOption(option : String, value : String) : Unit = ???

  /**
   * The clean mode preferred by this back-end.
   */
  def defaultCleanMode : String = "file"

  /**
   * Transform a comment of a user declaration into the language's comment system
   */
  override def comment(d : UserDefinedType) : String = ""

  /**
   * Transform a comment of a field into the language's comment system
   */
  override def comment(d : FieldLike) : String = ""

  /**
   * Tries to escape a string without decreasing the usability of the generated identifier.
   */
  def escapedLonely(target : String) : String = ???
}
