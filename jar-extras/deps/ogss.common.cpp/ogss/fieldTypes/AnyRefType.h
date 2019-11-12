//
// Created by Timm Felden on 01.12.15.
//

#ifndef SKILL_CPP_COMMON_ANNOTATIONTYPE_H
#define SKILL_CPP_COMMON_ANNOTATIONTYPE_H

#include "../internal/AbstractPool.h"
#include "BuiltinFieldType.h"
#include "../api/File.h"
#include "../internal/StringPool.h"
#include <memory>

namespace ogss {
    namespace api {
        class File;
    }

    using streams::InStream;
    namespace fieldTypes {

        /**
         * the implementation of annotations may not be fast (at all)
         *
         * @note maybe, the skill names returned by instances should point to
         * their pool's references, so that we can create an internal mapping from
         * const char* ⇒ type, which is basically a perfect hash map, because strings
         * will then be unique and we no longer have to care for identical copies
         */
        class AnyRefType : public BuiltinFieldType<void *, 8> {
            internal::StringPool *const string;
            /**
             * Used to read annotations from file.
             */
            std::vector<internal::AbstractPool *> *const fdts;
            /**
             * annotations will not be queried from outside, thus we can directly use char* obtained from
             * skill string pointers
             */
            api::File *owner;

        public:
            AnyRefType(internal::StringPool *string, std::vector<internal::AbstractPool *> *fdts)
                    : string(string), fdts(fdts), owner(nullptr) {}

            virtual ~AnyRefType() {}

            api::Box r(streams::InStream &in) const final {
                api::Box r = {};
                TypeID t = (TypeID) in.v32();
                if (!t)
                    return r;

                const ObjectID id = (ObjectID) in.v32();
                if (1 == t)
                    return string->get(id);

                r.anyRef = fdts->at(t - 2)->getAsAnnotation(id);

                return r;
            }

            bool w(api::Box target, streams::BufferedOutStream *out) const final {
                if (target.anyRef) {
                    if (auto ref = dynamic_cast<::ogss::api::Object *>(target.anyRef)) {
                        out->v64(owner->pool(ref)->typeID - 8);
                        out->v64(target.anyRef->id);
                    } else {
                        out->i8(1);
                        out->v64(owner->strings->id(target.string));
                    }
                    return false;
                } else {
                    out->i8(0);
                    return true;
                }
            }

            virtual bool requiresDestruction() const {
                return false;
            }

            friend class api::File;
        };
    }
}
#endif //SKILL_CPP_COMMON_ANNOTATIONTYPE_H
