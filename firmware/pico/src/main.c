#include "pico/stdlib.h"
#include "pico/cyw43_arch.h"
#include "hardware/flash.h"
#include "ws_client.h"
#include "captive_portal.h"
#include "driver/display.h"
#include "config.h"
#include <string.h>
#include <stdio.h>

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

    if (cyw43_arch_init()) return 1;

    stored_cred_t cred;
    bool has_creds = load_creds(&cred);

    if (!has_creds) {
        captive_portal_start();
        return 0;
    }

    cyw43_arch_enable_sta_mode();
    if (cyw43_arch_wifi_connect_timeout_ms(cred.ssid, cred.pass,
                                           CYW43_AUTH_WPA2_AES_PSK, 10000) != 0) {
        cyw43_arch_wifi_connect_timeout_ms(WIFI_SSID, WIFI_PASS,
                                           CYW43_AUTH_WPA2_AES_PSK, 10000);
    }

    display_init();
    ws_task();

    cyw43_arch_deinit();
    return 0;
}
