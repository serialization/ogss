//
// Created by Timm Felden on 23.05.19.
//

#ifndef OGSS_COMMON_CPP_FIELD_ITERATOR_H
#define OGSS_COMMON_CPP_FIELD_ITERATOR_H

#include "../internal/AutoField.h"
#include "../internal/DataField.h"

namespace ogss {
    namespace internal {
        class AbstractPool;
    }

    namespace iterators {
        using internal::AbstractPool;
        /**
         * iterates over all fields of a type in an arbitrary but stable order
         *
         * @note feel free to send me a PR with a version that respects the C++ iterator API
         *
         * @author Timm Felden
         */
        class FieldIterator final {
            const AbstractPool *p;
            int i;
        public:

            /**
             * @pre p != nullptr
             */
            explicit FieldIterator(const AbstractPool *p) : p(p), i(-p->afCount) {

                while (nullptr != p && 0 == i && 0 == p->dataFields.size()) {
                    p = p->super;
                    if (p)
                        i = -p->afCount;
                }
            }

            bool hasNext() {
                return p;
            }

            /// @pre hasNext()
            internal::FieldDeclaration *next() {
                internal::FieldDeclaration *f = ((i < 0) ? (internal::FieldDeclaration *) p->autoFields[-1 - i]
                                                         : p->dataFields.at(i));
                i++;
                if (i == p->dataFields.size()) {
                    do {
                        p = p->super;
                        if (p)
                            i = -p->afCount;
                    } while (nullptr != p && 0 == i && 0 == p->dataFields.size());
                }
                return f;
            }
        };

    }
}


#endif //OGSS_COMMON_CPP_FIELD_ITERATOR_H
