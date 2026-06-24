#include "captive_portal.h"
#include "config.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "nvs.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"
#include "lwip/sockets.h"
#include "lwip/netdb.h"
#include <string.h>
#include <stdlib.h>

#define NVS_NAMESPACE    "wifi_creds"
#define TAG              "portal"

static EventGroupHandle_t s_wifi_event_group;
#define WIFI_CONNECTED_BIT  BIT0

static bool load_credentials(char *ssid, size_t ssid_max, char *pass, size_t pass_max) {
    nvs_handle_t h;
    if (nvs_open(NVS_NAMESPACE, NVS_READONLY, &h) != ESP_OK) return false;
    bool ok = (nvs_get_str(h, "ssid", ssid, &ssid_max) == ESP_OK &&
               nvs_get_str(h, "pass", pass, &pass_max) == ESP_OK &&
               ssid[0] != '\0');
    nvs_close(h);
    return ok;
}

static void save_credentials(const char *ssid, const char *pass) {
    nvs_handle_t h;
    if (nvs_open(NVS_NAMESPACE, NVS_READWRITE, &h) != ESP_OK) return;
    nvs_set_str(h, "ssid", ssid);
    nvs_set_str(h, "pass", pass);
    nvs_commit(h);
    nvs_close(h);
}

static void wifi_event_handler(void *arg, esp_event_base_t base, int32_t id, void *data) {
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        esp_wifi_connect();
    } else if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        xEventGroupSetBits(s_wifi_event_group, WIFI_CONNECTED_BIT);
    }
}

static bool connect_sta(const char *ssid, const char *pass) {
    s_wifi_event_group = xEventGroupCreate();
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    esp_wifi_init(&cfg);

    esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event_handler, NULL);
    esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL);

    wifi_config_t wifi_cfg = {0};
    strlcpy((char *)wifi_cfg.sta.ssid, ssid, sizeof(wifi_cfg.sta.ssid));
    strlcpy((char *)wifi_cfg.sta.password, pass, sizeof(wifi_cfg.sta.password));

    esp_wifi_set_mode(WIFI_MODE_STA);
    esp_wifi_set_config(WIFI_IF_STA, &wifi_cfg);
    esp_wifi_start();
    esp_wifi_connect();

    EventBits_t bits = xEventGroupWaitBits(s_wifi_event_group, WIFI_CONNECTED_BIT,
                                           pdFALSE, pdFALSE, pdMS_TO_TICKS(15000));
    return (bits & WIFI_CONNECTED_BIT) != 0;
}

static void dns_task(void *arg) {
    int sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    struct sockaddr_in addr = {
        .sin_family      = AF_INET,
        .sin_port        = htons(53),
        .sin_addr.s_addr = INADDR_ANY,
    };
    bind(sock, (struct sockaddr *)&addr, sizeof(addr));

    uint8_t buf[512];
    struct sockaddr_in client;
    socklen_t client_len = sizeof(client);

    while (1) {
        int len = recvfrom(sock, buf, sizeof(buf) - 20, 0, (struct sockaddr *)&client, &client_len);
        if (len < 12) continue;

        uint8_t resp[512];
        memcpy(resp, buf, len);
        resp[2] = 0x81;
        resp[3] = 0x80;
        resp[7] = 1;

        int rlen = len;
        resp[rlen++] = 0xC0; resp[rlen++] = 0x0C;
        resp[rlen++] = 0x00; resp[rlen++] = 0x01;
        resp[rlen++] = 0x00; resp[rlen++] = 0x01;
        resp[rlen++] = 0x00; resp[rlen++] = 0x00;
        resp[rlen++] = 0x00; resp[rlen++] = 0x3C;
        resp[rlen++] = 0x00; resp[rlen++] = 0x04;
        resp[rlen++] = 192;  resp[rlen++] = 168;
        resp[rlen++] = 4;    resp[rlen++] = 1;

        sendto(sock, resp, rlen, 0, (struct sockaddr *)&client, client_len);
    }
}

static const char PORTAL_HTML[] =
    "<!DOCTYPE html><html><head><meta charset=utf-8>"
    "<meta name=viewport content='width=device-width,initial-scale=1'>"
    "<title>TextReader Setup</title></head><body>"
    "<h2>TextReader WiFi Setup</h2>"
    "<form method=POST action=/save>"
    "<p><label>Network name (SSID):<br>"
    "<input name=ssid type=text size=32 required></label></p>"
    "<p><label>Password:<br>"
    "<input name=pass type=password size=32></label></p>"
    "<p><input type=submit value='Connect to WiFi'></p>"
    "</form></body></html>";

