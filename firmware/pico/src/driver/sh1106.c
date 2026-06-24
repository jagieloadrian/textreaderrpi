#ifdef DISPLAY_DRIVER_SH1106

#include "sh1106.h"
#include "display.h"
#include "../../font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include "hardware/gpio.h"
#include <string.h>
#include <stdlib.h>

#define SH1106_SPI      spi0
#define SH1106_MOSI     19
#define SH1106_CLK      18
#define SH1106_CS       17
#define SH1106_DC       20
#define SH1106_RST      21
#define SH1106_WIDTH    128
#define SH1106_PAGES    8
#define SH1106_COL_OFFSET 2

static uint8_t s_fb[SH1106_WIDTH * SH1106_PAGES];

static void sh1106_cmd(uint8_t cmd) {
    gpio_put(SH1106_DC, 0);
    gpio_put(SH1106_CS, 0);
    spi_write_blocking(SH1106_SPI, &cmd, 1);
    gpio_put(SH1106_CS, 1);
}

static void sh1106_data(const uint8_t *buf, size_t len) {
    gpio_put(SH1106_DC, 1);
    gpio_put(SH1106_CS, 0);
    spi_write_blocking(SH1106_SPI, buf, len);
    gpio_put(SH1106_CS, 1);
}

static void sh1106_flush(void) {
    for (int page = 0; page < SH1106_PAGES; page++) {
        sh1106_cmd((uint8_t)(0xB0 | page));
        sh1106_cmd((uint8_t)(0x00 | (SH1106_COL_OFFSET & 0x0F)));
        sh1106_cmd((uint8_t)(0x10 | ((SH1106_COL_OFFSET >> 4) & 0x0F)));
        sh1106_data(&s_fb[page * SH1106_WIDTH], SH1106_WIDTH);
    }
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
    for (int i = 0; i < SH1106_WIDTH && idx < max_bytes; i++) out[idx++] = 0x00;
    return idx;
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

void display_init(void) {
    spi_init(SH1106_SPI, 8000000);
    gpio_set_function(SH1106_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(SH1106_CLK,  GPIO_FUNC_SPI);
    gpio_init(SH1106_CS);  gpio_set_dir(SH1106_CS, GPIO_OUT);  gpio_put(SH1106_CS, 1);
    gpio_init(SH1106_DC);  gpio_set_dir(SH1106_DC, GPIO_OUT);  gpio_put(SH1106_DC, 0);
    gpio_init(SH1106_RST); gpio_set_dir(SH1106_RST, GPIO_OUT); gpio_put(SH1106_RST, 1);

    gpio_put(SH1106_RST, 0); sleep_ms(10);
    gpio_put(SH1106_RST, 1); sleep_ms(10);

    sh1106_cmd(0xAE);
    sh1106_cmd(0xD5); sh1106_cmd(0x80);
    sh1106_cmd(0xA8); sh1106_cmd(0x3F);
    sh1106_cmd(0xD3); sh1106_cmd(0x00);
    sh1106_cmd(0x40);
    sh1106_cmd(0xAD); sh1106_cmd(0x8B);
    sh1106_cmd(0xA1);
    sh1106_cmd(0xC8);
    sh1106_cmd(0xDA); sh1106_cmd(0x12);
    sh1106_cmd(0x81); sh1106_cmd(0xCF);
    sh1106_cmd(0xD9); sh1106_cmd(0x1F);
    sh1106_cmd(0xDB); sh1106_cmd(0x40);
    sh1106_cmd(0xA4);
    sh1106_cmd(0xA6);
    sh1106_cmd(0xAF);
    memset(s_fb, 0, sizeof(s_fb));
}

void display_clear(void) {
    memset(s_fb, 0, sizeof(s_fb));
    sh1106_flush();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    static uint8_t bmp[1024];
    int bmp_len;

    if (strcmp(effect, "SCROLL") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - SH1106_WIDTH; offset++) {
            for (int page = 0; page < SH1106_PAGES; page++)
                for (int col = 0; col < SH1106_WIDTH; col++) {
                    int bi = offset + col;
                    s_fb[page * SH1106_WIDTH + col] = (bi < bmp_len) ? bmp[bi] : 0x00;
                }
            sh1106_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        for (int page = 0; page < SH1106_PAGES; page++)
            for (int col = 0; col < SH1106_WIDTH; col++)
                s_fb[page * SH1106_WIDTH + col] = (col < bmp_len) ? bmp[col] : 0x00;
        sh1106_flush();
        for (int i = 0; i < 6; i++) {
            sleep_ms((uint32_t)sp); sh1106_cmd(0xAE);
            sleep_ms((uint32_t)sp); sh1106_cmd(0xAF);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128];
        int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        bmp_len = build_bitmap(rev, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - SH1106_WIDTH; offset++) {
            for (int page = 0; page < SH1106_PAGES; page++)
                for (int col = 0; col < SH1106_WIDTH; col++) {
                    int bi = offset + col;
                    s_fb[page * SH1106_WIDTH + col] = (bi < bmp_len) ? bmp[bi] : 0x00;
                }
            sh1106_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "FADE") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        for (int page = 0; page < SH1106_PAGES; page++)
            for (int col = 0; col < SH1106_WIDTH; col++)
                s_fb[page * SH1106_WIDTH + col] = (col < bmp_len) ? bmp[col] : 0x00;
        sh1106_flush();
        int step_delay = sp / 32 + 1;
        for (int v = 0; v <= 255; v += 8) { sh1106_cmd(0x81); sh1106_cmd((uint8_t)v); sleep_ms((uint32_t)step_delay); }
        for (int v = 255; v >= 0; v -= 8) { sh1106_cmd(0x81); sh1106_cmd((uint8_t)v); sleep_ms((uint32_t)step_delay); }
        sh1106_cmd(0x81); sh1106_cmd(0xCF);
    }
}

#endif
