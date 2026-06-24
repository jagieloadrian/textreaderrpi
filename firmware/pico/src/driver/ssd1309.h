#ifndef SSD1309_H
#define SSD1309_H

void ssd1309_init(void);
void ssd1309_clear(void);
void ssd1309_display_text(const char *text, const char *effect, int speed_ms);

#endif
