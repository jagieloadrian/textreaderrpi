#ifdef DISPLAY_DRIVER_SSD1306

#include "ssd1306.h"
#include "display.h"
#include "font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/i2c.h"
#include <string.h>
#include <stdlib.h>

#define SSD1306_I2C         i2c0
#define SSD1306_I2C_SDA     4
#define SSD1306_I2C_SCL     5
#define SSD1306_ADDR        0x3C
#define SSD1306_WIDTH       128
#define SSD1306_HEIGHT      64
#define SSD1306_PAGES       (SSD1306_HEIGHT / 8)

static uint8_t s_fb[SSD1306_WIDTH * SSD1306_PAGES];

static void ssd1306_cmd(uint8_t cmd) {
    uint8_t buf[2] = {0x00, cmd};
    i2c_write_blocking(SSD1306_I2C, SSD1306_ADDR, buf, 2, false);
}

static void ssd1306_flush(void) {
    for (int page = 0; page < SSD1306_PAGES; page++) {
        uint8_t header[3] = {0x00, (uint8_t)(0xB0 | page), 0x00};
        i2c_write_blocking(SSD1306_I2C, SSD1306_ADDR, header, 1, false);
        ssd1306_cmd((uint8_t)(0xB0 | page));
        ssd1306_cmd(0x00);
        ssd1306_cmd(0x10);
        uint8_t buf[SSD1306_WIDTH + 1];
        buf[0] = 0x40;
        memcpy(buf + 1, &s_fb[page * SSD1306_WIDTH], SSD1306_WIDTH);
        i2c_write_blocking(SSD1306_I2C, SSD1306_ADDR, buf, sizeof(buf), false);
    }
}

static int build_text_bitmap(const char *text, uint8_t *out, int max_bytes) {
    int idx = 0;
    for (int i = 0; text[i] != '\0' && idx < max_bytes - (FONT_CHAR_WIDTH + 1); i++) {
        unsigned char c = (unsigned char)text[i];
        int fi = c - 0x20;
        if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
        for (int col = 0; col < FONT_CHAR_WIDTH; col++) {
            out[idx++] = font5x8[fi][col];
        }
        out[idx++] = 0x00;
    }
    for (int i = 0; i < SSD1306_WIDTH && idx < max_bytes; i++) {
        out[idx++] = 0x00;
    }
    return idx;
}

static int clamp_speed(int s) {
    if (s < 1) return 1;
    if (s > 5000) return 5000;
    return s;
}

void display_init(void) {
    i2c_init(SSD1306_I2C, 400000);
    gpio_set_function(SSD1306_I2C_SDA, GPIO_FUNC_I2C);
    gpio_set_function(SSD1306_I2C_SCL, GPIO_FUNC_I2C);
    gpio_pull_up(SSD1306_I2C_SDA);
    gpio_pull_up(SSD1306_I2C_SCL);

    sleep_ms(100);
    ssd1306_cmd(0xAE);
    ssd1306_cmd(0xD5); ssd1306_cmd(0x80);
    ssd1306_cmd(0xA8); ssd1306_cmd(SSD1306_HEIGHT - 1);
    ssd1306_cmd(0xD3); ssd1306_cmd(0x00);
    ssd1306_cmd(0x40);
    ssd1306_cmd(0x8D); ssd1306_cmd(0x14);
    ssd1306_cmd(0x20); ssd1306_cmd(0x00);
    ssd1306_cmd(0xA1);
    ssd1306_cmd(0xC8);
    ssd1306_cmd(0xDA); ssd1306_cmd(0x12);
    ssd1306_cmd(0x81); ssd1306_cmd(0xCF);
    ssd1306_cmd(0xD9); ssd1306_cmd(0xF1);
    ssd1306_cmd(0xDB); ssd1306_cmd(0x40);
    ssd1306_cmd(0xA4);
    ssd1306_cmd(0xA6);
    ssd1306_cmd(0xAF);
    memset(s_fb, 0, sizeof(s_fb));
}

void display_clear(void) {
    memset(s_fb, 0, sizeof(s_fb));
    ssd1306_flush();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);

    static uint8_t bmp[1024];
    int bmp_len;

    if (strcmp(effect, "SCROLL") == 0) {
        bmp_len = build_text_bitmap(text, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - SSD1306_WIDTH; offset++) {
            for (int page = 0; page < SSD1306_PAGES; page++) {
                for (int col = 0; col < SSD1306_WIDTH; col++) {
                    int bi = offset + col;
                    s_fb[page * SSD1306_WIDTH + col] = (bi < bmp_len) ? bmp[bi] : 0x00;
                }
            }
            ssd1306_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        bmp_len = build_text_bitmap(text, bmp, sizeof(bmp));
        for (int page = 0; page < SSD1306_PAGES; page++) {
            for (int col = 0; col < SSD1306_WIDTH; col++) {
                s_fb[page * SSD1306_WIDTH + col] = (col < bmp_len) ? bmp[col] : 0x00;
            }
        }
        ssd1306_flush();
        for (int i = 0; i < 6; i++) {
            sleep_ms((uint32_t)sp);
            ssd1306_cmd(0xAE);
            sleep_ms((uint32_t)sp);
            ssd1306_cmd(0xAF);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128];
        int copy_len = len < 127 ? len : 127;
        for (int i = 0; i < copy_len; i++) rev[i] = text[copy_len - 1 - i];
        rev[copy_len] = '\0';
        bmp_len = build_text_bitmap(rev, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - SSD1306_WIDTH; offset++) {
            for (int page = 0; page < SSD1306_PAGES; page++) {
                for (int col = 0; col < SSD1306_WIDTH; col++) {
                    int bi = offset + col;
                    s_fb[page * SSD1306_WIDTH + col] = (bi < bmp_len) ? bmp[bi] : 0x00;
                }
            }
            ssd1306_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "FADE") == 0) {
        bmp_len = build_text_bitmap(text, bmp, sizeof(bmp));
        for (int page = 0; page < SSD1306_PAGES; page++) {
            for (int col = 0; col < SSD1306_WIDTH; col++) {
                s_fb[page * SSD1306_WIDTH + col] = (col < bmp_len) ? bmp[col] : 0x00;
            }
        }
        ssd1306_flush();
        int step_delay = sp / 32 + 1;
        for (int v = 0; v <= 255; v += 8) {
            ssd1306_cmd(0x81); ssd1306_cmd((uint8_t)v);
            sleep_ms((uint32_t)step_delay);
        }
        for (int v = 255; v >= 0; v -= 8) {
            ssd1306_cmd(0x81); ssd1306_cmd((uint8_t)v);
            sleep_ms((uint32_t)step_delay);
        }
        ssd1306_cmd(0x81); ssd1306_cmd(0xCF);
    }
}

#endif
