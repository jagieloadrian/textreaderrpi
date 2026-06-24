#ifdef DISPLAY_DRIVER_PCD8544

#include "display.h"
#include <Adafruit_PCD8544.h>
#include <SPI.h>
#include <string.h>

#define PCD8544_CLK  18
#define PCD8544_DIN  23
#define PCD8544_DC   2
#define PCD8544_CS   5
#define PCD8544_RST  4

static Adafruit_PCD8544 s_display(PCD8544_CLK, PCD8544_DIN, PCD8544_DC, PCD8544_CS, PCD8544_RST);

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render(const char *text) {
    s_display.clearDisplay();
    s_display.setTextSize(1);
    s_display.setTextColor(BLACK);
    s_display.setCursor(0, 0);
    s_display.print(text);
    s_display.display();
}

void display_init(void) {
    s_display.begin();
    s_display.setContrast(50);
    s_display.clearDisplay();
    s_display.display();
}

void display_clear(void) {
    s_display.clearDisplay();
    s_display.display();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    int text_len = strlen(text);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int x = 0; x <= text_len * 6; x++) {
            s_display.clearDisplay();
            s_display.setCursor(-x, 0);
            s_display.setTextColor(BLACK);
            s_display.print(text);
            s_display.display();
            delay(sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render(text);
        for (int i = 0; i < 6; i++) {
            delay(sp); s_display.clearDisplay(); s_display.display();
            delay(sp); render(text);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        char rev[128]; int cl = text_len < 127 ? text_len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i]; rev[cl] = '\0';
        render(rev);
    } else if (strcmp(effect, "FADE") == 0) {
        render(text);
        int sd = sp / 64 + 1;
        for (int v = 0; v <= 63; v += 2) { s_display.setContrast(v); delay(sd); }
        for (int v = 63; v >= 0; v -= 2) { s_display.setContrast(v); delay(sd); }
        s_display.setContrast(50);
    }
}

#endif
