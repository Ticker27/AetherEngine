#include "aether_core.hpp"

#include <cstdlib>
#include <iostream>

int main() {
    if (aether::version() != "0.1.0") {
        std::cerr << "FAIL: unexpected version: " << aether::version() << std::endl;
        return 1;
    }
    std::cout << "aether-core-test PASSED" << std::endl;
    return 0;
}
