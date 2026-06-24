#ifndef CONFIG_H
#define CONFIG_H

#define BOARD_TYPE      "RP2040"
#define DISPLAY_DRIVER  "MAX7219"
#define FONT_TYPE       "FONT_5X8"

#define WIFI_SSID       "YourSSID"
#define WIFI_PASS       "YourPassword"

#define SERVER_HOST     "192.168.1.100"
#define SERVER_PORT     8080
#define ZONE_ID         "pico-01"

#define RECONNECT_INTERVAL_MS  5000

#define DISPLAY_WIDTH   8
#define NUM_DEVICES     1

#define FLASH_CRED_OFFSET  (256 * 1024)
#define FLASH_SECTOR_SIZE  4096

#endif
