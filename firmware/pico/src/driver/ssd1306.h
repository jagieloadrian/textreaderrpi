#ifndef SSD1306_H
#define SSD1306_H

void ssd1306_init(void);
void ssd1306_clear(void);
void ssd1306_display_text(const char *text, const char *effect, int speed_ms);

#endif
