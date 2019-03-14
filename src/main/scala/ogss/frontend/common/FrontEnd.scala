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
import ogss.oil.Comment
import ogss.oil.Identifier
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap
import java.util.ArrayList
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

/**
 * Provides common functionalities to be used by all front-ends.
 *
 * @note All Front-Ends must provide a default constructor.
 */
abstract class FrontEnd {
  /**
   * The result of this front-end.
   *
   * @note the OGFile is opened by CLI and remains untouched after invocation of
   * run
   *
   * @note helper methods use out to create new objects
   */
  var out : OGFile = _;

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

  def reportError(msg : String) : Nothing = {
    throw new Error(msg)
  }

  /**
   * Run the front-end on the argument path
   */
  def run(path : File)

  /**
   * Create a comment from a comment string
   *
   * The representation of text is without the leading "/°" and trailing "°/" (where °=*)
   */
  final def toComment(text : String) : Comment = {
    // scan s to split it into pieces
    @inline def scan(last : Int) : ListBuffer[String] = {
      var begin = 0;
      var next = 0;
      // we have to insert a line break, because the whitespace handling may have removed one
      var r = ListBuffer[String]("\n")
      while (next < last) {
        text.charAt(next) match {
          case ' ' | '\t' | 0x0B | '\f' | '\r' ⇒
            if (begin != next)
              r.append(text.substring(begin, next))
            begin = next + 1;
          case '\n' ⇒
            if (begin != next)
              r.append(text.substring(begin, next))
            r.append("\n")
            begin = next + 1;
          case _ ⇒
        }
        next += 1
      }
      if (begin != last) r.append(text.substring(begin, last))
      r
    }

    val ws = scan(text.size - 2)

    val r = out.Comments.make()
    r.setTags(new ArrayList)

    @inline def ammend(text : ListBuffer[String]) = {
      if (null == r.getText)
        r.setText(new ArrayList(asJavaCollection(text)))
      else
        r.getTags.get(r.getTags.size() - 1).setText(new ArrayList(asJavaCollection(text)))
    }

    @inline def ammendTag(text : ListBuffer[String], tag : String) = {
      if (null == r.getText)
        r.setText(new ArrayList(asJavaCollection(text)))
      else
        r.getTags.get(r.getTags.size() - 1).setText(new ArrayList(asJavaCollection(text)))
      val t = out.CommentTags.make()
      r.getTags.add(t)
      t.setName(tag)
    }

    @tailrec def parse(ws : ListBuffer[String], text : ListBuffer[String]) : Unit =
      if (ws.isEmpty) ammend(text)
      else (ws.head, ws.tail) match {
        case ("\n", ws) if (ws.isEmpty)     ⇒ ammend(text)
        case ("\n", ws) if (ws.head == "*") ⇒ parse(ws.tail, text)
        case ("\n", ws)                     ⇒ parse(ws, text)
        case (w, ws) if w.matches("""\*?@.+""") ⇒
          val end = if (w.contains(":")) w.lastIndexOf(':') else w.size
          val tag = w.substring(w.indexOf('@') + 1, end).toLowerCase
          ammendTag(text, tag); parse(ws, ListBuffer[String]())
        case (w, ws) ⇒ text.append(w); parse(ws, text)
      }

    parse(ws, ListBuffer[String]())

    r
  }

  // ogssname -> identifier
  private val idCache = new HashMap[String, Identifier]
  /**
   * Create an identifier from string
   *
   * @note results are deduplicated
   */
  final def toIdentifier(text : String) : Identifier = {

    var parts = ArrayBuffer(text)

    // split into parts at _
    parts = parts.flatMap(_.split("_").map { s ⇒ if (s.isEmpty()) "_" else s }.to)

    // split before changes from uppercase to lowercase
    parts = parts.flatMap { s ⇒
      val parts = ArrayBuffer[String]()
      var last = 0
      for (i ← 1 until s.length - 1) {
        if (s.charAt(i).isUpper && s.charAt(i + 1).isLower) {
          parts += s.substring(last, i)
          last = i
        }
      }
      parts += s.substring(last)
      parts
    }

    // create OGSS representation
    val ogssName = parts.map(_.capitalize).mkString

    idCache.getOrElseUpdate(
      ogssName,
      {
        val r = out.Identifiers.make
        r.setOgss(ogssName)
        r.setParts(new ArrayList(asJavaCollection(parts)))
        r
      }
    )
  }
}
