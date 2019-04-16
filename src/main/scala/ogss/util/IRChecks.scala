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

    // check that alias targets exist
    for (t ← tc.getAliases.asScala) {
      if (null == t.getTarget)
        throw new IllegalStateException(s"Type alias ${t.getName.getOgss} has no target")
    }

    // check that enums have at least one instance
    for (t ← tc.getEnums.asScala) {
      if (t.getValues.isEmpty())
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

    // check that all super types have lower STIDS
    for (t ← tc.getClasses.asScala; sup = t.getSuperType if null != sup) {
      expect(sup.getStid < t.getStid, s"class ${t.getName.getOgss} has an STID lower than that of its super class: ${t.getStid}, super: ${sup.getStid}")
    }
    for (t ← tc.getInterfaces.asScala; sup = t.getSuperType if null != sup) {
      for (superInterface ← t.getSuperInterfaces.asScala; ssi = superInterface.getSuperType if null != ssi)
        expect(ssi.getStid <= sup.getStid, s"interface ${t.getName.getOgss} has a super type with an STID lower than that of its super interfaces (${superInterface.getName.getOgss}) super class(${ssi.getName.getOgss}): ${sup.getStid}, super: ${ssi.getStid}")
    }
    // check that all super types without supertypes have only super interfaces without supertypes
    for (
      t ← tc.getClasses.asScala ++ tc.getInterfaces.asScala if null == t.getSuperType;
      superInterface ← t.getSuperInterfaces.asScala; ssi = superInterface.getSuperType if null != ssi
    ) {
      expect(false, s"type ${t.getName.getOgss} has no super class but its super interface ${superInterface.getName.getOgss} has a super class: ${ssi.getName.getOgss}")
    }
  }

  private def expect(predicate : Boolean, msg : String) = if (!predicate) throw new IllegalStateException(msg)
}