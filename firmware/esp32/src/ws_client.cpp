#include "ws_client.h"
#include "json_parser.h"
#include "driver/display.h"
#include "../../config.h"
#include <ArduinoWebsockets.h>
#include <Arduino.h>
#include <stdio.h>

using namespace websockets;

static WebsocketsClient wsClient;
static bool s_connected = false;

static void on_message(WebsocketsMessage msg) {
    parse_message(msg.data().c_str());
}

static void on_event(WebsocketsEvent event, String data) {
    if (event == WebsocketsEvent::ConnectionClosed ||
        event == WebsocketsEvent::GotPing ||
        event == WebsocketsEvent::GotPong) {
        if (event == WebsocketsEvent::ConnectionClosed) {
            s_connected = false;
        }
    }
}

void setup_ws(void) {
    char url[128];
    snprintf(url, sizeof(url), "ws://%s:%d/ws/zone/%s",
             SERVER_HOST, SERVER_PORT, ZONE_ID);

    wsClient.onMessage(on_message);
    wsClient.onEvent(on_event);

    s_connected = wsClient.connect(url);
}

void ws_loop(void) {
    if (!wsClient.available()) {
        s_connected = false;
        display_text("Connecting...", "BLINK", 500);
        delay(RECONNECT_INTERVAL_MS);
        setup_ws();
        return;
    }
    wsClient.poll();
}
