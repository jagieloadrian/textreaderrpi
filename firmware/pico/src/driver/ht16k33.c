#ifdef DISPLAY_DRIVER_HT16K33

#include "ht16k33.h"
#include "display.h"
#include "../../font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/i2c.h"
#include <string.h>
#include <stdlib.h>

#define HT16K33_I2C     i2c0
#define HT16K33_SDA     4
#define HT16K33_SCL     5
#define HT16K33_ADDR    0x70
#define HT16K33_ROWS    8
#define HT16K33_COLS    8

static uint8_t s_rows[HT16K33_ROWS];

static void ht16k33_write_cmd(uint8_t cmd) {
    i2c_write_blocking(HT16K33_I2C, HT16K33_ADDR, &cmd, 1, false);
}

static void ht16k33_flush(void) {
    uint8_t buf[17];
    buf[0] = 0x00;
    for (int i = 0; i < HT16K33_ROWS; i++) {
        buf[1 + i * 2]     = s_rows[i];
        buf[1 + i * 2 + 1] = 0x00;
    }
    i2c_write_blocking(HT16K33_I2C, HT16K33_ADDR, buf, sizeof(buf), false);
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render_char(unsigned char c) {
    int fi = c - 0x20;
    if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
    memset(s_rows, 0, sizeof(s_rows));
    for (int col = 0; col < FONT_CHAR_WIDTH && col < HT16K33_COLS; col++) {
        uint8_t column_byte = font5x8[fi][col];
        for (int row = 0; row < HT16K33_ROWS; row++) {
            if ((column_byte >> row) & 1) s_rows[row] |= (1 << col);
        }
    }
}

void display_init(void) {
    i2c_init(HT16K33_I2C, 400000);
    gpio_set_function(HT16K33_SDA, GPIO_FUNC_I2C);
    gpio_set_function(HT16K33_SCL, GPIO_FUNC_I2C);
    gpio_pull_up(HT16K33_SDA);
    gpio_pull_up(HT16K33_SCL);
    sleep_ms(10);
    ht16k33_write_cmd(0x21);
    ht16k33_write_cmd(0x81);
    ht16k33_write_cmd(0xEF);
    memset(s_rows, 0, sizeof(s_rows));
}

void display_clear(void) {
    memset(s_rows, 0, sizeof(s_rows));
    ht16k33_flush();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int i = 0; text[i] != '\0'; i++) {
            render_char((unsigned char)text[i]);
            ht16k33_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render_char((unsigned char)text[0]);
        ht16k33_flush();
        int hw_blink;
        if (sp >= 1000) hw_blink = 0x87;
        else if (sp >= 500) hw_blink = 0x85;
        else if (sp >= 250) hw_blink = 0x83;
        else hw_blink = 0x81;
        ht16k33_write_cmd((uint8_t)hw_blink);
        sleep_ms((uint32_t)(sp * 6));
        ht16k33_write_cmd(0x81);
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        for (int i = len - 1; i >= 0; i--) {
            render_char((unsigned char)text[i]);
            ht16k33_flush();
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "FADE") == 0) {
        render_char((unsigned char)text[0]);
        ht16k33_flush();
        int step_delay = sp / 16 + 1;
        for (int v = 0; v <= 15; v++) { ht16k33_write_cmd((uint8_t)(0xE0 | v)); sleep_ms((uint32_t)step_delay); }
        for (int v = 15; v >= 0; v--) { ht16k33_write_cmd((uint8_t)(0xE0 | v)); sleep_ms((uint32_t)step_delay); }
        ht16k33_write_cmd(0xEF);
    }
}

#endif
