//
// Created by feldentm on 03.11.15.
//

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/stat.h>
#include <sys/mman.h>
#endif
#include "FileInputStream.h"

using namespace ogss::streams;

#ifdef _WIN32
FileInputStream::FileInputStream(void *begin, void *end, const std::string *path,
                void *file, void *mapping):
                InStream(begin, end), path(path), file(file), mapping(mapping) {
}
#else
FileInputStream::FileInputStream(void *begin, void *end, const std::string* path, const FILE *file)
        : InStream(begin, end), path(path), file(file) {
}
#endif


FileInputStream::FileInputStream(const std::string& path)
        : InStream(nullptr, nullptr), path(new std::string(path)), file(nullptr) {
#ifdef _WIN32
    mapping = nullptr;

    auto stream = ::CreateFileA(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
            nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (stream == INVALID_HANDLE_VALUE)
        throw Exception(std::string("could not open file ") + path);
    auto length = ::GetFileSize(stream, nullptr);
    if (length <= 0) return;

    auto mappedStream = ::CreateFileMappingA(stream, nullptr, PAGE_READONLY, 0, 0, nullptr);
    if (mappedStream == INVALID_HANDLE_VALUE) {
        ::CloseHandle(stream);
        throw Exception(std::string("could not map file ") + path);
    }
    void *base = position = static_cast<uint8_t*>(::MapViewOfFile(mappedStream, FILE_MAP_READ, 0, 0, length));
    void *end = position + length;

    if (base == nullptr) {
        ::CloseHandle(stream);
        ::CloseHandle(mappedStream);
        throw Exception("Execution of MapViewOfFile failed.");
    }
    new(this) FileInputStream(base, end, this->path.get(), stream, mappedStream);
#else
    FILE *stream = fopen(path.c_str(), "r");

    if (nullptr == stream)
        throw Exception(std::string("could not open file ") + path);

    struct stat fileStat;
    if (-1 == fstat(fileno(stream), &fileStat))
        throw Exception("Execution of function fstat failed.");

    const size_t length = fileStat.st_size;

    if (!length) {
        return;
    }

    void *base = position = (uint8_t *) mmap(nullptr, length, PROT_READ, MAP_SHARED, fileno(stream), 0);
    void *end = position + length;

    if (MAP_FAILED == base)
        throw Exception("Execution of function mmap failed.");

    if (-1 == posix_madvise(position, length, MADV_WILLNEED))
        throw Exception("Execution of function madvise failed.");

    // set begin and end
    new(this) FileInputStream(base, end, this->path.get(), stream);
#endif
}

FileInputStream::~FileInputStream() {
#ifdef _WIN32
    if (nullptr != file) {
        ::UnmapViewOfFile(base);
        ::CloseHandle(mapping);
        ::CloseHandle(file);
    }
#else
    if (nullptr != file) {
        fclose((FILE *) file);

        if (nullptr != base)
            munmap(base, (size_t) end - (size_t) base);
    }
#endif
}
