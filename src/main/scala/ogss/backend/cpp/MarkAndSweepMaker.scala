package ogss.backend.cpp

import ogss.oil.{ClassDef, InterfaceDef, MapType, SeqType, Type}

import scala.collection.mutable

trait MarkAndSweepMaker extends AbstractBackEnd {
  case class TypeMarkImpl(name: String, argsAndImpl: String)

  abstract override def make {
    super.make
    if (!generateMarkAndSweep) return
    makeHeader
    makeSource
  }

  private def makeHeader {
    val out = files.open("MarkAndSweep.h")
    out.write(s"""${beginGuard("MARK_AND_SWEEP")}
#include <ogss/api/AbstractMarkAndSweep.h>

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
    namespace api {
        class MarkAndSweep final : public ::ogss::api::AbstractMarkAndSweep {
          protected:
            void markReferenced(::ogss::api::Object *obj) final;
          public:
            explicit MarkAndSweep(::ogss::api::File *file):
                    ::ogss::api::AbstractMarkAndSweep(file) {}
        };
    }
${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")
    out.close()
  }

  private def makeSource {
    val markImpls = mutable.Map[ClassDef, TypeMarkImpl]()
    for (c ← IR) {
      val impl = genClassMarker(c)
      if (impl != null) markImpls += (c -> impl)
    }

    val out = files.open("MarkAndSweep.cpp")
    out.write(
      s"""
#include "MarkAndSweep.h"
#include "File.h"

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}

${markImpls.map(m ⇒s"""
static inline void ${m._2.name}${m._2.argsAndImpl}""").mkString}

void api::MarkAndSweep::markReferenced(::ogss::api::Object *obj) {
    switch (obj->stid()) {
        case -1: throw std::logic_error("MarkAndSweep not implemented on NamedObj!");${IR.map(t ⇒ {
          val typeName = packageName + "::" + name(t)
          s"""
        case $typeName::typeID: ${callAllFields(t, typeName, markImpls)}"""
        }).mkString}
    }
}
${packageParts.map(_ ⇒ "}").mkString}
""")
    out.close()
  }

  private def callAllFields(t : ClassDef, typeName : String, markers: mutable.Map[ClassDef, TypeMarkImpl]) = {
    // Set ensures that we don't get double classes
    // if multiple interfaces have the same base type
    val ret = mutable.Set[ClassDef]()
    recIncludeAncestors(ret, t)
    val calls = ret.map(c ⇒ {
      markers.get(c) match {
        case Some(m) ⇒
          s"""
            ${m.name}(*this, v);"""
        case None ⇒ ""
      }
    }).mkString
    if (calls.isEmpty) "break;" else
      s"""{
            auto *v = static_cast<$typeName*>(obj);$calls
            break;
        }"""
  }

  private def recIncludeAncestors(s : mutable.Set[ClassDef], i : InterfaceDef): Unit = {
    if (i.superType != null) recIncludeAncestors(s, i.superType)
    i.superInterfaces.foreach(i ⇒ recIncludeAncestors(s, i))
  }

  private def recIncludeAncestors(s : mutable.Set[ClassDef], t : ClassDef): Unit = {
    s += t
    if (t.superType != null) recIncludeAncestors(s, t.superType)
    t.superInterfaces.foreach(i ⇒ recIncludeAncestors(s, i))
  }

  private def genClassMarker(t : ClassDef) = {
    val actions = t.fields.map(f ⇒
      if (f.isTransient || f.isDeleted) "" else processValue(f.`type`, s"v->${getter(f)}()")
    ).mkString
    if (actions.isEmpty) null else TypeMarkImpl(s"mark_${name(t)}_fields",
      s"(api::MarkAndSweep &m, $packageName::${name(t)}* v){$actions\n}\n")
  }

  private def processValue(t : Type, v : String): String = {
    t match {
      case _ : ClassDef ⇒
        s"""
    m.mark($v);"""
      case s : SeqType ⇒
        val inner = processValue(s.baseType, "item")
        if (inner.isEmpty) "" else s"""
    if ($v != nullptr) for (auto *item : *$v) {$inner
    }"""
      case m : MapType ⇒
        val innerK = processValue(m.keyType, "kv.first")
        val innerV = processValue(m.valueType, "kv.second")
        if (innerK.isEmpty && innerV.isEmpty) "" else
          s"""
    if ($v != nullptr) for (auto &kv : *$v) {$innerK$innerV
    }"""
      case _ ⇒ ""
    }
  }
}
