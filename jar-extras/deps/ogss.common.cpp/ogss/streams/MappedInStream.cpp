//
// Created by Timm Felden on 04.11.15.
//

#include "MappedInStream.h"

ogss::streams::MappedInStream::MappedInStream(
        void *base, uint8_t *position, void *end)
        : InStream(base, end) {
    this->position = position;
}

ogss::streams::MappedInStream::MappedInStream(const ogss::streams::MappedInStream *other, size_t begin, size_t end)
        : InStream(other->position + begin, other->position + end) {
    this->position = (uint8_t *) base;
}
