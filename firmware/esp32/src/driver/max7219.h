#ifndef MAX7219_H
#define MAX7219_H

void max7219_init(void);
void max7219_clear(void);
void max7219_display_text(const char *text, const char *effect, int speed_ms);

#endif
