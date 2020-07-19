//
// Created by Timm Felden on 28.12.15.
//

#ifndef SKILL_CPP_COMMON_TYPE_ORDER_ITERATOR_H
#define SKILL_CPP_COMMON_TYPE_ORDER_ITERATOR_H

#include <iterator>
#include "StaticDataIterator.h"
#include "TypeHierarchyIterator.h"


namespace ogss {
    namespace internal {
        template<class T>
        class Pool;
    }
    using internal::Pool;

    namespace iterators {

        /**
         * Iterates efficiently over dynamic instances of a pool in type order.
         *
         * @author Timm Felden
         */
        template<class T>
        class TypeOrderIterator :
                public std::iterator<std::input_iterator_tag, T> {

            TypeHierarchyIterator ts;
            StaticDataIterator<T> is;

        public:
            //! creates an empty iterator
            //! @note is is initialized exactly once by the body
            TypeOrderIterator() : ts() {}

            TypeOrderIterator(const Pool<T> *p)
                    : ts(p), is(p) {
                while (ts.hasNext()) {
                    auto t = static_cast<const Pool<T> *>(ts.next());
                    if (t->staticSize()) {
                        new(&is) StaticDataIterator<T>(t);
                        return;
                    }
                }
                // the iterator is empty, so p is empty as well. Hence, the overall state
                // is legal in terms of the iterators invariants
                new(&is) StaticDataIterator<T>(p);
            }

            TypeOrderIterator(const TypeOrderIterator &iter)
                    : ts(iter.ts), is(iter.is) {}

            TypeOrderIterator &operator++() {
                is.next();
                if (!is.hasNext()) {
                    while (ts.hasNext()) {
                        auto t = static_cast<const Pool<T> *>(ts.next());
                        if (t->staticSize()) {
                            new(&is) StaticDataIterator<T>(t);
                            break;
                        }
                    }
                }
                return *this;
            }

            TypeOrderIterator operator++(int) {
                TypeHierarchyIterator tmp(*this);
                operator++();
                return tmp;
            }

            //! move to next position and return current element
            T *next() {
                T *result = is.next();
                if (!is.hasNext()) {
                    while (ts.hasNext()) {
                        auto t = static_cast<const Pool<T> *>(ts.next());
                        if (t->staticSize()) {
                            new(&is) StaticDataIterator<T>(t);
                            break;
                        }
                    }
                }
                return result;
            }

            //! @return true, iff another element can be returned
            bool hasNext() const {
                return is.hasNext();
            }

            bool operator==(const TypeOrderIterator &iter) const {
                return !hasNext() && !iter.hasNext();
            }

            bool operator!=(const TypeOrderIterator &iter) const {
                return hasNext() || iter.hasNext();
            }

            T &operator*() const { return *is; }

            T *operator->() const { return is.operator->(); }

            //!iterators themselves can be used in generalized for loops
            //!@note this will not consume the iterator
            TypeOrderIterator<T> begin() const {
                return *this;
            }

            TypeOrderIterator<T> end() const {
                return TypeOrderIterator<T>();
            }
        };
    }
}

#endif //SKILL_CPP_COMMON_TYPE_ORDER_ITERATOR_H
