#include "display.h"
#include "max7219.h"

void display_init(void) {
    max7219_init();
}

void display_clear(void) {
    max7219_clear();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    max7219_display_text(text, effect, speed_ms);
}
