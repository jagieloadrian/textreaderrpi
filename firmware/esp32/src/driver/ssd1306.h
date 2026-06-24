#ifndef ESP32_SSD1306_H
#define ESP32_SSD1306_H
#ifdef DISPLAY_DRIVER_SSD1306

void ssd1306_init(void);
void ssd1306_clear(void);
void ssd1306_display_text(const char *text, const char *effect, int speed_ms);

#endif
#endif
