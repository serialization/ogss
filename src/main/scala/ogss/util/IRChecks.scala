package ogss.util

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import ogss.oil.OGFile
import ogss.oil.TypeContext

/**
 * This object provides some functions to check the well-formedness of
 * OIL-entities.
 *
 * @author Timm Felden
 */
object IRChecks {

  /**
   * Check a whole file by performing checks on its contents.
   */
  def check(sg : OGFile) {
    sg.TypeContexts.asScala.foreach(check)
  }

  /**
   * Check consistency of a type context. Throws an exception, if the check
   * fails.
   */
  def check(tc : TypeContext) {

    // check that enums have at least one instance
    for(t <- tc.getEnums.asScala) {
      if(t.getValues.isEmpty())
          throw new IllegalStateException(s"Enum ${t.getName.getOgss} has no instances")
    }

    // check that containers are UCC-ordered
    tc.getContainers.asScala.scanLeft(-1L) {
      case (last, c) ⇒
        val ucc = IRUtils.ucc(c.getKcc)
        if (last < ucc)
          ucc
        else
          throw new IllegalStateException(f"Container ${c.getName.getOgss} has UCC $ucc%8x, but last container had $last%8x")
    }

    // check that all types have STIDs and that they are continuous
    var nextSTID = 10
    for (t ← tc.getClasses.asScala) {
      expect(nextSTID == t.getStid, s"type ${t.getName.getOgss} has an invalid STID: ${t.getStid}, should be $nextSTID")
      nextSTID += 1
    }
    for (t ← tc.getContainers.asScala) {
      expect(nextSTID == t.getStid, s"type ${t.getName.getOgss} has an invalid STID: ${t.getStid}, should be $nextSTID")
      nextSTID += 1
    }
    for (t ← tc.getEnums.asScala) {
      expect(nextSTID == t.getStid, s"type ${t.getName.getOgss} has an invalid STID: ${t.getStid}, should be $nextSTID")
      nextSTID += 1
    }
  }

  private def expect(predicate : Boolean, msg : String) = if (!predicate) throw new IllegalStateException(msg)
}