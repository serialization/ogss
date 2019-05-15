//
// Created by Timm Felden on 29.03.19.
//

#ifndef OGSS_TEST_CPP_POOLBUILDER_H
#define OGSS_TEST_CPP_POOLBUILDER_H

#include "../api/types.h"
#include "../fieldTypes/FieldType.h"
#include "AbstractStringKeeper.h"

namespace ogss {
    namespace fieldTypes {
        class HullType;
    }
    namespace streams {
        class FileInputStream;
    }
    namespace internal {
        class AbstractEnumPool;

        class AbstractPool;

        class StringPool;

        struct PoolBuilder {

            /**
             * Pool is initialized with all type and field names.
             */
            virtual const AbstractStringKeeper *getSK() const = 0;


            /**
             * The size of sifa as constructed by this PoolBuilder.
             */
            const int sifaSize;

            /**
             * Known Container Constructor. Coded as kind|2 + STID_2|15 + STID_1|15. The IDs are relative to SIFA rather
             * than udts (note: STID = SIFA offset)
             *
             * @return -1 if there are no more KCCs
             */
            virtual uint32_t kcc(int id) const = 0;

            /**
             * Allocate the correct container Type for a given valid kcc (i.e. != -1).
             * If kcc is unknown, a container with api::Box-bases is constructed.
             *
             * @note It is no longer possible for unknown fields to guess the correct
             *       representation of an underlying container. Instead, boxed bases and string have kcc -1.
             */
            virtual fieldTypes::HullType *makeContainer(uint32_t kcc, TypeID tid,
                                                        fieldTypes::FieldType *kb1,
                                                        fieldTypes::FieldType *kb2) const = 0;

            /**
             * @return the name of the pool corresponding to the argument known id; return null if not a valid id
             */
            virtual api::String name(int id) const = 0;

            /**
             * Create a new base pool.
             *
             * @return an instance of the pool corresponding to the argument known id
             */
            virtual AbstractPool *make(int id, TypeID index) const = 0;

            /**
             * @return names of known enums in ascending order
             */
            virtual api::String enumName(int id) const = 0;

            /**
             * @return values of known enums in ascending order
             */
            virtual AbstractEnumPool *enumMake(
                    int id, TypeID index, const std::vector<api::String> &foundValues) const = 0;

        protected:
            explicit PoolBuilder(int sifaSize) : sifaSize(sifaSize) {}
        };
    }
}


#endif //OGSS_TEST_CPP_POOLBUILDER_H
