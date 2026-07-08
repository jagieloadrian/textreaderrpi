#pragma once

#include <stdbool.h>

#define MG_ARCH                 MG_ARCH_PICOSDK
#define MG_ENABLE_TCPIP         1
#define MG_ENABLE_DRIVER_PICO_W 1

// Runtime WiFi config, filled by the caller before MG_TCPIP_DRIVER_INIT()
// runs (see main.c / captive_portal.c). ponytail: globals instead of a
// config-passing API, since there's exactly one driver-init call site per mode.
extern bool g_wifi_apmode;
extern char g_wifi_ssid[33];
extern char g_wifi_pass[65];

#define MG_SET_WIFI_CONFIG(data)                     \
  do {                                                \
    (data)->wifi.apmode = g_wifi_apmode;              \
    if (g_wifi_apmode) {                              \
      (data)->wifi.apssid = g_wifi_ssid;               \
      (data)->wifi.apip = MG_IPV4(192, 168, 4, 1);      \
      (data)->wifi.apmask = MG_IPV4(255, 255, 255, 0);  \
    } else {                                           \
      (data)->wifi.ssid = g_wifi_ssid;                  \
      (data)->wifi.pass = g_wifi_pass;                  \
    }                                                   \
  } while (0)
