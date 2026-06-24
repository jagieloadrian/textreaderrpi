#ifndef ST7735_H
#define ST7735_H

void st7735_init(void);
void st7735_clear(void);
void st7735_display_text(const char *text, const char *effect, int speed_ms);

#endif
