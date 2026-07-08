#include "pico/stdlib.h"
#include "hardware/flash.h"
#include "ws_client.h"
#include "captive_portal.h"
#include "display.h"
#include "config.h"
#include <string.h>
#include <stdio.h>
#include <stdbool.h>

// Set by main()/captive_portal.c before MG_TCPIP_DRIVER_INIT(); read via
// MG_SET_WIFI_CONFIG in mongoose_config.h.
bool g_wifi_apmode = false;
char g_wifi_ssid[33];
char g_wifi_pass[65];

typedef struct {
    char ssid[33];
    char pass[65];
} stored_cred_t;

static bool load_creds(stored_cred_t *out) {
    const uint8_t *flash_ptr = (const uint8_t *)(XIP_BASE + FLASH_CRED_OFFSET);
    if (flash_ptr[0] == 0xFF) return false;
    memcpy(out->ssid, flash_ptr, 33);
    memcpy(out->pass, flash_ptr + 33, 65);
    out->ssid[32] = '\0';
    out->pass[64] = '\0';
    return out->ssid[0] != '\0';
}

int main(void) {
    stdio_init_all();

    stored_cred_t cred;
    bool has_creds = load_creds(&cred);

    if (!has_creds) {
        wifi_start();
        return 0;
    }

    strncpy(g_wifi_ssid, cred.ssid, sizeof(g_wifi_ssid) - 1);
    strncpy(g_wifi_pass, cred.pass, sizeof(g_wifi_pass) - 1);

    display_init();
    ws_client_start();

    return 0;
}
