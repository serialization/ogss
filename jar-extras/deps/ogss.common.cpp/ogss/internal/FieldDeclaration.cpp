//
// Created by Timm Felden on 20.11.15.
//

#include "FieldDeclaration.h"
#include "AbstractPool.h"

void ogss::internal::FieldDeclaration::addRestriction(const ogss::restrictions::FieldRestriction *r) {
    SK_TODO;
}

ogss::internal::FieldDeclaration::~FieldDeclaration() {
//    // delete stateful restrictions
//    for (auto c : checkedRestrictions) {
//        if (3 == c->id) // range
//            delete c;
//    }
//    for (auto c : otherRestrictions) {
//        if (1 == c->id || 5 == c->id) // default, coding
//            delete c;
//    }
//
//    for (auto c : dataChunks)
//        delete c;
}