#include "display.h"

#if defined(DISPLAY_DRIVER_SSD1306)

#elif defined(DISPLAY_DRIVER_SSD1309)

#elif defined(DISPLAY_DRIVER_SSD1327)

#elif defined(DISPLAY_DRIVER_SH1106)

#elif defined(DISPLAY_DRIVER_HT16K33)

#elif defined(DISPLAY_DRIVER_ST7735)

#elif defined(DISPLAY_DRIVER_ST7789)

#elif defined(DISPLAY_DRIVER_ILI9225)

#elif defined(DISPLAY_DRIVER_PCD8544)

#elif defined(DISPLAY_DRIVER_SSD1680)

#else

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

#endif
