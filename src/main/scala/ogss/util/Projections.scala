package ogss.util

import java.util.ArrayList

import scala.collection.JavaConverters._

import ogss.oil.EnumDef
import ogss.oil.OGFile
import ogss.oil.TypeContext
import scala.collection.mutable.HashMap
import ogss.oil.InterfaceDef
import ogss.oil.Type
import scala.collection.mutable.ArrayBuffer
import ogss.oil.ClassDef
import ogss.oil.WithInheritance
import ogss.oil.ArrayType
import ogss.oil.Identifier
import ogss.oil.ListType
import ogss.oil.SetType
import ogss.oil.MapType
import ogss.oil.Field
import ogss.oil.CustomField
import ogss.oil.View
import ogss.oil.TypeAlias
import ogss.oil.ContainerType

/**
 * Takes an unprojected .oil-file and creates interface and typedef
 * projections.
 */
class Projections(sg : OGFile) {

  private def run {
    if (sg.TypeContexts.size() != 1)
      throw new IllegalStateException(s"expected exactly one type context, but found ${sg.TypeContexts.size()}")

    val tc = sg.TypeContexts.iterator().next();
    if (tc.getProjectedInterfaces || tc.getProjectedTypeDefinitions)
      throw new IllegalStateException(s"""expected an unprojected type context, but found ${sg.TypeContexts.size()}
  interfaces: ${tc.getProjectedInterfaces}
  typedefs: ${tc.getProjectedTypeDefinitions}""")

    substituteInterfaces(substituteAliases(tc))
    substituteInterfaces(tc)
  }

  private def substituteAliases(tc : TypeContext) : TypeContext = {
    // note we cannot fast exit if no interfaces are available, because we are not allowed to reuse the same types

    // set trivial properties
    val r = sg.TypeContexts.make()
    r.setProjectedInterfaces(tc.getProjectedInterfaces)
    r.setProjectedTypeDefinitions(true)
    r.setAliases(new ArrayList)
    r.setEnums(copyEnums(tc.getEnums))
    r.setClasses(copyClasses(tc.getClasses))
    r.setInterfaces(copyInterfaces(tc.getInterfaces))
    r.setContainers(new ArrayList)

    // calculate type map
    val typeMap = new HashMap[Type, Type]
    makeTBN(r)
    val tbn = r.getByName

    def ensure(t : Type) : Type = {
      typeMap.getOrElseUpdate(t, t match {
        case t : ArrayType ⇒ {
          val r = sg.ArrayTypes.make()
          val b = ensure(t.getBaseType)
          r.setBaseType(b)
          r.setName(toIdentifier(s"${b.getName.getOgss}[]"))
          r
        }
        case t : ListType ⇒ {
          val r = sg.ListTypes.make()
          val b = ensure(t.getBaseType)
          r.setBaseType(b)
          r.setName(toIdentifier(s"list<${b.getName.getOgss}>"))
          r
        }
        case t : SetType ⇒ {
          val r = sg.SetTypes.make()
          val b = ensure(t.getBaseType)
          r.setBaseType(b)
          r.setName(toIdentifier(s"set<${b.getName.getOgss}>"))
          r
        }
        case t : MapType ⇒ {
          val r = sg.MapTypes.make()
          val k = ensure(t.getKeyType)
          val v = ensure(t.getValueType)
          r.setKeyType(k)
          r.setValueType(v)
          r.setName(toIdentifier(s"map<${k.getName.getOgss},${v.getName.getOgss}>"))
          r
        }
        case _ ⇒ tbn.get(t.getName.getOgss)
      })
    }

    for (c ← tc.getAliases.asScala) {
      typeMap(c) = ensure(c.getTarget)
    }
    completeTypeMap(tc, r, typeMap)

    // fix type hierarchy
    for (c ← (r.getClasses.asScala ++ r.getInterfaces.asScala)) {
      // move super to this tc
      val sis = new ArrayList[InterfaceDef]
      for (s ← c.getSuperInterfaces.asScala) {
        val n = typeMap(s).asInstanceOf[InterfaceDef]
        sis.add(n)
        n.getSubTypes.add(c)
      }
      c.setSuperInterfaces(sis)

      if (null != c.getSuperType) {
        c.setSuperType(typeMap(c.getSuperType).asInstanceOf[ClassDef])
        c.getSuperType.getSubTypes.add(c)
      }
      c.setBaseType(typeMap(c.getBaseType).asInstanceOf[ClassDef])

      // calculate new fields
      copyFields(typeMap, tc.getByName.get(c.getName.getOgss).asInstanceOf[WithInheritance], c)
    }

    // we may have changed types, so we need to recalculate STIDs and KCCs
    IRUtils.recalculateSTIDs(r)

    r
  }

