//
// Created by Timm Felden on 01.04.19.
//

#ifndef OGSS_TEST_CPP_DATAFIELD_H
#define OGSS_TEST_CPP_DATAFIELD_H

#include "AbstractPool.h"
#include "FieldDeclaration.h"

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
              const TypeID fieldID, AbstractPool *const owner) :
      FieldDeclaration(type, name, fieldID, owner),
      blocks(0) {
        assert(fieldID);
        owner->dataFields.push_back(this);
    }

    /**
     * Read data from a mapped input stream and set it accordingly. This is
     * invoked at the very end of state construction and done massively in
     * parallel.
     */
    virtual void read(int i, int last, streams::MappedInStream &in) const = 0;

    /**
     * write data into a map at the end of a write/append operation
     *
     * @note only called, if there actually is field data to be written
     * @return true iff the written data contains default values only
     */
    virtual bool write(int i, int last,
                       streams::BufferedOutStream *out) const = 0;

    //! number of remaining blocks while treating this field
    //! @note this field is initialized on use and has no meaning otherwise
    std::atomic<int32_t> blocks;

    friend class ParReadTask;

    friend class SeqReadTask;

    friend class Writer;
};

} // namespace internal
} // namespace ogss

#endif // OGSS_TEST_CPP_DATAFIELD_H