static void url_decode(const char *src, char *dst, size_t dst_max) {
    size_t i = 0;
    while (*src && i < dst_max - 1) {
        if (*src == '%' && src[1] && src[2]) {
            char hex[3] = {src[1], src[2], '\0'};
            dst[i++] = (char)strtol(hex, NULL, 16);
            src += 3;
        } else if (*src == '+') {
            dst[i++] = ' ';
            src++;
        } else {
            dst[i++] = *src++;
        }
    }
    dst[i] = '\0';
}

static esp_err_t portal_get_handler(httpd_req_t *req) {
    httpd_resp_set_type(req, "text/html");
    httpd_resp_send(req, PORTAL_HTML, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
}

static esp_err_t portal_post_handler(httpd_req_t *req) {
    char body[256] = {0};
    int ret = httpd_req_recv(req, body, sizeof(body) - 1);
    if (ret <= 0) return ESP_FAIL;
    body[ret] = '\0';

    char ssid_raw[64] = {0}, pass_raw[64] = {0};
    char ssid[64]     = {0}, pass[64]     = {0};

    char *p = strstr(body, "ssid=");
    if (p) {
        p += 5;
        char *end = strchr(p, '&');
        int len = end ? (int)(end - p) : (int)strlen(p);
        if (len > 63) len = 63;
        memcpy(ssid_raw, p, len);
    }
    p = strstr(body, "pass=");
    if (p) {
        p += 5;
        char *end = strchr(p, '&');
        int len = end ? (int)(end - p) : (int)strlen(p);
        if (len > 63) len = 63;
        memcpy(pass_raw, p, len);
    }

    url_decode(ssid_raw, ssid, sizeof(ssid));
    url_decode(pass_raw, pass, sizeof(pass));

    if (ssid[0] == '\0') {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "SSID required");
        return ESP_FAIL;
    }

    save_credentials(ssid, pass);
    httpd_resp_set_type(req, "text/html");
    httpd_resp_send(req,
        "<html><body><h3>Credentials saved. Rebooting...</h3></body></html>",
        HTTPD_RESP_USE_STRLEN);
    vTaskDelay(pdMS_TO_TICKS(500));
    esp_restart();
    return ESP_OK;
}

static void start_portal(void) {
    esp_netif_create_default_wifi_ap();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    esp_wifi_init(&cfg);

    wifi_config_t ap_cfg = {
        .ap = {
            .ssid           = "TextReader-Setup",
            .ssid_len       = 0,
            .channel        = 1,
            .authmode       = WIFI_AUTH_OPEN,
            .max_connection = 4,
        },
    };
    esp_wifi_set_mode(WIFI_MODE_AP);
    esp_wifi_set_config(WIFI_IF_AP, &ap_cfg);
    esp_wifi_start();
    ESP_LOGI(TAG, "AP started: TextReader-Setup");

    xTaskCreate(dns_task, "dns", 4096, NULL, 5, NULL);

    httpd_handle_t server = NULL;
    httpd_config_t http_cfg = HTTPD_DEFAULT_CONFIG();
    http_cfg.uri_match_fn = httpd_uri_match_wildcard;
    httpd_start(&server, &http_cfg);

    static const httpd_uri_t get_uri = {
        .uri = "/*", .method = HTTP_GET, .handler = portal_get_handler
    };
    static const httpd_uri_t post_uri = {
        .uri = "/save", .method = HTTP_POST, .handler = portal_post_handler
    };
    httpd_register_uri_handler(server, &get_uri);
    httpd_register_uri_handler(server, &post_uri);

    while (1) vTaskDelay(pdMS_TO_TICKS(1000));
}

void wifi_start(void) {
    char ssid[64] = {0}, pass[64] = {0};
    if (load_credentials(ssid, sizeof(ssid), pass, sizeof(pass))) {
        if (connect_sta(ssid, pass)) {
            ESP_LOGI(TAG, "Connected to %s", ssid);
            return;
        }
        ESP_LOGW(TAG, "STA connect failed, starting provisioning portal");
    }
    start_portal();
}
