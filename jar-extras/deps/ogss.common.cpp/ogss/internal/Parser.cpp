//
// Created by Timm Felden on 03.04.19.
//

#include "Parser.h"
#include "AutoField.h"
#include "DataField.h"
#include "EnumPool.h"
#include "LazyField.h"
#include "SubPool.h"
#include "UnknownObject.h"

#include "../fieldTypes/AnyRefType.h"
#include "../fieldTypes/ArrayType.h"
#include "../fieldTypes/ListType.h"
#include "../fieldTypes/MapType.h"
#include "../fieldTypes/SetType.h"

void ogss::internal::Parser::parseFile(FileInputStream *in) {
    // G
    {
        const uint8_t first = in->i8();
        // guard is 22 26?
        if (first == 0x22) {
            if (in->i8() != 0x26)
                ParseException(in, "Illegal guard.");

            guard.reset(new std::string());
        }
        // guard is hash?
        else if (first == 0x23) {
            auto buf = new std::string();
            guard.reset(buf);
            char next;
            while ((next = in->i8())) {
                buf += next;
            }
        } else
            ParseException(in, "Illegal guard.");
    }

    // S
    try {
        fields.push_back(strings);
        strings->readSL(in);
    } catch (std::exception &e) {
        ParseException(in, std::string("corrupted string block: ") += e.what());
    }

    // T
    typeBlock();

    fixContainerMD();

    // HD
    processData();

    if (!in->eof()) {
        ParseException(in, "Expected end of file, but some bytes remain.");
    }
}

ogss::internal::Parser::Parser(const std::string &path, FileInputStream *in,
                               const PoolBuilder &pb) :
  StateInitializer(path, in, pb),
  pb(pb),
  fields(),
  fdts() {}

void ogss::internal::Parser::ParseException(ogss::InStream *in,
                                            const std::string &msg) {
    throw Exception(std::string("ParseException at ") +
                    std::to_string(in->getPosition()) + ": " + msg);
}

