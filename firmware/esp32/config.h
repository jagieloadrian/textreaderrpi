#ifndef CONFIG_H
#define CONFIG_H

#define BOARD_TYPE          "ESP32"
#define DISPLAY_DRIVER      "MAX7219"
#define FONT_TYPE           "DEFAULT"

#define WIFI_SSID           ""
#define WIFI_PASS           ""

#define SERVER_HOST         ""
#define SERVER_PORT         8080
#define ZONE_ID             "esp32"

#define RECONNECT_INTERVAL_MS  5000

#define NUM_DEVICES         4
#define MD_HARDWARE_SPI     1

#define MD_CS_PIN           5
#define MD_CLK_PIN          18
#define MD_MOSI_PIN         23

#endif
