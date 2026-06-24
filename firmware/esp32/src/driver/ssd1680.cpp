#ifdef DISPLAY_DRIVER_SSD1680

#include "display.h"
#include <GxEPD2_BW.h>
#include <GxEPD2_213_B74.h>
#include <Fonts/FreeMonoBold9pt7b.h>
#include <SPI.h>
#include <string.h>

#define EPD_CS   5
#define EPD_DC   2
#define EPD_RST  4
#define EPD_BUSY 22

static GxEPD2_BW<GxEPD2_213_B74, GxEPD2_213_B74::HEIGHT> s_epd(GxEPD2_213_B74(EPD_CS, EPD_DC, EPD_RST, EPD_BUSY));

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void full_render(const char *text, uint16_t fg, uint16_t bg) {
    s_epd.setFullWindow();
    s_epd.setFont(&FreeMonoBold9pt7b);
    s_epd.setTextColor(fg);
    s_epd.firstPage();
    do {
        s_epd.fillScreen(bg);
        s_epd.setCursor(0, 18);
        s_epd.print(text);
    } while (s_epd.nextPage());
}

void display_init(void) {
    s_epd.init(115200);
    full_render("", GxEPD_BLACK, GxEPD_WHITE);
}

void display_clear(void) {
    full_render("", GxEPD_BLACK, GxEPD_WHITE);
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    int text_len = strlen(text);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int x = 0; x <= text_len * 6; x += 8) {
            s_epd.setFullWindow();
            s_epd.setFont(&FreeMonoBold9pt7b);
            s_epd.setTextColor(GxEPD_BLACK);
            s_epd.firstPage();
            do {
                s_epd.fillScreen(GxEPD_WHITE);
                s_epd.setCursor(-x, 18);
                s_epd.print(text);
            } while (s_epd.nextPage());
            delay(sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        full_render(text, GxEPD_BLACK, GxEPD_WHITE);
        delay(sp);
        full_render("", GxEPD_BLACK, GxEPD_WHITE);
        delay(sp);
        full_render(text, GxEPD_BLACK, GxEPD_WHITE);
    } else if (strcmp(effect, "REVERSE") == 0) {
        char rev[128]; int cl = text_len < 127 ? text_len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i]; rev[cl] = '\0';
        full_render(rev, GxEPD_BLACK, GxEPD_WHITE);
    } else if (strcmp(effect, "FADE") == 0) {
        full_render("", GxEPD_BLACK, GxEPD_WHITE);
        delay(sp);
        full_render(text, GxEPD_BLACK, GxEPD_WHITE);
    }
}

#endif
