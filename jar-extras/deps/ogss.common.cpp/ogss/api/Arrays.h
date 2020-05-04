//
// Created by Timm Felden on 27.01.16.
//

#ifndef SKILL_CPP_COMMON_ARRAYS_H
#define SKILL_CPP_COMMON_ARRAYS_H

#include <vector>
#include "Box.h"

namespace ogss {
    namespace api {

        /**
         * An array that statically has no information about its content.
         *
         * @author Timm Felden
         * @note if you know the type runtime type, it is safe to cast down to Array<T>
         * @note representation is always a std::vector
         */
        struct BoxedArray {
            virtual ~BoxedArray() {}

            /**
             * get boxed value at position i
             *
             * @note the box is not stored in the actual container,
             * thus it is not possible to have a std::vector style operator[]
             */
            virtual Box get(size_t i) const = 0;

            /**
             * update value stored at position i
             *
             * @note same as (*this)[i] = v
             */
            virtual void update(size_t i, Box v) = 0;

            virtual size_t length() const = 0;

            /**
             * ensures that the vector is of size i
             */
            virtual void ensureSize(size_t i) = 0;
        };

        /**
         * Actual representation of skill arrays.
         */
        template<typename T>
        struct Array : public std::vector<T>, public BoxedArray {

            Array() : std::vector<T>() {}
            Array(std::initializer_list<T> init) : std::vector<T>(init) {}
            Array(const Array &other) : std::vector<T>(other) {}

            virtual ~Array() {}

            virtual Box get(size_t i) const {
                return box(this->at(i));
            }

            virtual void update(size_t i, Box v) {
                (*this)[i] = unbox<T>(v);
            }

            virtual size_t length() const {
                return this->size();
            }

            virtual void ensureSize(size_t i) {
                if (i > this->size())
                    this->resize(i);
            }
        };
    }
}

#endif //SKILL_CPP_COMMON_ARRAYS_H
