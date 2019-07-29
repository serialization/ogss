//
// Created by Timm Felden on 28.03.19.
//

#ifndef OGSS_CPP_STATEINITIALIZER_H
#define OGSS_CPP_STATEINITIALIZER_H

#include "../api/File.h"
#include "../streams/FileInputStream.h"
#include "PoolBuilder.h"

namespace ogss {
namespace fieldTypes {
class FieldType;

class HullType;
} // namespace fieldTypes
namespace internal {

class AbstractPool;

class AbstractEnumPool;

using fieldTypes::AnyRefType;
using fieldTypes::HullType;
using streams::FileInputStream;

struct StateInitializer {

    static StateInitializer *make(const std::string &path,
                                  const PoolBuilder &pb, uint8_t mode);

    const std::string &path;
    std::unique_ptr<FileInputStream> in;
    bool canWrite;

    // guard from file
    std::unique_ptr<std::string> guard;

    // types
    std::vector<internal::AbstractPool *> classes;
    std::vector<HullType *> containers;
    std::vector<AbstractEnumPool *> enums;

    // complex builtin types
    StringPool *strings;
    AnyRefType *const anyRef;

    /**
     * State Initialization of Fields Array. We will memcpy the variable part of
     * the array into the first field to achieve state initialization.
     *
     * @note invariant: ∀i. SIFA[i].name == pb.KCN(i)
     * @note SIFA owns nothing; types are owned by type vectors above.
     */
    fieldTypes::FieldType **const SIFA;
    const size_t sifaSize;

    /**
     * The thread pool used to create this initializer. Nullptr, if not a
     * ParParser.
     */
    concurrent::Pool *threadPool;

  protected:
    /**
     * next SIFA ID to be used if some type is added to SIFA
     */
    int nsID;

    /**
     * The next global field ID. Note that this ID does not correspond to the ID
     * used in the file about to be read but to an ID that would be used if it
     * were written.
     *
     * @note to make this work as intended, merging known fields into the
     * dataFields array has to be done while reading F.
     * @note ID 0 is reserved for the String hull which is always present
     */
    int nextFieldID;

    StateInitializer(const std::string &path, FileInputStream *in,
                     const PoolBuilder &pb);

    /**
     * Calculate correct maxDeps values for containers used by containers.
     */
    void fixContainerMD();

  public:
    /**
     * state initializer is freed by the generated OGFile constructor
     *
     * @note in OGSS/Java this corresponds to awaitResults
     */
    virtual ~StateInitializer() noexcept(false);
};
} // namespace internal
} // namespace ogss

#endif // OGSS_TEST_CPP_STATEINITIALIZER_H