  private def substituteInterfaces(tc : TypeContext) {
    // note we cannot fast exit if no interfaces are available, because we are not allowed to reuse the same types

    // set trivial properties
    val r = sg.TypeContexts.make()
    r.setProjectedInterfaces(true)
    r.setProjectedTypeDefinitions(tc.getProjectedTypeDefinitions)
    r.setAliases(copyAliases(tc.getAliases))
    r.setEnums(copyEnums(tc.getEnums))
    r.setClasses(copyClasses(tc.getClasses))
    r.setInterfaces(new ArrayList)
    r.setContainers(new ArrayList)

    // calculate type map
    val typeMap = new HashMap[Type, Type]
    for (c ← tc.getInterfaces.asScala) {
      typeMap(c) =
        if (null == c.getSuperType) tc.getByName.get("AnyRef")
        else c.getSuperType
    }
    makeTBN(r)
    completeTypeMap(tc, r, typeMap)

    // fix type hierarchy
    for (c ← r.getClasses.asScala) {
      // remove interfaces from the type hierarchy
      c.setSuperInterfaces(r.getInterfaces)
      if (null != c.getSuperType) {
        c.setSuperType(typeMap(c.getSuperType).asInstanceOf[ClassDef])
        c.getSuperType.getSubTypes.add(c)
      }
      c.setBaseType(typeMap(c.getBaseType).asInstanceOf[ClassDef])

      // calculate new fields
      collectFields(typeMap, tc.getByName.get(c.getName.getOgss).asInstanceOf[ClassDef], c)
    }

    // update type alias targets
    for (c ← tc.getAliases.asScala) {
      c.setTarget(typeMap(tc.getByName.get(c.getName.getOgss)))
    }

    // we may have changed types, so we need to recalculate STIDs and KCCs
    IRUtils.recalculateSTIDs(r)
  }

  /**
   * Collect fieldLikes from a type and its super interfaces and type them in
   * the new type context using typeMap
   */
  private def collectFields(typeMap : HashMap[Type, Type], from : ClassDef, target : ClassDef) {
    val cs = IRUtils.allCustoms(from)
    if (null != from.getSuperType)
      cs --= IRUtils.allCustoms(from.getSuperType)

    val tcs = new ArrayList[CustomField]
    target.setCustoms(tcs)
    for (f ← cs.toArray.sortBy(f ⇒ (f.getName.getOgss.length(), f.getName.getOgss))) {
      val r = copy(f)
      r.setOwner(target)
      tcs.add(r)
    }

    val fs = IRUtils.allFields(from)
    if (null != from.getSuperType)
      fs --= IRUtils.allFields(from.getSuperType)

    val tfs = new ArrayList[Field]
    target.setFields(tfs)
    for (f ← fs.toArray.sortBy(f ⇒ (f.getName.getOgss.length(), f.getName.getOgss))) {
      val r = copy(f, typeMap)
      r.setOwner(target)
      tfs.add(r)
    }

    val vs = IRUtils.allViews(from)
    if (null != from.getSuperType)
      vs --= IRUtils.allViews(from.getSuperType)

    val tvs = new ArrayList[View]
    target.setViews(tvs)
    for (f ← vs.toArray.sortBy(f ⇒ (f.getName.getOgss.length(), f.getName.getOgss))) {
      val r = copy(f, typeMap)
      r.setOwner(target)
      tvs.add(r)
    }
  }

