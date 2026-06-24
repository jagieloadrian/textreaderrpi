#ifdef DISPLAY_DRIVER_SH1106

#include "display.h"
#include <U8g2lib.h>
#include <SPI.h>
#include <string.h>

#define SH1106_CS   17
#define SH1106_DC   20
#define SH1106_RST  21

static U8G2_SH1106_128X64_NONAME_F_4W_HW_SPI s_u8g2(U8G2_R0, SH1106_CS, SH1106_DC, SH1106_RST);

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render(const char *text) {
    s_u8g2.clearBuffer();
    s_u8g2.setFont(u8g2_font_5x8_tf);
    s_u8g2.drawStr(0, 8, text);
    s_u8g2.sendBuffer();
}

void display_init(void) {
    s_u8g2.begin();
    s_u8g2.clearDisplay();
}

void display_clear(void) {
    s_u8g2.clearDisplay();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    if (strcmp(effect, "SCROLL") == 0) {
        render(text); delay(sp * 4);
    } else if (strcmp(effect, "BLINK") == 0) {
        render(text);
        for (int i = 0; i < 6; i++) {
            delay(sp); s_u8g2.setContrast(0);
            delay(sp); s_u8g2.setContrast(255);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = strlen(text); char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i]; rev[cl] = '\0';
        render(rev);
    } else if (strcmp(effect, "FADE") == 0) {
        render(text);
        int sd = sp / 16 + 1;
        for (int v = 0; v <= 255; v += 16) { s_u8g2.setContrast(v); delay(sd); }
        for (int v = 255; v >= 0; v -= 16) { s_u8g2.setContrast(v); delay(sd); }
        s_u8g2.setContrast(200);
    }
}

#endif
