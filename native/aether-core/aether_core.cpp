#include "aether_core.hpp"

namespace aether {

const std::string& version() {
    static const std::string kVersion = "0.1.0";
    return kVersion;
}

} // namespace aether
