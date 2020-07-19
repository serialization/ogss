//
// Created by Timm Felden on 03.02.16.
//

#include "FileOutputStream.h"
#include "../api/Exception.h"
#include "BufferedOutStream.h"

using namespace ogss::streams;

FileOutputStream::FileOutputStream(const std::string &path) :
  Stream(&buffer, static_cast<void*>(&buffer + BUFFER_SIZE)),
  path(path),
  file(fopen(path.c_str(), "w+")),
  bytesWriten(0) {
    if (nullptr == file)
        throw Exception(std::string("could not open file ") + path);
}

FileOutputStream::~FileOutputStream() {
    if (file)
        fclose(file);
}

void FileOutputStream::flush() {
    // prevent double flushs
    assert(base != position);

    fwrite(base, 1, position - (uint8_t *)base, file);
    bytesWriten += position - (uint8_t *)base;
    position = (uint8_t *)base;
}

void FileOutputStream::write(BufferedOutStream *out) {
    if (base != position)
        flush();

    bytesWriten += out->bytesWriten;

    // write completed buffers
    for (BufferedOutStream::Buffer &data : out->completed) {
        // there is no need to distinguish wrapped from buffered data here
        int size = std::abs(data.size);
        fwrite(data.begin, 1, size, file);
    }

    delete out;
}

void FileOutputStream::writeSized(BufferedOutStream *out) {
    assert(out->bytesWriten > 1);

    // @note write has been called before writeSized, hence base == position
    bytesWriten += out->bytesWriten;
    v64(out->bytesWriten - 2);
    flush();

    // write completed buffers
    for (BufferedOutStream::Buffer &data : out->completed) {
        // there is no need to distinguish wrapped from buffered data here
        int size = std::abs(data.size);
        fwrite(data.begin, 1, size, file);
    }

    delete out;
}