  private def copyFields(typeMap : HashMap[Type, Type], from : WithInheritance, target : WithInheritance) {
    val tcs = new ArrayList[CustomField]
    target.setCustoms(tcs)
    for (f ← from.getCustoms.asScala) {
      val r = copy(f)
      r.setOwner(target)
      tcs.add(r)
    }

    val tfs = new ArrayList[Field]
    target.setFields(tfs)
    for (f ← from.getFields.asScala) {
      val r = copy(f, typeMap)
      r.setOwner(target)
      tfs.add(r)
    }

    val tvs = new ArrayList[View]
    target.setViews(tvs)
    for (f ← from.getViews.asScala) {
      val r = copy(f, typeMap)
      r.setOwner(target)
      tvs.add(r)
    }
  }

  private def copy(f : CustomField) : CustomField = {
    val r = sg.CustomFields.make()
    r.setComment(f.getComment)
    r.setName(f.getName)
    r.setLanguage(f.getLanguage)
    r.setOptions(f.getOptions)
    r.setTypename(f.getTypename)
    r
  }
  /**
   * Copy a field but will not set owner to simplify moves in interface
   * projection!
   */
  private def copy(f : Field, typeMap : HashMap[Type, Type]) : Field = {
    val r = sg.Fields.make()
    r.setIsTransient(f.getIsTransient)
    r.setType(typeMap(f.getType))
    r.setComment(f.getComment)
    r.setName(f.getName)
    r
  }
  private def copy(f : View, typeMap : HashMap[Type, Type]) : View = {
    val r = sg.Views.make()
    r.setName(f.getName)
    r.setComment(f.getComment)
    r.setType(typeMap(f.getType))
    ??? // TODO target
    r
  }

  private def copyAliases(aliases : ArrayList[TypeAlias]) : ArrayList[TypeAlias] = {
    val r = new ArrayList[TypeAlias]
    for (c ← asScalaBuffer(aliases)) {
      val n = sg.TypeAliass.make()
      n.setComment(c.getComment)
      n.setName(c.getName)
      r.add(n)
    }
    r
  }

  private def copyEnums(enums : ArrayList[EnumDef]) : ArrayList[EnumDef] = {
    val r = new ArrayList[EnumDef]
    for (c ← asScalaBuffer(enums)) {
      val n = sg.EnumDefs.make()
      n.setValues(c.getValues)
      n.setComment(c.getComment)
      n.setName(c.getName)
      r.add(n)
    }
    r
  }

  /**
   * note: does not copy subtypes, fields, custom and views, as those require a deep
   * copy; they are all initialized with empty arrays
   */
  private def copyClasses(classes : ArrayList[ClassDef]) : ArrayList[ClassDef] = {
    val r = new ArrayList[ClassDef]
    for (c ← asScalaBuffer(classes)) {
      val n = sg.ClassDefs.make()
      n.setName(c.getName)
      n.setComment(c.getComment)
      n.setBaseType(c.getBaseType)
      n.setSuperType(c.getSuperType)
      n.setSuperInterfaces(c.getSuperInterfaces)
      n.setSubTypes(new ArrayList)
      n.setCustoms(new ArrayList)
      n.setFields(new ArrayList)
      n.setViews(new ArrayList)
      r.add(n)
    }
    r
  }

