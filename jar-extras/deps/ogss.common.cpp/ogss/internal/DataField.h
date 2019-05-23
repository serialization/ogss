//
// Created by Timm Felden on 01.04.19.
//

#ifndef OGSS_TEST_CPP_DATAFIELD_H
#define OGSS_TEST_CPP_DATAFIELD_H

#include "FieldDeclaration.h"
#include "AbstractPool.h"

namespace ogss {
    namespace internal {
        class ParReadTask;

        class SeqReadTask;

        class Writer;

        /**
         * A field that is associated with data stored in a file.
         *
         * @author Timm Felden
         */
        class DataField : public FieldDeclaration {
        protected:

            DataField(const FieldType *const type, api::String const name,
                      const TypeID fieldID, AbstractPool *const owner)
                    : FieldDeclaration(type, name, fieldID, owner) {
                assert(fieldID);
                owner->dataFields.push_back(this);
            }

            /**
             * Read data from a mapped input stream and set it accordingly. This is invoked at the very end of state
             * construction and done massively in parallel.
             */
            virtual void read(int i, int last, streams::MappedInStream &in) const = 0;

            /**
              * write data into a map at the end of a write/append operation
              *
              * @note only called, if there actually is field data to be written
              * @return true iff the written data contains default values only
              */
            virtual bool write(int i, int last, streams::BufferedOutStream *out) const = 0;

            friend class ParReadTask;

            friend class SeqReadTask;

            friend class Writer;
        };

    }
}


#endif //OGSS_TEST_CPP_DATAFIELD_H
