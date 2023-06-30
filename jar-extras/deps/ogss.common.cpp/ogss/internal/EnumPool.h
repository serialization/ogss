//
// Created by Timm Felden on 15.04.19.
//

#ifndef OGSS_COMMON_INTERNAL_ENUMPOOL_H
#define OGSS_COMMON_INTERNAL_ENUMPOOL_H

#include "../api/Enum.h"
#include "../fieldTypes/FieldType.h"

namespace ogss {
namespace api {
class File;
}
namespace internal {
struct StateInitializer;

class AbstractEnumPool : public fieldTypes::FieldType {
  public:
    /**
     * Last value used for writing for loops over known values.
     * [[for(i = 0; i < last; i++)]]
     */
    const EnumBase last;

    const api::String name;

    /**
     * turn an enum constant into a proxy
     * @return nullptr, if the value is not legal (including UNKNOWN)
     */
    virtual api::AbstractEnumProxy *proxy(EnumBase target) const = 0;

    /**
     * @return begin of all instances
     */
    virtual api::AbstractEnumProxy **begin() const = 0;

    /**
     * @return end of all instances
     */
    virtual api::AbstractEnumProxy **end() const = 0;

    /**
     * @return the default value obtained from file; if no file has been read,
     * the default value from the specification is used
     */
    virtual api::AbstractEnumProxy *fileDefault() const = 0;

  protected:
    AbstractEnumPool(int tid, const api::String name, const EnumBase last) :
      FieldType(tid), last(last), name(name){};

    ~AbstractEnumPool() override = default;

    friend struct StateInitializer;
    friend class api::File;
};

class Creator;
class Writer;

template <typename T> class EnumPool final : public AbstractEnumPool {
    /**
     * values as seen from the combined specification
     */
    std::vector<api::EnumProxy<T> *> values;

    /**
     * values from the perspective of the files specification, i.e. this table
     * is used to decode values from disc
     */
    api::EnumProxy<T> **fileValues;
    size_t fvCount;

    /**
     * values from the perspective of the tools specification, i.e. this table
     * is used to convert enum values to proxies
     */
    api::EnumProxy<T> **staticValues;
    size_t svCount;

  public:
    /**
     * @note Shall only be called by generated PBs!
     */
    EnumPool(int tid, api::String name,
             const std::vector<api::String> &foundValues,
             api::String *const known, const EnumBase last) :
      AbstractEnumPool(tid, name, last), values() {

        api::EnumProxy<T> *p;
        if (foundValues.empty()) {
            // only known values, none from file
            // @note we set file values anyway to get sane default values
            fvCount = 0;
            staticValues = new api::EnumProxy<T> *[svCount = last];
            values.reserve(last);
            for (EnumBase i = 0; i < last; i++) {
                p = new api::EnumProxy<T>((T)i, this, known[i], i);
                values.push_back(p);
                staticValues[i] = p;
            }
        } else {
            fileValues = new api::EnumProxy<T> *[fvCount = foundValues.size()];

            // check if there is a known enum associated with this pool
            if (!known) {
                svCount = 0;
                values.reserve(foundValues.size());
                for (size_t i = 0; i < foundValues.size(); i++) {
                    p = new api::EnumProxy<T>(T::UNKNOWN, this, foundValues[i],
                                              i);
                    values.push_back(p);
                    fileValues[i] = p;
                }
            } else {
                staticValues = new api::EnumProxy<T> *[svCount = last];
                values.reserve(last);

                // merge file values and statically known values
                int id = 0, fi = 0;
                EnumBase ki = 0;
                while ((fi < foundValues.size()) | (ki < last)) {
                    int cmp = ki < last ? (fi < foundValues.size()
                                             ? api::ogssLess::javaCMP(
                                                 foundValues[fi], known[ki])
                                             : 1)
                                        : -1;

                    if (0 == cmp) {
                        p = new api::EnumProxy<T>((T)ki, this, foundValues[fi],
                                                  id++);

                        fileValues[fi++] = p;
                        staticValues[ki++] = p;
                        values.push_back(p);

                    } else if (cmp < 0) {
                        p = new api::EnumProxy<T>(T::UNKNOWN, this,
                                                  foundValues[fi], id++);
                        fileValues[fi++] = p;
                        values.push_back(p);
                    } else {
                        p = new api::EnumProxy<T>((T)ki, this, known[ki], id++);
                        staticValues[ki++] = p;
                        values.push_back(p);
                    }
                }
            }
        }
    }

    ~EnumPool() final {
        // all proxies from either source are in values, so delete only them
        for (api::EnumProxy<T> *v : values)
            delete v;
        if (svCount)
            delete[] staticValues;
        if (fvCount)
            delete[] fileValues;
    }

    //! return proxy for enum constant
    api::EnumProxy<T> *get(T target) const {
        assert(target != (T)-1);
        return staticValues[(EnumBase)target];
    }

    //! return type-erased proxy for type-erased enum constant
    api::AbstractEnumProxy *proxy(EnumBase target) const final {
        assert(0 <= target && target < svCount);
        return staticValues[target];
    }

    api::Box r(streams::InStream &in) const final {
        api::Box r{};
        EnumBase target = in.v32();
        assert(0 <= target && target < fvCount);
        r.enumProxy = fileValues[target];
        return r;
    }

    /**
     * This is a bit of a hack, because we allow v to be both, an enum value and
     * an EnumProxy*.
     */
    bool w(api::Box v, streams::BufferedOutStream *out) const final {
        const auto ID = ((0 <= v.i64) & (v.i64 < svCount))
                          ? v.i64
                          : (v.enumProxy ? v.enumProxy->id : 0);
        if (ID) {
            out->v64(ID);
            return false;
        } else {
            out->i8(0);
            return true;
        }
    }

    api::AbstractEnumProxy **begin() const final {
        return (api::AbstractEnumProxy **)values.data();
    }

    api::AbstractEnumProxy **end() const final {
        return (api::AbstractEnumProxy **)(values.data() + values.size());
    }

    api::AbstractEnumProxy *fileDefault() const final {
        return fvCount ? fileValues[0] : staticValues[0];
    }

    friend class Creator;
    friend class Writer;
};
} // namespace internal
} // namespace ogss

#endif // OGSS_COMMON_INTERNAL_ENUMPOOL_H
