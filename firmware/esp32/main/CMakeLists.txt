if(NOT DEFINED DISPLAY_DRIVER)
    set(DISPLAY_DRIVER "MAX7219")
endif()

if(DISPLAY_DRIVER STREQUAL "SSD1306")
    set(DRIVER_SRC "driver/ssd1306.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_SSD1306=1")
elseif(DISPLAY_DRIVER STREQUAL "SSD1309")
    set(DRIVER_SRC "driver/ssd1309.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_SSD1309=1")
elseif(DISPLAY_DRIVER STREQUAL "SSD1327")
    set(DRIVER_SRC "driver/ssd1327.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_SSD1327=1")
elseif(DISPLAY_DRIVER STREQUAL "SH1106")
    set(DRIVER_SRC "driver/sh1106.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_SH1106=1")
elseif(DISPLAY_DRIVER STREQUAL "HT16K33")
    set(DRIVER_SRC "driver/ht16k33.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_HT16K33=1")
elseif(DISPLAY_DRIVER STREQUAL "ST7735")
    set(DRIVER_SRC "driver/st7735.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_ST7735=1")
elseif(DISPLAY_DRIVER STREQUAL "ST7789")
    set(DRIVER_SRC "driver/st7789.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_ST7789=1")
elseif(DISPLAY_DRIVER STREQUAL "ILI9225")
    set(DRIVER_SRC "driver/ili9225.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_ILI9225=1")
elseif(DISPLAY_DRIVER STREQUAL "PCD8544")
    set(DRIVER_SRC "driver/pcd8544.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_PCD8544=1")
elseif(DISPLAY_DRIVER STREQUAL "SSD1680")
    set(DRIVER_SRC "driver/ssd1680.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_SSD1680=1")
else()
    set(DRIVER_SRC "driver/max7219.c")
    set(DRIVER_DEF "DISPLAY_DRIVER_MAX7219=1")
endif()

idf_component_register(
    SRCS "main.c" "ws_client.c" "json_parser.c" "captive_portal.c" "ota.c"
         ${DRIVER_SRC}
    INCLUDE_DIRS "." "../../common"
    REQUIRES
        esp_wifi nvs_flash esp_event esp_netif lwip
        esp_http_server esp_websocket_client
        driver esp_timer app_update json
)

target_compile_definitions(${COMPONENT_LIB} PRIVATE ${DRIVER_DEF})
