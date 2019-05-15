//
// Created by Timm Felden on 20.11.15.
//

#ifndef SKILL_CPP_COMMON_FIELDDECLARATION_H
#define SKILL_CPP_COMMON_FIELDDECLARATION_H

#include "../api/AbstractField.h"
#include "../restrictions/FieldRestriction.h"
#include "../streams/MappedInStream.h"
#include "../streams/BufferedOutStream.h"
#include <vector>
#include <unordered_set>
#include "../api/Object.h"

namespace ogss {
    namespace internal {
        class AbstractPool;

        template<class T>
        class Pool;

        /**
         * internal view onto field RTTI
         *
         * @author Timm Felden
         */
        class FieldDeclaration : public api::AbstractField {

        public:
            //! the ID of this field
            const TypeID fieldID;

            /**
             * reflective access to the enclosing type
             */
            AbstractPool *const owner;

        protected:
            FieldDeclaration(const FieldType *const type, api::String const name, const TypeID fieldID,
                             AbstractPool *const owner)
                    : AbstractField(type, name), fieldID(fieldID), owner(owner), restrictions(nullptr) {}

            inline Exception ParseException(long position, long begin, long end, const std::string &msg) {
                std::stringstream message;
                message << "ParseException while parsing field.\n Position" << position
                        << "\n inside " << begin << ", " << end << "\n reason: "
                        << msg << std::endl;
                return Exception(message.str());
            }

            //! allow known fields to access object IDs directly
            inline ObjectID ID(const api::Object *ref) const {
                return ref->id;
            }

            virtual ~FieldDeclaration();

            /**
             * Restriction handling.
             *
             * @note null if empty
             */
            std::unordered_set<const restrictions::FieldRestriction *> *restrictions;

        public:

            /**
             * @return true, iff there are checked restrictions
             */
            bool hasRestrictions() {
                return restrictions;
            }

            void addRestriction(const restrictions::FieldRestriction *r);

            /**
             * Check restrictions of the field.
             *
             * @return true, iff field fulfills all restrictions
             */
            virtual bool check() const = 0;


        protected:

            static inline ObjectID objectID(api::Object* v) {
                return v->id;
            }

            template<class T>
            friend
            class Pool;

            friend class AbstractPool;
        };
    }
}


#endif //SKILL_CPP_COMMON_FIELDDECLARATION_H
