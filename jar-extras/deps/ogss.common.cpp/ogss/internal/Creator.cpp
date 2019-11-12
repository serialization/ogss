//
// Created by Timm Felden on 29.03.19.
//

#include "Creator.h"
#include "EnumPool.h"

#include "../fieldTypes/HullType.h"
#include "../internal/AutoField.h"

using namespace ogss::internal;

Creator::Creator(const std::string &path, const ogss::internal::PoolBuilder &pb)
        : StateInitializer(path, nullptr, pb) {
    guard.reset(new std::string());

    ogss::TypeID tid = 10 + classes.size();

    // Create Classes
    {
        int THH = 0;
        // the index of the next known class at index THH
        // @note to self: in C++ this should be string*[32]
        int nextID[50];
        nextID[0] = 0;
        ::ogss::api::String nextName = pb.name(0);

        AbstractPool *p = nullptr, *last = nullptr;
        while (nextName) {
            if (0 == THH) {
                if (last) {
                    last->next = nullptr;
                }
                last = nullptr;
                p = pb.make(nextID[0]++, tid++);
            } else {
                p = p->makeSub(nextID[THH]++, tid++, nullptr);
            }
            p->bpo = 0;
            p->lastID = 0;

            SIFA[nsID++] = p;
            classes.push_back(p);

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
                while (nullptr == nextName && THH != 1) {
                    p = p->super;
                    nextName = p->nameSub(nextID[--THH]);
                }
                // check at base type level
                if (nullptr == nextName) {
                    nextName = pb.name(nextID[--THH]);
                }
            }
        }
    }

    // Execute known container constructors
    {
        uint32_t kcc;
        for (int i = 0; -1u != (kcc = pb.kcc(i)); i++) {
            uint32_t kkind = (kcc >> 30u) & 3u;
            FieldType *kb1 = SIFA[kcc & 0x7FFFu];
            FieldType *kb2 = 3 == kkind ? SIFA[(kcc >> 15u) & 0x7FFFu] : nullptr;
            HullType *r = pb.makeContainer(kcc, tid++, kb1, kb2);
            SIFA[nsID++] = r;
            r->fieldID = nextFieldID++;
            containers.push_back(r);
        }
    }

    // Construct known enums
    {
        int ki = 0;
        AbstractEnumPool *r;
        ::ogss::api::String nextName = pb.enumName(ki);
        const std::vector<::ogss::api::String> foundValues;
        // create remaining known enums
        while (nullptr != nextName) {
            r = pb.enumMake(ki++, tid++, foundValues);
            enums.push_back(r);
            SIFA[nsID++] = r;
            nextName = pb.enumName(ki);
        }
    }

    // Create Fields
    for (AbstractPool *p : classes) {
    	::ogss::api::String f;
        for (int i = 0; (f = p->KFN(i)); i++) {

            FieldDeclaration *const fd = p->KFC(i, SIFA, nextFieldID);

            if (!dynamic_cast<AutoField *>(fd)) {
                nextFieldID++;

                // increase maxDeps
                if (auto h = dynamic_cast<HullType *>(const_cast<FieldType *>(fd->type))) {
                    h->maxDeps++;
                }
            }
        }
    }

    fixContainerMD();
}
