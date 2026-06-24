#ifndef ST7789_H
#define ST7789_H

void st7789_init(void);
void st7789_clear(void);
void st7789_display_text(const char *text, const char *effect, int speed_ms);

#endif
