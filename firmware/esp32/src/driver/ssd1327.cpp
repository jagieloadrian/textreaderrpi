#ifdef DISPLAY_DRIVER_SSD1327

#include "display.h"
#include <Adafruit_SSD1327.h>
#include <Wire.h>
#include <string.h>

static Adafruit_SSD1327 s_oled(128, 128, &Wire);

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render(const char *text) {
    s_oled.clearDisplay();
    s_oled.setTextSize(1);
    s_oled.setTextColor(0x0F);
    s_oled.setCursor(0, 0);
    s_oled.print(text);
    s_oled.display();
}

void display_init(void) {
    Wire.begin();
    s_oled.begin(0x3D);
    s_oled.clearDisplay();
    s_oled.display();
}

void display_clear(void) {
    s_oled.clearDisplay();
    s_oled.display();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    if (strcmp(effect, "SCROLL") == 0) {
        render(text); delay(sp * 4);
    } else if (strcmp(effect, "BLINK") == 0) {
        render(text);
        for (int i = 0; i < 6; i++) {
            delay(sp); s_oled.clearDisplay(); s_oled.display();
            delay(sp); render(text);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = strlen(text); char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i]; rev[cl] = '\0';
        render(rev);
    } else if (strcmp(effect, "FADE") == 0) {
        render(text);
        int sd = sp / 16 + 1;
        for (int v = 0; v <= 15; v++) { s_oled.setTextColor(v); render(text); delay(sd); }
        for (int v = 15; v >= 0; v--) { s_oled.setTextColor(v); render(text); delay(sd); }
        s_oled.setTextColor(0x0F); render(text);
    }
}

#endif
