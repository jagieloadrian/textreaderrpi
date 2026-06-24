#include "max7219.h"
#include "../../config.h"
#include <MD_Parola.h>
#include <MD_MAX72XX.h>
#include <SPI.h>
#include <string.h>
#include <algorithm>

static MD_Parola s_parola(MD_MAX72XX::ICSTATION_HW, MD_CS_PIN, NUM_DEVICES);

static int clamp_speed(int speed_ms) {
    if (speed_ms < 1) return 1;
    if (speed_ms > 5000) return 5000;
    return speed_ms;
}

void max7219_init(void) {
    SPI.begin(MD_CLK_PIN, -1, MD_MOSI_PIN, MD_CS_PIN);
    s_parola.begin();
    s_parola.setIntensity(4);
    s_parola.displayClear();
}

void max7219_clear(void) {
    s_parola.displayClear();
}

void max7219_display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);

    if (strcmp(effect, "BLINK") == 0) {
        s_parola.setSpeed(sp);
        s_parola.displayText(text, PA_CENTER, sp, sp, PA_PRINT, PA_NO_EFFECT);
        unsigned long t = millis();
        bool on = true;
        while (millis() - t < (unsigned long)(sp * 6)) {
            if (millis() - t > (unsigned long)(sp * (on ? 1 : 1))) {
                if (on) {
                    s_parola.displayClear();
                } else {
                    s_parola.displayText(text, PA_CENTER, sp, 0, PA_PRINT, PA_NO_EFFECT);
                    while (!s_parola.displayAnimate()) {}
                }
                on = !on;
                t = millis();
            }
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = strlen(text);
        char rev[128];
        int copy_len = len < 127 ? len : 127;
        for (int i = 0; i < copy_len; i++) {
            rev[i] = text[copy_len - 1 - i];
        }
        rev[copy_len] = '\0';
        s_parola.setSpeed(sp);
        s_parola.displayText(rev, PA_LEFT, sp, sp, PA_SCROLL_LEFT, PA_SCROLL_LEFT);
        while (!s_parola.displayAnimate()) { delay(1); }
    } else if (strcmp(effect, "FADE") == 0) {
        s_parola.displayText(text, PA_CENTER, sp, 0, PA_PRINT, PA_NO_EFFECT);
        while (!s_parola.displayAnimate()) {}
        MD_MAX72XX *mx = s_parola.getGraphicObject();
        for (int i = 0; i <= 15; i++) {
            mx->control(MD_MAX72XX::INTENSITY, i);
            delay(sp / 16 + 1);
        }
        for (int i = 15; i >= 0; i--) {
            mx->control(MD_MAX72XX::INTENSITY, i);
            delay(sp / 16 + 1);
        }
        mx->control(MD_MAX72XX::INTENSITY, 4);
    } else {
        s_parola.setSpeed(sp);
        s_parola.displayText(text, PA_LEFT, sp, sp, PA_SCROLL_LEFT, PA_SCROLL_LEFT);
        while (!s_parola.displayAnimate()) { delay(1); }
    }
}
