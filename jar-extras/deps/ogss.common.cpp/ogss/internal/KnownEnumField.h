//
// Created by Timm Felden on 01.04.19.
//

#ifndef OGSS_TEST_CPP_KNOWN_ENUM_FIELD_H
#define OGSS_TEST_CPP_KNOWN_ENUM_FIELD_H

#include "DataField.h"

namespace ogss {
namespace internal {

class Parser;

/**
 * A field that is known and holds an enum value. Default initialization of such
 * fields requires another iteration of instances after all data has been read.
 *
 * @author Timm Felden
 */
class KnownEnumField : public DataField {
  protected:
    KnownEnumField(const FieldType *const type, api::String const name,
                   const TypeID fieldID, AbstractPool *const owner) :
      DataField(type, name, fieldID, owner) {}

    friend class Parser;
};

} // namespace internal
} // namespace ogss

#endif // OGSS_TEST_CPP_KNOWN_ENUM_FIELD_H
