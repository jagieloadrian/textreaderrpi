#ifndef DISPLAY_H
#define DISPLAY_H

void display_init(void);
void display_clear(void);
void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps);

#endif
