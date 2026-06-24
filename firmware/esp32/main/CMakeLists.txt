idf_component_register(
    SRCS "main.c" "ws_client.c" "json_parser.c" "captive_portal.c" "ota.c"
         "driver/max7219.c"
    INCLUDE_DIRS "."
    REQUIRES
        esp_wifi nvs_flash esp_event esp_netif lwip
        esp_http_server esp_websocket_client
        driver esp_timer esp_ota
)
