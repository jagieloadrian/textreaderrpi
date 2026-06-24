#ifndef HT16K33_H
#define HT16K33_H

void ht16k33_init(void);
void ht16k33_clear(void);
void ht16k33_display_text(const char *text, const char *effect, int speed_ms);

#endif
