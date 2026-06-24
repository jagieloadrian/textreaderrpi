#ifdef DISPLAY_DRIVER_SSD1306

#include "display.h"
#include <Adafruit_SSD1306.h>
#include <Wire.h>
#include <string.h>

static Adafruit_SSD1306 s_oled(128, 64, &Wire);

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render(const char *text) {
    s_oled.clearDisplay();
    s_oled.setTextSize(1);
    s_oled.setTextColor(SSD1306_WHITE);
    s_oled.setCursor(0, 0);
    s_oled.print(text);
    s_oled.display();
}

void display_init(void) {
    Wire.begin();
    s_oled.begin(SSD1306_SWITCHCAPVCC, 0x3C);
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
        render(text);
        s_oled.startscrollleft(0x00, 0x0F);
        delay(sp * 8);
        s_oled.stopscroll();
    } else if (strcmp(effect, "BLINK") == 0) {
        render(text);
        for (int i = 0; i < 6; i++) {
            delay(sp); s_oled.invertDisplay(true);
            delay(sp); s_oled.invertDisplay(false);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = strlen(text);
        char rev[128];
        int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        render(rev);
        s_oled.startscrollright(0x00, 0x0F);
        delay(sp * 8);
        s_oled.stopscroll();
    } else if (strcmp(effect, "FADE") == 0) {
        render(text);
        int step_delay = sp / 32 + 1;
        for (int v = 0; v <= 255; v += 8) {
            s_oled.ssd1306_command(SSD1306_SETCONTRAST);
            s_oled.ssd1306_command(v);
            delay(step_delay);
        }
        for (int v = 255; v >= 0; v -= 8) {
            s_oled.ssd1306_command(SSD1306_SETCONTRAST);
            s_oled.ssd1306_command(v);
            delay(step_delay);
        }
        s_oled.ssd1306_command(SSD1306_SETCONTRAST);
        s_oled.ssd1306_command(0xCF);
    }
}

#endif