  /**
   * note: does not copy subtypes, fields, custom and views, as those require a deep
   * copy; they are all initialized with empty arrays
   */
  private def copyInterfaces(interfaces : ArrayList[InterfaceDef]) : ArrayList[InterfaceDef] = {
    val r = new ArrayList[InterfaceDef]
    for (c ← asScalaBuffer(interfaces)) {
      val n = sg.InterfaceDefs.make()
      n.setName(c.getName)
      n.setComment(c.getComment)
      n.setBaseType(c.getBaseType)
      n.setSuperType(c.getSuperType)
      n.setSuperInterfaces(c.getSuperInterfaces)
      n.setSubTypes(new ArrayList)
      n.setCustoms(new ArrayList)
      n.setFields(new ArrayList)
      n.setViews(new ArrayList)
      r.add(n)
    }
    r
  }

  private def makeTBN(to : TypeContext) {
    val tbn = new java.util.HashMap[String, Type]
    to.setByName(tbn)
    for (c ← asScalaIterator(sg.BuiltinTypes.iterator()))
      tbn.put(c.getName.getOgss, c)
    for (c ← to.getAliases.asScala)
      tbn.put(c.getName.getOgss, c)
    for (c ← to.getEnums.asScala)
      tbn.put(c.getName.getOgss, c)
    for (c ← to.getClasses.asScala)
      tbn.put(c.getName.getOgss, c)
    for (c ← to.getInterfaces.asScala)
      tbn.put(c.getName.getOgss, c)
  }

  /**
   * Complete a given type mapping from one context to another by adding
   * identity mappings until every type can be mapped.
   * Also, container types will be created.
   *
   * @note will recalculate to.byName
   */
  private def completeTypeMap(from : TypeContext, to : TypeContext, typeMap : HashMap[Type, Type]) {
    val tbn = to.getByName

    def ensure(t : Type) : Type = {
      typeMap.getOrElseUpdate(t, t match {
        case t : ArrayType ⇒ {
          val r = sg.ArrayTypes.make()
          val b = ensure(t.getBaseType)
          r.setBaseType(b)
          r.setName(toIdentifier(s"${b.getName.getOgss}[]"))
          r
        }
        case t : ListType ⇒ {
          val r = sg.ListTypes.make()
          val b = ensure(t.getBaseType)
          r.setBaseType(b)
          r.setName(toIdentifier(s"list<${b.getName.getOgss}>"))
          r
        }
        case t : SetType ⇒ {
          val r = sg.SetTypes.make()
          val b = ensure(t.getBaseType)
          r.setBaseType(b)
          r.setName(toIdentifier(s"set<${b.getName.getOgss}>"))
          r
        }
        case t : MapType ⇒ {
          val r = sg.MapTypes.make()
          val k = ensure(t.getKeyType)
          val v = ensure(t.getValueType)
          r.setKeyType(k)
          r.setValueType(v)
          r.setName(toIdentifier(s"map<${k.getName.getOgss},${v.getName.getOgss}>"))
          r
        }
        case _ ⇒ tbn.get(t.getName.getOgss)
      })
    }

    for (f ← from.getByName.values().asScala) {
      ensure(f) match {
        case t : ContainerType ⇒
          to.getContainers.add(t)
          tbn.put(t.getName.getOgss, t)
        case _ ⇒
      }
    }

    to.getContainers.sort { (l, r) ⇒
      val cmp = Integer.compare(l.getName.getOgss.length(), r.getName.getOgss.length())
      if (0 != cmp) cmp
      else l.getName.getOgss.compareTo(r.getName.getOgss)
    }
  }

  // ogssname -> identifier
  private val idCache = new HashMap[String, Identifier]
  for (id ← sg.Identifiers.asScala)
    idCache(id.getOgss) = id

  /**
   * Create an identifier from string
   *
   * @note results are deduplicated
   */
  final def toIdentifier(text : String) : Identifier = {
    if (null == text)
      return null

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
        val r = sg.Identifiers.make
        r.setOgss(ogssName)
        r.setParts(new ArrayList(asJavaCollection(parts)))
        r
      }
    )
  }

  run
}