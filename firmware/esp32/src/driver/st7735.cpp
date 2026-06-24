#ifdef DISPLAY_DRIVER_ST7735

#include "display.h"
#include <Adafruit_ST7735.h>
#include <SPI.h>
#include <string.h>

#define ST7735_CS   5
#define ST7735_DC   2
#define ST7735_RST  4

static Adafruit_ST7735 s_tft(ST7735_CS, ST7735_DC, ST7735_RST);

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render(const char *text, uint16_t fg, uint16_t bg) {
    s_tft.fillScreen(bg);
    s_tft.setTextSize(1);
    s_tft.setTextColor(fg);
    s_tft.setCursor(0, 0);
    s_tft.print(text);
}

void display_init(void) {
    s_tft.initR(INITR_BLACKTAB);
    s_tft.fillScreen(ST77XX_BLACK);
}

void display_clear(void) {
    s_tft.fillScreen(ST77XX_BLACK);
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    int text_len = strlen(text);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int x = 0; x <= text_len * 6; x++) {
            s_tft.fillScreen(ST77XX_BLACK);
            s_tft.setCursor(-x, 0);
            s_tft.setTextColor(ST77XX_WHITE);
            s_tft.print(text);
            delay(sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render(text, ST77XX_WHITE, ST77XX_BLACK);
        for (int i = 0; i < 6; i++) {
            delay(sp); s_tft.fillScreen(ST77XX_BLACK);
            delay(sp); render(text, ST77XX_WHITE, ST77XX_BLACK);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        char rev[128]; int cl = text_len < 127 ? text_len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i]; rev[cl] = '\0';
        for (int x = 0; x <= cl * 6; x++) {
            s_tft.fillScreen(ST77XX_BLACK);
            s_tft.setCursor(-x, 0);
            s_tft.setTextColor(ST77XX_WHITE);
            s_tft.print(rev);
            delay(sp);
        }
    } else if (strcmp(effect, "FADE") == 0) {
        render(text, ST77XX_WHITE, ST77XX_BLACK);
        int sd = sp / 32 + 1;
        for (int v = 0; v <= 31; v++) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render(text, c, ST77XX_BLACK); delay(sd);
        }
        for (int v = 31; v >= 0; v--) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render(text, c, ST77XX_BLACK); delay(sd);
        }
        render(text, ST77XX_WHITE, ST77XX_BLACK);
    }
}

#endif