void ogss::internal::Parser::typeDefinitions() {
    int nextTID = 10;
    int THH = 0;
    // the index of the next known class at index THH
    int nextID[50];
    nextID[0] = 0;
    // the nextName, null if there is no next PD
    String nextName = pb.name(0);

    AbstractPool *p = nullptr, *last = nullptr;
    AbstractPool *result;

    // Name of all seen class names to prevent duplicate allocation of the same
    // pool.
    std::unordered_set<String> seenNames;
    int TCls = in->v32();

    // file state
    String name = nullptr;
    int count = 0;
    AbstractPool *superDef = nullptr;
    std::unordered_set<TypeRestriction *> *attr = nullptr;
    int bpo = 0;

    for (bool moreFile; (moreFile = (TCls > 0)) | (nullptr != nextName);
         TCls--) {
        // read next pool from file if required
        if (moreFile) {
            // name
            name = static_cast<String>(strings->idMap.at(in->v32()));
            if (!name) {
                ParseException(in.get(),
                               "corrupted file: nullptr in type name");
            }

            // static size
            count = in->v32();

            // attr
            {
                const int rc = in->v32();
                if (0 == rc)
                    attr = nullptr;
                else
                    attr = typeRestrictions(rc);
            }

            // super
            {
                const TypeID superID = in->v32();
                if (0 == superID) {
                    superDef = nullptr;
                    bpo = 0;
                } else if (superID > fdts.size())
                    ParseException(in.get(),
                                   std::string("Type ") + *name +
                                     " refers to an ill-formed super type.\n   "
                                     "       found: " +
                                     std::to_string(superID) +
                                     "; current number of other types " +
                                     std::to_string(fdts.size()));
                else {
                    superDef = (AbstractPool *)fdts[superID - 1];
                    bpo = in->v32();
                }
            }
        }

        // allocate pool
        bool keepKnown, keepFile = !moreFile;
        api::ogssLess compare;
        do {
            keepKnown = (nullptr == nextName);

            if (moreFile) {
                // check common case, i.e. the next class is the expected one
                if (!keepKnown) {
                    if (superDef == p) {
                        if (name == nextName) {
                            // the next pool is the expected one
                            keepFile = false;

                        } else if (compare(name, nextName)) {
                            // we have to advance the file pool
                            keepKnown = true;
                            keepFile = false;

                        } else {
                            // we have to advance known pools
                            keepFile = true;
                        }
                    } else {

                        // depending on the files super THH, we can decide if we
                        // have to process the files type or our type first;
                        // invariant: p != superDef ⇒ superDef.THH != THH
                        // invariant: ∀p. p.next.THH <= p.THH + 1
                        // invariant: ∀p. p.Super = null <=> p.THH = 0
                        if (superDef && (superDef->THH < THH)) {
                            // we have to advance known pools
                            keepFile = true;

                        } else {
                            // we have to advance the file pool
                            keepKnown = true;
                            keepFile = false;
                        }
                    }
                } else {
                    // there are no more known pools
                    keepFile = false;
                }
            } else if (keepKnown) {
                // we are done
                return;
            }

            // create the next pool
            if (keepKnown) {
                // an unknown pool has to be created
                if (superDef) {
                    result = superDef->makeSub(nextTID++, name, attr);
                } else {
                    if (last) {
                        last->next = nullptr;
                    }
                    last = nullptr;
                    result = new SubPool<UnknownObject>(nextTID++, nullptr,
                                                        name, attr);
                }
                result->bpo = bpo;
                fdts.push_back(result);
                classes.push_back(result);

                // set next
                if (last) {
                    last->next = result;
                }
                last = result;
            } else {
                if (p) {
                    p = p->makeSub(nextID[THH]++, nextTID++, attr);
                } else {
                    if (last) {
                        last->next = nullptr;
                    }
                    last = nullptr;
                    p = pb.make(nextID[0]++, nextTID++);
                }
                // @note this is sane, because it is 0 if p is not part of the
                // type hierarchy of superDef
                p->bpo = bpo;
                SIFA[nsID++] = p;
                classes.push_back(p);

                if (!keepFile) {
                    result = p;
                    fdts.push_back(result);
                }

                // set next
                if (last) {
                    last->next = p;
                }
                last = p;

                // move to next pool
                {
                    // try to move down to our first child
                    nextName = p->nameSub(nextID[++THH] = 0);

                    // move up until we find a next pool
                    while (nullptr == nextName & THH != 1) {
                        p = p->super;
                        nextName = p->nameSub(nextID[--THH]);
                    }
                    // check at base type level
                    if (nullptr == nextName) {
                        p = nullptr;
                        nextName = pb.name(nextID[THH = 0]);
                    }
                }
            }
            // check for duplicate adds
            if (seenNames.find(last->name) != seenNames.end()) {
                throw ogss::Exception("duplicate definition of type " +
                                      *last->name);
            }
            seenNames.insert(last->name);
        } while (keepFile);

        result->cachedSize = result->staticDataInstances = count;

        // add a null value for each data field to ensure that the temporary
        // size of data fields matches those from file
        const int fieldCount = in->v32();
        if (fieldCount)
            result->dataFields.insert(result->dataFields.end(), fieldCount,
                                      nullptr);
    }
}

std::unordered_set<ogss::TypeRestriction *> *
ogss::internal::Parser::typeRestrictions(int count) {
    SK_TODO;
}

std::unordered_set<ogss::restrictions::FieldRestriction *> *
ogss::internal::Parser::fieldRestrictions(int count) {
    SK_TODO;
}

using ogss::fieldTypes::FieldType;

FieldType *ogss::internal::Parser::fieldType() {
    const TypeID typeID = in->v32();
    switch (typeID) {
    case 0:
        return (FieldType *)&fieldTypes::BoolType;
    case 1:
        return (FieldType *)&fieldTypes::I8;
    case 2:
        return (FieldType *)&fieldTypes::I16;
    case 3:
        return (FieldType *)&fieldTypes::I32;
    case 4:
        return (FieldType *)&fieldTypes::I64;
    case 5:
        return (FieldType *)&fieldTypes::V64;
    case 6:
        return (FieldType *)&fieldTypes::F32;
    case 7:
        return (FieldType *)&fieldTypes::F64;
    case 8:
        return anyRef;
    case 9:
        return strings;
    default:
        return fdts.at(typeID - 10);
    }
}

static uint32_t toUCC(uint32_t kind, FieldType *b1, FieldType *b2) {
    uint32_t baseTID1 = b1->typeID;
    uint32_t baseTID2 = !b2 ? 0 : b2->typeID;
    if (baseTID2 < baseTID1)
        return (baseTID1 << 17u) | (kind << 15u) | baseTID2;

    return (baseTID2 << 17u) | (kind << 15u) | baseTID1;
}

