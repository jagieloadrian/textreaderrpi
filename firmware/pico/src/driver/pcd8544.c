#ifdef DISPLAY_DRIVER_PCD8544

#include "pcd8544.h"
#include "display.h"
#include "font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include "hardware/gpio.h"
#include <string.h>
#include <stdlib.h>

#define PCD8544_SPI     spi0
#define PCD8544_MOSI    19
#define PCD8544_CLK     18
#define PCD8544_CS      17
#define PCD8544_DC      20
#define PCD8544_RST     21
#define PCD8544_WIDTH   84
#define PCD8544_ROWS    6

static uint8_t s_fb[PCD8544_WIDTH * PCD8544_ROWS];

static void pcd8544_write(uint8_t byte, int is_data) {
    gpio_put(PCD8544_DC, is_data ? 1 : 0);
    gpio_put(PCD8544_CS, 0);
    spi_write_blocking(PCD8544_SPI, &byte, 1);
    gpio_put(PCD8544_CS, 1);
}

static void pcd8544_flush(void) {
    pcd8544_write(0x40, 0);
    pcd8544_write(0x80, 0);
    for (int i = 0; i < PCD8544_WIDTH * PCD8544_ROWS; i++) pcd8544_write(s_fb[i], 1);
}

static int build_bitmap(const char *text, uint8_t *out, int max_bytes) {
    int idx = 0;
    for (int i = 0; text[i] != '\0' && idx < max_bytes - (FONT_CHAR_WIDTH + 1); i++) {
        unsigned char c = (unsigned char)text[i];
        int fi = c - 0x20;
        if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
        for (int col = 0; col < FONT_CHAR_WIDTH; col++) out[idx++] = font5x8[fi][col];
        out[idx++] = 0x00;
    }
    for (int i = 0; i < PCD8544_WIDTH && idx < max_bytes; i++) out[idx++] = 0x00;
    return idx;
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

void display_init(void) {
    spi_init(PCD8544_SPI, 4000000);
    gpio_set_function(PCD8544_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(PCD8544_CLK,  GPIO_FUNC_SPI);
    gpio_init(PCD8544_CS);  gpio_set_dir(PCD8544_CS, GPIO_OUT);  gpio_put(PCD8544_CS, 1);
    gpio_init(PCD8544_DC);  gpio_set_dir(PCD8544_DC, GPIO_OUT);  gpio_put(PCD8544_DC, 0);
    gpio_init(PCD8544_RST); gpio_set_dir(PCD8544_RST, GPIO_OUT); gpio_put(PCD8544_RST, 1);
    gpio_put(PCD8544_RST, 0); sleep_ms(10); gpio_put(PCD8544_RST, 1);

    pcd8544_write(0x21, 0);
    pcd8544_write(0x13, 0);
    pcd8544_write(0xBF, 0);
    pcd8544_write(0x20, 0);
    pcd8544_write(0x0C, 0);
    memset(s_fb, 0, sizeof(s_fb));
}

void display_clear(void) {
    memset(s_fb, 0, sizeof(s_fb));
    pcd8544_flush();
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);
    static uint8_t bmp[512];
    int bmp_len;

    if (strcmp(effect, "SCROLL") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - PCD8544_WIDTH; offset++) {
            memset(s_fb, 0, sizeof(s_fb));
            for (int col = 0; col < PCD8544_WIDTH; col++) {
                int bi = offset + col;
                s_fb[col] = (bi < bmp_len) ? bmp[bi] : 0x00;
            }
            pcd8544_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        memset(s_fb, 0, sizeof(s_fb));
        for (int col = 0; col < PCD8544_WIDTH; col++) s_fb[col] = (col < bmp_len) ? bmp[col] : 0x00;
        pcd8544_flush();
        for (int i = 0; i < 6; i++) {
            sleep_ms((uint32_t)sp); pcd8544_write(0x09, 0);
            sleep_ms((uint32_t)sp); pcd8544_write(0x0C, 0);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        bmp_len = build_bitmap(rev, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - PCD8544_WIDTH; offset++) {
            memset(s_fb, 0, sizeof(s_fb));
            for (int col = 0; col < PCD8544_WIDTH; col++) {
                int bi = offset + col;
                s_fb[col] = (bi < bmp_len) ? bmp[bi] : 0x00;
            }
            pcd8544_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "FADE") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        memset(s_fb, 0, sizeof(s_fb));
        for (int col = 0; col < PCD8544_WIDTH; col++) s_fb[col] = (col < bmp_len) ? bmp[col] : 0x00;
        pcd8544_flush();
        int step_delay = sp / 64 + 1;
        for (int v = 0x00; v <= 0x3F; v += 2) { pcd8544_write(0x21, 0); pcd8544_write((uint8_t)(0x80 | v), 0); pcd8544_write(0x20, 0); sleep_ms((uint32_t)step_delay); }
        for (int v = 0x3F; v >= 0x00; v -= 2) { pcd8544_write(0x21, 0); pcd8544_write((uint8_t)(0x80 | v), 0); pcd8544_write(0x20, 0); sleep_ms((uint32_t)step_delay); }
        pcd8544_write(0x21, 0); pcd8544_write(0xBF, 0); pcd8544_write(0x20, 0);
    }
}

#endif
