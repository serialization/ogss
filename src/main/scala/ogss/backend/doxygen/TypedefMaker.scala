/*  ___ _  ___ _ _                                                            *\
** / __| |/ (_) | |       The SKilL Generator                                 **
** \__ \ ' <| | | |__     (c) 2013-18 University of Stuttgart                 **
** |___/_|\_\_|_|____|    see LICENSE                                         **
\*                                                                            */
package ogss.backend.doxygen

/**
 * Creates user type equivalents.
 *
 * @author Timm Felden
 */
trait TypedefMaker extends AbstractBackEnd {
  abstract override def make {
    super.make
    val ts = types.aliases

    if (!ts.isEmpty) {
      val out = files.open(s"""src/typedefs.h""")

      out.write(s"""
// typedefs inside of the project
#include <string>
#include <list>
#include <set>
#include <map>
#include <stdint.h>

${
        (for (t ← ts)
          yield s"""
${
          comment(t)
        }typedef ${mapType(t.target)} ${capital(t.name)};
""").mkString
      }
""")

      out.close()
    }
  }
}
