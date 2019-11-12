//
// Created by Timm Felden on 04.11.15.
//

#include "File.h"
#include "../fieldTypes/AnyRefType.h"
#include "../internal/EnumPool.h"
#include "../internal/LazyField.h"
#include "../internal/Pool.h"
#include "../internal/StateInitializer.h"
#include "../internal/Writer.h"
#include "../iterators/FieldIterator.h"
#include "../streams/FileOutputStream.h"

#include <atomic>

using namespace ogss;
using namespace api;
using namespace internal;

// file.cpp knows anything anyway, so we realize global constants here to
// speed-up compilation
namespace ogss {
namespace fieldTypes {
BoolFieldType BoolType;
I8FieldType I8;
I16FieldType I16;
I32FieldType I32;
I64FieldType I64;
V64FieldType V64;
F32FieldType F32;
F64FieldType F64;
} // namespace fieldTypes
} // namespace ogss

File::File(internal::StateInitializer *init) :
  guard(*init->guard),
  strings(init->strings),
  anyRef(init->anyRef),
  sifaSize((int)init->sifaSize - 10),
  classCount(init->classes.size()),
  classes(new AbstractPool *[classCount]),
  containerCount(init->containers.size()),
  containers(new HullType *[containerCount]),
  enumCount(init->enums.size()),
  enums(new AbstractEnumPool *[enumCount]),
  TBN(nullptr),
  fromFile(init->in.release()),
  currentWritePath(init->path),
  canWrite(init->canWrite),
  threadPool(init->threadPool),
  SIFA{} {

    // release complex builtin types
    init->strings = nullptr;
    anyRef->owner = this;

    for (size_t i = 0; i < classCount; i++) {
        auto t = init->classes[i];
        t->owner = this;
        const_cast<AbstractPool **>(classes)[i] = t;
    }
    for (size_t i = 0; i < containerCount; i++) {
        auto t = init->containers[i];
        const_cast<HullType **>(containers)[i] = t;
    }
    for (size_t i = 0; i < enumCount; i++) {
        auto t = init->enums[i];
        const_cast<AbstractEnumPool **>(enums)[i] = t;
    }
}

File::~File() {
    for (size_t i = 0; i < classCount; i++) {
        delete classes[i];
    }
    delete[] classes;

    for (size_t i = 0; i < containerCount; i++) {
        delete containers[i];
    }
    delete[] containers;

    for (size_t i = 0; i < enumCount; i++) {
        delete enums[i];
    }
    delete[] enums;

    delete anyRef;
    delete strings;

    delete TBN;
    delete fromFile;

    delete threadPool;
}

void File::check() {
    // TODO type checks!
    //    // TODO lacks type and unknown restrictions
    //
    //    // collected checked fields
    //    std::vector<FieldDeclaration *> fields;
    //    for (auto &pair : *TBN) {
    //        const auto p = pair.second;
    //        for (const auto f : p->dataFields) {
    //            if (f->hasRestrictions())
    //                fields.push_back(f);
    //        }
    //    }
    //
    //    std::atomic<bool> failed;
    //    failed = false;
    //
    //    // @note this should be more like, each pool is checking its type
    //    restriction, aggregating its field restrictions,
    //    // and if there are any, then they will all be checked using
    //    (hopefully) overridden check methods
    //#pragma omp parallel for
    //    for (size_t i = 0; i < fields.size(); i++) {
    //        const auto f = fields[i];
    //        if (!f->check()) {
    //            std::cerr << "Restriction check failed for " <<
    //            *(f->owner->name) << "." << *(f->name) << std::endl; failed =
    //            true;
    //        }
    //    }
    //
    //    if (failed)
    //        throw SkillException("check failed");
}

void File::changePath(std::string path) {
    if (currentWritePath != path) {
        currentWritePath = path;
        canWrite = true;
    }
}

const std::string &File::currentPath() const { return currentWritePath; }

void File::changeMode(WriteMode newMode) {
    if (newMode == WriteMode::readOnly)
        canWrite = false;
    else {
        if (!canWrite)
            throw std::invalid_argument("this file is read-only");
    }
}

std::string File::to_string(::ogss::api::Object *ref) {
    if (!ref)
        return "(null)";

    const auto p = pool(ref);
    std::stringstream ss;

    ss << p->name << "#" << ref->id << "{";
    bool first = true;
    iterators::FieldIterator fs(p);
    while (fs.hasNext()) {
        auto f = fs.next();
        if (first) {
            first = false;
        } else {
            ss << ", ";
        }

        ss << *f->name << "=";
        switch (f->type->typeID) {
        case ogss::KnownTypeID::BOOL:
            ss << f->getR(ref).boolean;
            break;
        case ogss::KnownTypeID::I8:
            ss << f->getR(ref).i8;
            break;
        case ogss::KnownTypeID::I16:
            ss << f->getR(ref).i16;
            break;
        case ogss::KnownTypeID::I32:
            ss << f->getR(ref).i32;
            break;
        case ogss::KnownTypeID::I64:
        case ogss::KnownTypeID::V64:
            ss << f->getR(ref).i64;
            break;
        case ogss::KnownTypeID::F32:
            ss << f->getR(ref).f32;
            break;
        case ogss::KnownTypeID::F64:
            ss << f->getR(ref).f64;
            break;
        case ogss::KnownTypeID::STRING:
            ss << *f->getR(ref).string;
            break;
        default:
            ss << f->getR(ref).anyRef;
            break;
        }
    }

    ss << "}";

    return ss.str();
}

bool File::contains(::ogss::api::Object *ref) const {
    if (!ref)
        return false;
    const ObjectID ID = ref->id;
    if (!ID)
        return false;

    try {
        const auto p = pool(ref);

        // the returned pool could belong to another state
        if (!p || p->owner != this)
            return false;

        // ensure that ref does not belong to another state
        if (0 < ID)
            return ref == p->getAsAnnotation(ID);

        return ref == ((internal::Pool<Object> *)p)->newObjects.at(-1 - ID);
    } catch (...) {
        // out of bounds or similar mean its not one of ours
        return false;
    }
}

void File::loadLazyData() {
    // check if the file input stream is still open
    if (!fromFile)
        return;

    // ensure that strings are loaded
    ((StringPool *)strings)->loadLazyData();

    // ensure that lazy fields have been loaded
    for (AbstractPool *p : *this) {
        for (DataField *df : p->dataFields)
            if (auto f = dynamic_cast<LazyField *>(df))
                f->ensureIsLoaded();
    }

    // close the file input stream and ensure that it is not read again
    delete fromFile;
    fromFile = nullptr;
}

void File::flush() {
    if (!canWrite)
        throw std::invalid_argument("this file is read-only");

    loadLazyData();

    streams::FileOutputStream out(currentPath());
    internal::Writer write(this, out);
}

void File::close() {
    flush();
    changeMode(readOnly);
}
