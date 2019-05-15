//
// Created by Timm Felden on 29.01.16.
//

#ifndef SKILL_CPP_COMMON_ALLOBJECTITERATOR_H
#define SKILL_CPP_COMMON_ALLOBJECTITERATOR_H

#include "TypeOrderIterator.h"

namespace ogss {
    namespace api {
        class Object;
    }
    namespace iterators {
        /**
         * wraps a type order iterator
         *
         * @author Timm Felden
         */
        struct AllObjectIterator :
                public std::iterator<std::input_iterator_tag, api::Object *> {

            virtual AllObjectIterator &operator++() = 0;

            virtual api::Object *next() = 0;

            virtual bool hasNext() const = 0;

            virtual bool operator==(const AllObjectIterator &iter) const = 0;

            virtual bool operator!=(const AllObjectIterator &iter) const = 0;

            virtual api::Object &operator*() const = 0;

            virtual api::Object *operator->() const = 0;

            template<class T>
            class Implementation;
        };

        /**
         * implementation of AllObjectIterator requires template arguments.
         *
         * @author Timm Felden
         */
        template<class T>
        class AllObjectIterator::Implementation : public AllObjectIterator {
            TypeOrderIterator <T> iter;

        public:
            Implementation() : iter() {}

            explicit Implementation(const Pool<T> *p) : iter(p) {}

            Implementation(const Implementation<T> &i) : iter(i.iter) {}

            virtual AllObjectIterator &operator++() {
                ++iter;
                return *this;
            }

            virtual api::Object *next() { return iter.next(); }

            virtual bool hasNext() const { return iter.hasNext(); }

            virtual bool operator==(const AllObjectIterator &other) const {
                return iter == ((const Implementation<T> &) other).iter;
            }

            virtual bool operator!=(const AllObjectIterator &other) const {
                return iter != ((const Implementation<T> &) other).iter;
            }

            virtual api::Object &operator*() const { return *iter; }

            virtual api::Object *operator->() const { return iter.operator->(); }
        };
    }
}

#endif //SKILL_CPP_COMMON_ALLOBJECTITERATOR_H
