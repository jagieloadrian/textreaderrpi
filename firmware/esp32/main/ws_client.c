#include "ws_client.h"
#include "json_parser.h"
#include "display.h"
#include "config.h"
#include "esp_websocket_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "ws";

static void ws_event_handler(void *arg, esp_event_base_t base, int32_t id, void *data) {
    esp_websocket_event_data_t *ev = (esp_websocket_event_data_t *)data;
    if (id == WEBSOCKET_EVENT_DATA && ev->op_code == 1) {
        parse_message(ev->data_ptr);
    } else if (id == WEBSOCKET_EVENT_DISCONNECTED) {
        display_text("Connecting...", "SCROLL", 80, 0, 0);
        ESP_LOGI(TAG, "disconnected");
    } else if (id == WEBSOCKET_EVENT_CONNECTED) {
        ESP_LOGI(TAG, "connected");
    }
}

void ws_client_start(void) {
    char uri[128];
    snprintf(uri, sizeof(uri), "ws://%s:%d/ws/zone/%s", SERVER_HOST, SERVER_PORT, ZONE_ID);
    esp_websocket_client_config_t cfg = {
        .uri = uri,
        .reconnect_timeout_ms = RECONNECT_INTERVAL_MS,
    };
    esp_websocket_client_handle_t client = esp_websocket_client_init(&cfg);
    esp_websocket_register_events(client, WEBSOCKET_EVENT_ANY, ws_event_handler, NULL);
    esp_websocket_client_start(client);
    while (1) vTaskDelay(pdMS_TO_TICKS(1000));
}
