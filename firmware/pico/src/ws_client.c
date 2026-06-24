#include "ws_client.h"
#include "json_parser.h"
#include "display.h"
#include "config.h"
#include "mongoose.h"
#include "pico/stdlib.h"
#include "pico/cyw43_arch.h"
#include <string.h>
#include <stdio.h>

static bool s_connected = false;

static void ws_handler(struct mg_connection *c, int ev, void *ev_data) {
    if (ev == MG_EV_WS_OPEN) {
        s_connected = true;
    } else if (ev == MG_EV_WS_MSG) {
        struct mg_ws_message *wm = (struct mg_ws_message *)ev_data;
        char buf[512];
        int len = (int)wm->data.len;
        if (len >= (int)sizeof(buf)) len = (int)sizeof(buf) - 1;
        memcpy(buf, wm->data.buf, (size_t)len);
        buf[len] = '\0';
        parse_message(buf);
    } else if (ev == MG_EV_CLOSE) {
        s_connected = false;
    }
    (void)c;
}

void ws_client_start(void) {
    struct mg_mgr mgr;
    mg_mgr_init(&mgr);

    for (;;) {
        if (!s_connected) {
            display_text("Connecting...", "BLINK", 500, 500, 8);
            char url[128];
            snprintf(url, sizeof(url), "ws://%s:%d/ws/zone/%s",
                     SERVER_HOST, SERVER_PORT, ZONE_ID);
            mg_ws_connect(&mgr, url, ws_handler, NULL, NULL);
            sleep_ms(RECONNECT_INTERVAL_MS);
        }
        mg_mgr_poll(&mgr, 10);
        cyw43_arch_poll();
        sleep_ms(1);
    }
}