void ogss::internal::Parser::TContainer() {
    // next type ID
    int tid = 10 + classes.size();
    // KCC index
    int ki = 0;
    // @note it is always possible to construct the next kcc from SIFA
    uint32_t kcc = pb.kcc(ki);
    uint32_t kkind;
    FieldType *kb1, *kb2;
    //@note using uint means we can accept more types than Java
    uint32_t lastUCC = 0;
    uint32_t kucc;
    if (-1u != kcc) {
        kkind = (kcc >> 30u) & 3u;
        kb1 = SIFA[kcc & 0x7FFFu];
        kb2 = 3 == kkind ? SIFA[(kcc >> 15u) & 0x7FFFu] : nullptr;
        kucc = toUCC(kkind, kb1, kb2);
    } else {
        kkind = kucc = 0;
        kb1 = kb2 = nullptr;
    }

    for (int count = in->v32(); count != 0; count--) {
        const uint32_t fkind = in->i8();
        FieldType *const fb1 = fieldType();
        FieldType *const fb2 = (3 == fkind) ? fieldType() : nullptr;
        const uint32_t fucc = toUCC(fkind, fb1, fb2);

        HullType *r = nullptr;
        int cmp = -1;

        // construct known containers until we hit the state of the file
        while (-1u != kcc && (cmp = (fucc - kucc)) >= 0) {
            r = pb.makeContainer(kcc, tid++, kb1, kb2);
            SIFA[nsID++] = r;
            r->fieldID = nextFieldID++;
            containers.push_back(r);

            // check UCC order
            if (lastUCC > kucc) {
                ParseException(in.get(), "File is not UCC-ordered.");
            }
            lastUCC = kucc;

            // move to next kcc
            kcc = pb.kcc(++ki);
            if (-1u != kcc) {
                kkind = (kcc >> 30u) & 3u;
                kb1 = SIFA[kcc & 0x7FFFu];
                kb2 = 3 == kkind ? SIFA[(kcc >> 15u) & 0x7FFFu] : nullptr;
                kucc = toUCC(kkind, kb1, kb2);
            }

            // break loop for perfect matches after the first iteration
            if (0 == cmp)
                break;
        }

        // the last constructed kcc was not the type from the file
        if (0 != cmp) {
            switch (fkind) {
            case 0:
                r = new fieldTypes::ArrayType<api::Box>(tid++, kcc, fb1);
                break;
            case 1:
                r = new fieldTypes::ListType<api::Box>(tid++, kcc, fb1);
                break;
            case 2:
                r = new fieldTypes::SetType<api::Box>(tid++, kcc, fb1);
                break;

            case 3:
                r = new fieldTypes::MapType<api::Box, api::Box>(tid++, kcc, fb1,
                                                                fb2);
                break;

            default:
                throw ogss::Exception(
                  std::string("Illegal container constructor ID: ") +
                  std::to_string(fkind));
            }

            r->fieldID = nextFieldID++;
            containers.push_back(r);

            // check UCC order
            if (lastUCC > fucc) {
                ParseException(in.get(), "File is not UCC-ordered.");
            }
            lastUCC = fucc;
        }
        fields.push_back(r);
        fdts.push_back(r);
    }

    // construct remaining known containers
    while (-1u != kcc) {
        const auto r = pb.makeContainer(kcc, tid++, kb1, kb2);
        SIFA[nsID++] = r;
        r->fieldID = nextFieldID++;
        containers.push_back(r);

        // check UCC order
        if (lastUCC > kucc) {
            ParseException(in.get(), "File is not UCC-ordered.");
        }
        lastUCC = kucc;

        // move to next kcc
        kcc = pb.kcc(++ki);
        if (-1u != kcc) {
            kkind = (kcc >> 30u) & 3u;
            kb1 = SIFA[kcc & 0x7FFFu];
            kb2 = 3 == kkind ? SIFA[(kcc >> 15u) & 0x7FFFu] : nullptr;
            kucc = toUCC(kkind, kb1, kb2);
        }
    }
}

