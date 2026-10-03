#pragma once

#include <string>
#include <functional>

namespace aether {
namespace bridge {

struct MessageRequest {
    std::string requestId;
    std::string method;
    std::string payloadJson;
};

struct MessageResponse {
    std::string requestId;
    bool ok;
    std::string dataJson;
    std::string error;

    std::string ToJson() const;
};

using MessageHandler = std::function<MessageResponse(const MessageRequest&)>;

/**
 * MessageBridge — protocol layer for Dart <-> Native communication.
 *
 * Protocol:
 * Request: { "requestId": "abc-001", "method": "runtime.status", "payload": {} }
 * Response: { "requestId": "abc-001", "ok": true, "data": { "state": "running" } }
 *
 * This layer is transport-agnostic — MethodChannel is handled in Kotlin.
 */
class MessageBridge {
public:
    static MessageBridge& Instance();

    MessageBridge(const MessageBridge&) = delete;
    MessageBridge& operator=(const MessageBridge&) = delete;

    MessageResponse Handle(const MessageRequest& request);
    void RegisterHandler(const std::string& method, MessageHandler handler);

private:
    MessageBridge();
    ~MessageBridge() = default;
};

} // namespace bridge
} // namespace aether
