#ifndef SH1106_H
#define SH1106_H

void sh1106_init(void);
void sh1106_clear(void);
void sh1106_display_text(const char *text, const char *effect, int speed_ms);

#endif
