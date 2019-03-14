/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.java

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap


trait SubTypesMaker extends AbstractBackEnd {
  abstract override def make {
    super.make

    for (t ← IR) {
      val out = files.open(s"Sub$$${name(t)}.java")

      // package
      out.write(s"""package ${this.packageName};

import ogss.common.java.internal.Pool;
import ogss.common.java.internal.NamedObj;

${
        suppressWarnings
      }public final class Sub$$${name(t)} extends ${name(t)} implements NamedObj {
    transient public final Pool<?> τp;

     public Sub$$${name(t)}(Pool<?> τp, int ID) {
        super(ID);
        this.τp = τp;
    }

    @Override
    public int stid() {
        return -1;
    }

    @Override
    public Pool<?> τp() {
        return τp;
    }
}""");
      out.close()
    }
  }
}
