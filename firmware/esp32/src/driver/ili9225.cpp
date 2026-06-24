#ifdef DISPLAY_DRIVER_ILI9225

#include "display.h"
#include <TFT_22_ILI9225.h>
#include <SPI.h>
#include <string.h>

#define ILI9225_RST  4
#define ILI9225_RS   2
#define ILI9225_CS   5
#define ILI9225_MOSI 23
#define ILI9225_CLK  18

static TFT_22_ILI9225 s_tft(ILI9225_RST, ILI9225_RS, ILI9225_CS, ILI9225_MOSI, ILI9225_CLK);

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render(const char *text, uint16_t fg, uint16_t bg) {
    s_tft.fillRectangle(0, 0, 175, 15, bg);
    s_tft.setTextSize(1);
    s_tft.drawText(0, 0, text, fg);
}

void display_init(void) {
    s_tft.begin();
    s_tft.setOrientation(0);
    s_tft.fillScreen(COLOR_BLACK);
}

void display_clear(void) {
    s_tft.fillScreen(COLOR_BLACK);
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    int text_len = strlen(text);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int x = 0; x <= text_len * 6; x++) {
            s_tft.fillRectangle(0, 0, 175, 10, COLOR_BLACK);
            s_tft.drawText(-x, 0, text, COLOR_WHITE);
            delay(sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render(text, COLOR_WHITE, COLOR_BLACK);
        for (int i = 0; i < 6; i++) {
            delay(sp); s_tft.fillRectangle(0, 0, 175, 15, COLOR_BLACK);
            delay(sp); render(text, COLOR_WHITE, COLOR_BLACK);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        char rev[128]; int cl = text_len < 127 ? text_len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i]; rev[cl] = '\0';
        render(rev, COLOR_WHITE, COLOR_BLACK);
    } else if (strcmp(effect, "FADE") == 0) {
        render(text, COLOR_WHITE, COLOR_BLACK);
        int sd = sp / 32 + 1;
        for (int v = 0; v <= 31; v++) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render(text, c, COLOR_BLACK); delay(sd);
        }
        for (int v = 31; v >= 0; v--) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render(text, c, COLOR_BLACK); delay(sd);
        }
        render(text, COLOR_WHITE, COLOR_BLACK);
    }
}

#endif
