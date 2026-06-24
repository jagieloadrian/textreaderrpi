#include "captive_portal.h"
#include "config.h"
#include "mongoose.h"
#include "pico/stdlib.h"
#include "pico/cyw43_arch.h"
#include "hardware/flash.h"
#include "hardware/sync.h"
#include <string.h>
#include <stdio.h>

static const char *FORM_HTML =
    "<!DOCTYPE html>"
    "<html lang=\"en\">"
    "<head>"
    "<meta charset=\"UTF-8\">"
    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
    "<title>TextReader Setup</title>"
    "<style>"
    "body{margin:0;padding:16px;background:#fffbfe;font-family:system-ui,-apple-system,sans-serif;"
    "color:#1c1b1f;font-size:16px;line-height:1.5}"
    ".card{background:#f3edf7;border:1px solid #79747e;border-radius:12px;padding:24px;"
    "max-width:400px;margin:24px auto}"
    "h1{font-size:1.25rem;font-weight:600;line-height:1.2;margin:0 0 16px}"
    "label{display:block;font-size:0.875rem;font-weight:400;margin-bottom:4px;color:#1c1b1f}"
    "input{display:block;width:100%;box-sizing:border-box;padding:8px 12px;"
    "border:1px solid #79747e;border-radius:8px;font-size:1rem;background:#fffbfe;"
    "color:#1c1b1f;min-height:44px}"
    "input:focus{outline:2px solid #6750a4;outline-offset:1px;border-color:#6750a4}"
    ".field{margin-bottom:16px}"
    "button{display:block;width:100%;padding:12px 16px;background:#6750a4;color:#fff;"
    "border:none;border-radius:8px;font-size:1rem;font-weight:600;cursor:pointer;"
    "min-height:44px}"
    "button:active{opacity:0.85}"
    "</style>"
    "</head>"
    "<body>"
    "<div class=\"card\">"
    "<h1>TextReader Setup</h1>"
    "<form method=\"POST\" action=\"/save\">"
    "<div class=\"field\">"
    "<label for=\"ssid\">WiFi Network Name</label>"
    "<input type=\"text\" id=\"ssid\" name=\"ssid\" required maxlength=\"32\""
    " autocomplete=\"off\" autocorrect=\"off\" autocapitalize=\"none\" spellcheck=\"false\">"
    "</div>"
    "<div class=\"field\">"
    "<label for=\"pass\">Password</label>"
    "<input type=\"password\" id=\"pass\" name=\"pass\" required maxlength=\"64\""
    " autocomplete=\"current-password\">"
    "</div>"
    "<button type=\"submit\">Connect to WiFi</button>"
    "</form>"
    "</div>"
    "</body>"
    "</html>";

static const char *SUCCESS_HTML =
    "<!DOCTYPE html>"
    "<html lang=\"en\">"
    "<head>"
    "<meta charset=\"UTF-8\">"
    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
    "<title>TextReader Setup</title>"
    "<style>"
    "body{margin:0;padding:16px;background:#fffbfe;font-family:system-ui,-apple-system,sans-serif;"
    "color:#1c1b1f;font-size:16px;line-height:1.5}"
    ".card{background:#eaddff;border:1px solid #6750a4;border-radius:12px;padding:24px;"
    "max-width:400px;margin:24px auto}"
    "h1{font-size:1.25rem;font-weight:600;line-height:1.2;color:#21005d;margin:0 0 8px}"
    "p{color:#49454f;margin:0}"
    "</style>"
    "</head>"
    "<body>"
    "<div class=\"card\">"
    "<h1>Credentials saved.</h1>"
    "<p>TextReader is rebooting\xe2\x80\xa6</p>"
    "<p>You can close this page.</p>"
    "</div>"
    "</body>"
    "</html>";

typedef struct {
    char ssid[33];
    char pass[65];
} cred_t;

static volatile bool s_save_done = false;
static cred_t s_cred;

static void portal_handler(struct mg_connection *c, int ev, void *ev_data) {
    if (ev == MG_EV_HTTP_MSG) {
        struct mg_http_message *hm = (struct mg_http_message *)ev_data;
        if (mg_match(hm->uri, mg_str("/save"), NULL) &&
            mg_match(hm->method, mg_str("POST"), NULL)) {
            char ssid_buf[33] = {0};
            char pass_buf[65] = {0};
            mg_http_get_var(&hm->body, "ssid", ssid_buf, sizeof(ssid_buf));
            mg_http_get_var(&hm->body, "pass", pass_buf, sizeof(pass_buf));
            strncpy(s_cred.ssid, ssid_buf, sizeof(s_cred.ssid) - 1);
            strncpy(s_cred.pass, pass_buf, sizeof(s_cred.pass) - 1);
            mg_http_reply(c, 200, "Content-Type: text/html\r\n", "%s", SUCCESS_HTML);
            s_save_done = true;
        } else {
            mg_http_reply(c, 200, "Content-Type: text/html\r\n", "%s", FORM_HTML);
        }
    }
    (void)ev_data;
}

static void write_creds_to_flash(const cred_t *cred) {
    uint8_t buf[FLASH_SECTOR_SIZE];
    memset(buf, 0xFF, sizeof(buf));
    memcpy(buf, cred, sizeof(cred_t));
    uint32_t ints = save_and_disable_interrupts();
    flash_range_erase(FLASH_CRED_OFFSET, FLASH_SECTOR_SIZE);
    flash_range_program(FLASH_CRED_OFFSET, buf, FLASH_SECTOR_SIZE);
    restore_interrupts(ints);
}

void wifi_start(void) {
    cyw43_arch_enable_ap_mode("TextReader-Setup", NULL, CYW43_AUTH_OPEN);

    struct mg_mgr mgr;
    mg_mgr_init(&mgr);
    mg_http_listen(&mgr, "http://0.0.0.0:80", portal_handler, NULL);

    s_save_done = false;
    while (!s_save_done) {
        mg_mgr_poll(&mgr, 10);
        cyw43_arch_poll();
        sleep_ms(1);
    }

    mg_mgr_free(&mgr);
    write_creds_to_flash(&s_cred);
    sleep_ms(500);
    (*((volatile uint32_t *)(PPB_BASE + 0x0ED0C)) = 0x05FA0004);
}
