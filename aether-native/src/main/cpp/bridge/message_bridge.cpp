#include "message_bridge.h"
#include "../common/logging.h"
#include "../runtime/runtime_state.h"
#include "engine_bridge.h"
#include <unordered_map>
#include <mutex>
#include <sstream>

namespace aether {
namespace bridge {

namespace {
std::unordered_map<std::string, MessageHandler> g_handlers;
std::mutex g_mutex;

std::string EscapeJson(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c; break;
        }
    }
    return out;
}
} // namespace

std::string MessageResponse::ToJson() const {
    std::ostringstream oss;
    oss << "{";
    oss << "\"requestId\":\"" << EscapeJson(requestId) << "\",";
    oss << "\"ok\":" << (ok ? "true" : "false") << ",";
    if (ok) {
        oss << "\"data\":" << (dataJson.empty() ? "{}" : dataJson);
    } else {
        oss << "\"error\":\"" << EscapeJson(error) << "\"";
    }
    oss << "}";
    return oss.str();
}

MessageBridge::MessageBridge() {
    // Register built-in handlers
    RegisterHandler("runtime.status", [](const MessageRequest& req) -> MessageResponse {
        auto state = runtime::RuntimeStateManager::Instance().GetStateString();
        std::ostringstream data;
        data << "{\"state\":\"" << state << "\"}";
        return MessageResponse{req.requestId, true, data.str(), ""};
    });

    RegisterHandler("runtime.version", [](const MessageRequest& req) -> MessageResponse {
        auto version = EngineBridge::Instance().GetVersion();
        std::ostringstream data;
        data << "{\"version\":\"" << EscapeJson(version) << "\"}";
        return MessageResponse{req.requestId, true, data.str(), ""};
    });

    RegisterHandler("runtime.ping", [](const MessageRequest& req) -> MessageResponse {
        return MessageResponse{req.requestId, true, "{\"pong\":true}", ""};
    });
}

MessageBridge& MessageBridge::Instance() {
    static MessageBridge instance;
    return instance;
}

void MessageBridge::RegisterHandler(const std::string& method, MessageHandler handler) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_handlers[method] = std::move(handler);
    AETHER_LOGI("Message handler registered");
}

MessageResponse MessageBridge::Handle(const MessageRequest& request) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_handlers.find(request.method);
    if (it == g_handlers.end()) {
        AETHER_LOGW("No handler for method");
        return MessageResponse{request.requestId, false, "", "method not found: " + request.method};
    }
    try {
        return it->second(request);
    } catch (const std::exception& e) {
        AETHER_LOGE("Handler threw");
        return MessageResponse{request.requestId, false, "", e.what()};
    }
}

} // namespace bridge
} // namespace aether
