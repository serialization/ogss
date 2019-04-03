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
package ogss.util

import java.util.Date
import ogss.backend.common.BackEnd

/**
 * Use this to create a 5 lines header that looks similar in all languages.
 *
 * This corresponds to the -hN, -u, -license, -date options.
 *
 * @author Timm Felden
 */
case class HeaderInfo(
  var line1 :    Option[String] = None,
  var line2 :    Option[String] = None,
  var line3 :    Option[String] = None,
  var license :  Option[String] = None,
  var userName : Option[String] = None,
  var date :     Option[String] = None
) {

  private val logo = """   ____  _____________  
  / __ \/ ___/ __/ __/  
 / /_/ / (_\_\ \_\ \    
 \____/\___/___/___/    
                        """.split('\n')

  /**
   * create a header out of language specific format specifications
   */
  def format(
    backEnd :     BackEnd,
    topLeft :     String,
    topRight :    String,
    left :        String,
    right :       String,
    bottomLeft :  String,
    bottomRight : String
  ) : String = {

    // create header lines
    val headerLine1 = (line1 match {
      case Some(s) ⇒ s
      case None    ⇒ license.map("LICENSE: " + _).getOrElse(s"Your OGSS/${backEnd.name} Binding")
    })
    val headerLine2 = (line2 match {
      case Some(s) ⇒ s
      case None ⇒ "generated: " + (date match {
        case Some(s) ⇒ s
        case None    ⇒ (new java.text.SimpleDateFormat("dd.MM.yyyy")).format(new Date)
      })
    })
    val headerLine3 = (line3 match {
      case Some(s) ⇒ s
      case None ⇒ "by: " + (userName match {
        case Some(s) ⇒ s
        case None    ⇒ System.getProperty("user.name")
      })
    })

    // create content
    val content = Array[String](
      topLeft + logo(0),
      left + logo(1) + headerLine1,
      left + logo(2) + headerLine2,
      left + logo(3) + headerLine3,
      bottomLeft + logo(4)
    )

    // create right bar, so that we can trim
    val rightSide = Array[String](topRight, right, right, right, bottomRight)

    (for ((l, r) ← content.zip(rightSide)) yield {
      val length = backEnd.lineLength - (if (-1 != r.indexOf('\n')) r.indexOf('\n') else r.length)

      l.padTo(length, " ").mkString.substring(0, length) + r
    }).mkString("", "\n", "\n")
  }
}
