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
package ogss.backend.cpp

import scala.collection.JavaConverters._

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import ogss.oil.Field
import ogss.oil.ClassDef

/**
 * creates header and implementation for all type definitions
 *
 * @author Timm Felden
 */
trait TypesMaker extends AbstractBackEnd {

  @inline private final def localFieldName(implicit f : Field) : String = name(f)

  abstract override def make {
    super.make

    makeHeader
    makeSource
  }

  private final def makeHeader {

    // one header per base type
    for (base ← IR.par if null == base.getSuperType) {
      val out = files.open(s"TypesOf${name(base)}.h")

      base.getSubTypes

      // get all customizations in types below base, so that we can generate includes for them
      val customIncludes = gatherCustomIncludes(base).toSet.toArray.sorted

      //includes package
      out.write(s"""${beginGuard(s"types_of_${name(base)}")}
#include <ogss/api/types.h>
#include <ogss/api/Exception.h>
#include <cassert>
#include <vector>
#include <set>
#include <map>
${customIncludes.map(i ⇒ s"#include <$i>\n").mkString}
${
        if (enums.isEmpty) ""
        else """#include "enums.h""""
      }

namespace ogss{
    namespace internal {
        template<class T>
        class Book;

        template<class T>
        class Pool;

        template<class T>
        class SubPool;
    }
}

${packageParts.mkString("namespace ", " {\nnamespace ", " {")}
${
        if (!visited.isEmpty) s"""
    // predef visitor
    namespace api {
        class Visitor;
    }
"""
        else ""
      }
    // type predef for cyclic dependencies${
        (for (t ← IR) yield s"""
    class ${name(t)};""").mkString
      }
    // type predef known fields for friend declarations
    namespace internal {${
        (for (t ← IR if base == t.getBaseType; f ← t.getFields.asScala) yield s"""
        class ${knownField(f)};""").mkString
      }${
        (for (t ← IR if base == t.getBaseType if !t.getFields.isEmpty()) yield s"""
        template<class T, class B>
        struct ${builder(t)};""").mkString
      }
    }
    // begin actual type defs
""")

      for (t ← IR if base == t.getBaseType) {
        val fields = allFields(t)
        val Name = name(t)
        val SuperName = if (null != t.getSuperType()) name(t.getSuperType)
        else "::ogss::api::Object"

        //class declaration
        out.write(s"""
    ${
          comment(t)
        }class $Name : public $SuperName {
        friend class ::ogss::internal::Book<${name(t)}>;
        friend class ::ogss::internal::Pool<${name(t)}>;${
          (for (f ← t.getFields.asScala) yield s"""
        friend class internal::${knownField(f)};""").mkString
        }${
          if (t.getFields.isEmpty()) ""
          else s"""
        template<class T, class B>
        friend struct internal::${builder(t)};"""
        }

    protected:
""")
        // fields
        out.write((for (f ← t.getFields.asScala)
          yield s"""    ${mapType(f.getType())} ${localFieldName(f)};
""").mkString)

        // constructor
        out.write(s"""
        $Name() { }

    public:
""")

        // accept visitor
        if (visited.contains(t.getName)) {
          out.write(s"""
        virtual void accept(api::Visitor *v);
""")
        }

        // reveal skill id
        if (revealObjectID && null == t.getSuperType)
          out.write("""
        inline ::ogss::ObjectID ID() const { return this->id; }
""")

        if (interfaceChecks) {
          val subs = interfaceCheckMethods.getOrElse(t.getName, HashSet())
          val supers = interfaceCheckImplementations.getOrElse(t.getName, HashSet())
          val both = subs.intersect(supers)
          subs --= both
          supers --= both
          out.write(subs.map(s ⇒ s"""
        virtual bool is${capital(s)}() const { return false; }
""").mkString)
          out.write(supers.map(s ⇒ s"""
        virtual bool is${capital(s)}() const override { return true; }
""").mkString)
          out.write(both.map(s ⇒ s"""
        inline bool is${capital(s)}() const { return true; }
""").mkString)
        }

        // custom fields
        val customizations = t.getCustoms.asScala.filter(_.getLanguage.equals("cpp")).toArray
        for (c ← customizations) {
          val opts = c.getOptions.asScala
          val default = opts.find(_.getName.toLowerCase.equals("default")).map(s ⇒ s" = ${s.getArguments.get(0)}").getOrElse("")
          out.write(s"""
        ${comment(c)}${c.getTypename} ${name(c)}$default; 
""")
        }

        ///////////////////////
        // getters & setters //
        ///////////////////////
        for (f ← t.getFields.asScala) {
          implicit val thisF = f;

          def makeSetterImplementation : String = {
            s"${
              "" /*
          f.getRestrictions.asScala.map {
            //@range
            case r:IntRangeRestriction ⇒
              (r.getLow == Long.MinValue, r.getHigh == Long.MaxValue) match {
              case (true, true)   ⇒ ""
              case (true, false)  ⇒ s"assert(${name(f)} <= ${r.getHigh}L);"
              case (false, true)  ⇒ s"assert(${r.getLow}L <= ${name(f)});"
              case (false, false) ⇒ s"assert(${r.getLow}L <= ${name(f)} && ${name(f)} <= ${r.getHigh}L);"
            }
            case r:FloatRangeRestriction if("f32".equals(f.getType.getName)) ⇒
              s"assert(${r.getLowFloat}f <= ${name(f)} && ${name(f)} <= ${r.getHighFloat}f);"
            case r:FloatRangeRestriction ⇒
              s"assert(${r.getLowDouble} <= ${name(f)} && ${name(f)} <= ${r.getHighDouble});"

            //@monotone modification check
            case r:MonotoneRestriction ⇒ "assert(id == -1L); "

            case _ ⇒ ""
          }.mkString*/
            }this->${name(f)} = ${name(f)};"
          }

          out.write(s"""
        ${comment(f)}inline ${mapType(f.getType)} ${getter(f)}() const { return ${name(f)}; }
        ${comment(f)}inline void ${setter(f)}(${mapType(f.getType)} ${name(f)}) {$makeSetterImplementation}
""")
        }

        out.write(s"""

        ::ogss::TypeID stid() const override { return ${t.getStid}; }
    };

    class ${name(t)}_UnknownSubType : public ${name(t)}, public ::ogss::api::NamedObj {

        //! bulk allocation constructor
        ${name(t)}_UnknownSubType() : ::ogss::api::NamedObj(nullptr) { };
        ${name(t)}_UnknownSubType(::ogss::ObjectID id, const ::ogss::internal::AbstractPool *pool)
            : ${name(t)}(), ::ogss::api::NamedObj(pool) { this->id = id; };

        friend class ::ogss::internal::Book<${name(t)}_UnknownSubType>;
        friend class ::ogss::internal::Pool<${name(t)}_UnknownSubType>;
        friend class ::ogss::internal::SubPool<${name(t)}_UnknownSubType>;

    public:

        ::ogss::TypeID stid() const override { return -1; }
    };
""");
      }

      // close name spaces
      out.write(s"""${packageParts.map(_ ⇒ "}").mkString}
$endGuard""")

      out.close()
    }
  }

  private final def makeSource {

    // one file per base type
    for (base ← IR if null == base.getSuperType) {
      // create cpp-Files only if we have to implement a visitor to speed-up compilation
      if (IR.exists(t ⇒ base == t.getBaseType && visited.contains(t.getName))) {
        val out = files.open(s"TypesOf${name(base)}.cpp")
        out.write(s"""#include "File.h"
#include "TypesOf${name(base)}.h"${
          (for (t ← IR if base == t.getBaseType && visited.contains(t.getName)) yield s"""
void $packageName::${name(t)}::accept($packageName::api::Visitor *v) {
    v->visit(this);
}""").mkString
        }
""")
        out.close()
      }
    }
  }

  private def gatherCustomIncludes(t : ClassDef) : Seq[String] = {
    val x = t.getCustoms.asScala.filter(_.getLanguage.equals("cpp")).flatMap {
      case null ⇒ ArrayBuffer[String]()
      case c ⇒ c.getOptions.asScala.find(
        _.getName.toLowerCase().equals("include")
      ).map(_.getArguments.asScala).getOrElse(ArrayBuffer[String]())
    }
    x ++ t.getSubTypes.asScala.collect { case c : ClassDef ⇒ c }.flatMap(gatherCustomIncludes)
  }
}
