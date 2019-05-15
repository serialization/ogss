//
// Created by Timm Felden on 28.12.15.
//

#ifndef SKILL_CPP_COMMON_STATIC_DATA_ITERATOR_H
#define SKILL_CPP_COMMON_STATIC_DATA_ITERATOR_H

#include <iterator>
#include "../internal/Pool.h"


namespace ogss {
    namespace internal {
        template<class T>
        class Pool;
    }
    using internal::Pool;

    namespace iterators {
        /**
         * Iterates efficiently over static instances of a pool.
         *
         * @author Timm Felden
         */
        template<class T>
        class StaticDataIterator :
                public std::iterator<std::input_iterator_tag, T> {

            // ! target pool
            const Pool<T> *const p;

            ObjectID index;
            ObjectID last;

            bool second;

        public:
            //! creates an empty iterator
            StaticDataIterator()
                    : p(nullptr), index(0), last(0), second(false) {}

            StaticDataIterator(const Pool<T> *p)
                    : p(p), index(p->bpo), last(index + p->staticDataInstances), second(false) {

                // mode switch, if there data is empty
                if ((index == last) | (nullptr == p->data)) {
                    second = true;
                    index = 0;
                    last = p->newObjects.size();
                }
            }

            StaticDataIterator(const StaticDataIterator &iter)
                    : p(iter.p), index(iter.index), last(iter.last), second(iter.second) {}

            StaticDataIterator &operator++() {
                if (++index == last) {
                    if (!second) {
                        second = true;
                        index = 0;
                        last = p->newObjects.size();
                    }
                }
                return *this;
            }

            const StaticDataIterator operator++(int) {
                StaticDataIterator tmp(*this);
                operator++();
                return tmp;
            }

            //! move to next position and return current element
            T *next() {
                auto r = second ? p->newObjects[index] : p->data[index];
                if (++index == last) {
                    if (!second) {
                        second = true;
                        index = 0;
                        last = p->newObjects.size();
                    }
                }
                return r;
            }

            //! @return true, iff another element can be returned
            bool hasNext() const {
                return index != last;
            }

            //! @note all empty iterators are considered equal
            bool operator==(const StaticDataIterator<T> &iter) const {
                return (!hasNext() && !iter.hasNext())
                       || (p == iter.p &&
                           second == iter.second &&
                           index == iter.index &&
                           last == iter.last);
            }

            //! @note all empty iterators are considered equal
            bool operator!=(const StaticDataIterator<T> &iter) const {
                return (hasNext() || iter.hasNext())
                       && !(p == iter.p &&
                            second == iter.second &&
                            index == iter.index &&
                            last == iter.last);
            }

            T &operator*() const {
                // @note increment happens before access, because we shifted data by 1
                return *(second ? p->newObjects[index] : p->data[index]);
            }

            T *operator->() const {
                return second ? p->newObjects[index] : p->data[index];
            }

            //!iterators themselves can be used in generalized for loops
            //!@note this will not consume the iterator
            StaticDataIterator<T> begin() const {
                return *this;
            }

            StaticDataIterator<T> end() const {
                return StaticDataIterator<T>();
            }
        };
    }
}

#endif //SKILL_CPP_COMMON_STATIC_DATA_ITERATOR_H
