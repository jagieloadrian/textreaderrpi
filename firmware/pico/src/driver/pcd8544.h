#ifndef PCD8544_H
#define PCD8544_H

void pcd8544_init(void);
void pcd8544_clear(void);
void pcd8544_display_text(const char *text, const char *effect, int speed_ms);

#endif
