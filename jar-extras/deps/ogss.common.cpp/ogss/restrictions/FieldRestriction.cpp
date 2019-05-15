//
// Created by Timm Felden on 24.11.15.
//

#include "FieldRestriction.h"

ogss::restrictions::FieldRestriction::~FieldRestriction() {

}

const ogss::restrictions::NonNull ogss::restrictions::NonNull::instance;

const ogss::restrictions::NonNull *ogss::restrictions::NonNull::get() {
    return &instance;
}

const ogss::restrictions::ConstantLengthPointer ogss::restrictions::ConstantLengthPointer::instance;

const ogss::restrictions::ConstantLengthPointer *ogss::restrictions::ConstantLengthPointer::get() {
    return &instance;
}