void ogss::internal::Parser::TEnum() {
    // next type ID
    int tid = 10 + classes.size() + containers.size();

    int ki = 0;
    String nextName = pb.enumName(ki);
    AbstractEnumPool *r;
    // create enums from file
    for (int count = in->v32(); count != 0; count--) {
        String name = static_cast<String>(strings->idMap.at(in->v32()));
        int vcount = in->v32();
        if (vcount <= 0)
            ParseException(in.get(),
                           std::string("Enum ") + *name + " is zero-sized.");

        std::vector<api::String> vs;
        vs.reserve(vcount);
        for (auto i = vcount; i != 0; i--) {
            vs.push_back((String)strings->idMap.at(in->v32()));
        }

        int cmp = nextName ? api::ogssLess::javaCMP(name, nextName) : -1;

        while (true) {
            if (0 == cmp) {
                r = pb.enumMake(ki++, tid++, vs);
                enums.push_back(r);
                fdts.push_back(r);
                SIFA[nsID++] = r;
                nextName = pb.enumName(ki);
                break;

            } else if (cmp < 1) {
                r = new EnumPool<api::UnknownEnum>(tid++, name, vs, nullptr, 0);
                enums.push_back(r);
                fdts.push_back(r);
                break;

            } else {
                // known, but not file
                std::vector<api::String> noVS;
                r = pb.enumMake(ki++, tid++, noVS);
                enums.push_back(r);
                SIFA[nsID++] = r;
                nextName = pb.enumName(ki);
                cmp = nextName ? api::ogssLess::javaCMP(name, nextName) : -1;
            }
        }
    }
    // create remaining known enums
    while (nextName) {
        std::vector<api::String> noVS;
        r = pb.enumMake(ki++, tid++, noVS);
        enums.push_back(r);
        SIFA[nsID++] = r;
        nextName = pb.enumName(ki);
    }
}

void ogss::internal::Parser::readFields(ogss::AbstractPool *p) {
    // C++ bullshit ;)
    api::ogssLess compare;

    // we have not yet seen a known field
    int ki = 0;

    // we pass the size by adding null's for each expected field in the stream
    // because AL.clear does not return its backing array, i.e. we will likely
    // not resize it that way
    int idx = p->dataFields.size();

    p->dataFields.clear();
    String kfn = p->KFN(0);
    while (0 != idx--) {
        // read field
        const String name =
          static_cast<String const>(strings->idMap.at(in->v32()));
        FieldType *t = fieldType();
        std::unordered_set<restrictions::FieldRestriction *> *attr;
        {
            int attrCount = in->v32();
            if (attrCount)
                attr = fieldRestrictions(attrCount);
            else
                attr = nullptr;
        }

        FieldDeclaration *f = nullptr;

        while ((kfn = p->KFN(ki))) {
            // is it the next known field?
            if (name == kfn) {
                if (dynamic_cast<AutoField *>(
                      f = p->KFC(ki++, SIFA, nextFieldID)))
                    ParseException(in.get(),
                                   std::string("Found transient field ") +
                                     *p->name + "." + *name + " in the file.");

                if (f->type != t)
                    ParseException(
                      in.get(), std::string("Field ") + *p->name + "." + *name +
                                  " should have type " +
                                  std::to_string(f->type->typeID) +
                                  " but has type " + std::to_string(t->typeID));

                break;
            }

            // else, it might be an unknown field
            if (compare(name, kfn)) {
                // create unknown field
                f = new LazyField(t, name, nextFieldID, p);
                break;
            }

            // else, it is a known field not contained in the file
            f = p->KFC(ki++, SIFA, nextFieldID);
            if (!dynamic_cast<AutoField *>(f)) {
                nextFieldID++;

                // increase maxDeps
                if (auto ft =
                      dynamic_cast<const fieldTypes::HullType *>(f->type)) {
                    const_cast<fieldTypes::HullType *>(ft)->maxDeps++;
                }
            }
            f = nullptr;
        }

        if (!f) {
            // no known fields left, so it is obviously unknown
            f = new LazyField(t, name, nextFieldID, p);
        }

        nextFieldID++;

        // increase maxDeps
        if (auto ft = dynamic_cast<const fieldTypes::HullType *>(f->type)) {
            const_cast<fieldTypes::HullType *>(ft)->maxDeps++;
        }

        // TODO f.addRestriction(rest);

        fields.push_back(f);
    }

    // create remaining auto fields
    while (p->KFN(ki)) {
        const auto f = p->KFC(ki++, SIFA, nextFieldID);
        if (!dynamic_cast<AutoField *>(f)) {
            nextFieldID++;

            // increase maxDeps
            if (auto ft = dynamic_cast<const fieldTypes::HullType *>(f->type)) {
                const_cast<fieldTypes::HullType *>(ft)->maxDeps++;
            }
        }
    }
}
