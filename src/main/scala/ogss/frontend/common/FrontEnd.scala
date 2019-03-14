/*
 * Copyright 2019 University of Stuttgart, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ogss.frontend.common

import java.io.File

import ogss.oil.OGFile;

/**
 * Provides common functionalities to be used by all front-ends.
 *
 * @note All Front-Ends must provide a default constructor.
 */
abstract class FrontEnd {

  /**
   * The name of this front-end as per command line interface.
   */
  def name : String;

  /**
   * The human readable description of this front-end.
   */
  def description : String;

  /**
   * The file extension associated with this front-end.
   */
  def extension : String;

  def run(path : File) : OGFile;
}
