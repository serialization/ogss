/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.java

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap

trait EnumMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← enums) {
      val out = files.open(s"${name(t)}.java")

      // package
      out.write(s"""package ${this.packageName};

public enum ${name(t)} {
  ${
        // TODO comments!
        t.getValues.map(id ⇒ escaped(capital(id.getName))).sortWith(
          (l, r) ⇒ l.length() < r.length() || (l.length() == r.length() && l.compareTo(r) < 0)
        ).mkString("", ",\n  ", ";")
      }
}""");
      out.close()
    }
  }
}
