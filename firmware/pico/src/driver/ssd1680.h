#ifndef SSD1680_H
#define SSD1680_H

#define EPAPER_PARTIAL_REFRESH 1

void ssd1680_init(void);
void ssd1680_clear(void);
void ssd1680_display_text(const char *text, const char *effect, int speed_ms);

#endif
