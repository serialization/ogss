//
// Created by Timm Felden on 27.01.16.
//

#ifndef SKILL_CPP_COMMON_API_SET_H
#define SKILL_CPP_COMMON_API_SET_H

#include <unordered_set>
#include <memory>
#include "Box.h"

namespace ogss {
    namespace api {

        struct SetIterator {
            virtual bool hasNext() const = 0;

            virtual Box next() = 0;
        };

        /**
         * A set that statically has no information about its content.
         *
         * @author Timm Felden
         * @note if you know the type runtime type, it is safe to cast down to Set<T>
         * @note representation is always a std::set
         */
        struct BoxedSet {

            virtual ~BoxedSet() {}

            /**
             * b € this
             */
            virtual bool contains(const Box &b) const = 0;

            /**
             * add b to the set
             */
            virtual void add(const Box &b) = 0;

            virtual size_t length() const = 0;

            virtual std::unique_ptr<SetIterator> all() = 0;
        };

        /**
         * Actual representation of skill sets.
         */
        template<typename T>
        class Set : public std::unordered_set<T>, public BoxedSet {
            typedef typename std::unordered_set<T>::iterator iter;

            class BoxedIterator : public SetIterator {
                iter state;
                const iter last;

            public:
                BoxedIterator(Set *self) : state(self->begin()), last(self->end()) {}

                virtual bool hasNext() const {
                    return state != last;
                }

                virtual Box next() {
                    return box(*state++);
                }
            };

        public:

            using typename std::unordered_set<T>::value_type;

            Set() : std::unordered_set<T>() {}
            Set(std::initializer_list<value_type> init) :
                std::unordered_set<T>(init) {}
            Set(const Set &other) : std::unordered_set<T>(other) {}

            virtual ~Set() {};

            virtual bool contains(const Box &b) const {
                return this->find(unbox<T>(b)) != this->end();
            }

            virtual void add(const Box &b) {
                this->insert(unbox<T>(b));
            }

            virtual size_t length() const {
                return this->size();
            }

            virtual std::unique_ptr<SetIterator> all() {
                return std::unique_ptr<SetIterator>(new BoxedIterator(this));
            }
        };
    }
}

#endif //SKILL_CPP_COMMON_API_SET_H
