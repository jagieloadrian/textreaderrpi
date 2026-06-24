#ifndef SSD1327_H
#define SSD1327_H

void ssd1327_init(void);
void ssd1327_clear(void);
void ssd1327_display_text(const char *text, const char *effect, int speed_ms);

#endif
