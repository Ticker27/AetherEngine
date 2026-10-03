#pragma once

#include <string>
#include <utility>

namespace aether {
namespace common {

template <typename T>
class Result {
public:
    static Result<T> Ok(T value) {
        return Result<T>(true, std::move(value), "");
    }

    static Result<T> Err(std::string error) {
        return Result<T>(false, T{}, std::move(error));
    }

    bool IsOk() const { return ok_; }
    bool IsErr() const { return !ok_; }

    const T& Value() const { return value_; }
    T& Value() { return value_; }

    const std::string& Error() const { return error_; }

    explicit operator bool() const { return ok_; }

private:
    Result(bool ok, T value, std::string error)
        : ok_(ok), value_(std::move(value)), error_(std::move(error)) {}

    bool ok_;
    T value_;
    std::string error_;
};

template <>
class Result<void> {
public:
    static Result<void> Ok() { return Result<void>(true, ""); }
    static Result<void> Err(std::string error) { return Result<void>(false, std::move(error)); }

    bool IsOk() const { return ok_; }
    bool IsErr() const { return !ok_; }
    const std::string& Error() const { return error_; }
    explicit operator bool() const { return ok_; }

private:
    Result(bool ok, std::string error) : ok_(ok), error_(std::move(error)) {}
    bool ok_;
    std::string error_;
};

} // namespace common
} // namespace aether
