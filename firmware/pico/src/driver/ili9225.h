#ifndef ILI9225_H
#define ILI9225_H

void ili9225_init(void);
void ili9225_clear(void);
void ili9225_display_text(const char *text, const char *effect, int speed_ms);

#endif
