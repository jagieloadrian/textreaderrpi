#include "display.h"
#include "font/font5x8.h"
#include "config.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include <string.h>
#include <stdlib.h>

#define MAX7219_SPI      spi0
#define MAX7219_PIN_MOSI 19
#define MAX7219_PIN_CLK  18
#define MAX7219_PIN_CS   17

#define REG_NOOP        0x00
#define REG_DECODE_MODE 0x09
#define REG_INTENSITY   0x0A
#define REG_SCAN_LIMIT  0x0B
#define REG_SHUTDOWN    0x0C
#define REG_DISPLAY_TEST 0x0F
#define REG_ROW_BASE    0x01

#define MAX_BITMAP_LEN  (256 + NUM_DEVICES * 8)

static uint8_t s_bitmap[MAX_BITMAP_LEN];
static int     s_bitmap_len;

void max7219_send(uint8_t reg, uint8_t data, int device_index) {
    uint8_t buf[NUM_DEVICES * 2];
    memset(buf, 0, sizeof(buf));
    buf[device_index * 2]     = reg;
    buf[device_index * 2 + 1] = data;
    gpio_put(MAX7219_PIN_CS, 0);
    spi_write_blocking(MAX7219_SPI, buf, sizeof(buf));
    gpio_put(MAX7219_PIN_CS, 1);
}

void max7219_send_all(uint8_t reg, uint8_t data) {
    uint8_t buf[NUM_DEVICES * 2];
    for (int i = 0; i < NUM_DEVICES; i++) {
        buf[i * 2]     = reg;
        buf[i * 2 + 1] = data;
    }
    gpio_put(MAX7219_PIN_CS, 0);
    spi_write_blocking(MAX7219_SPI, buf, sizeof(buf));
    gpio_put(MAX7219_PIN_CS, 1);
}

void display_init(void) {
    spi_init(MAX7219_SPI, 10000000);
    gpio_set_function(MAX7219_PIN_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(MAX7219_PIN_CLK,  GPIO_FUNC_SPI);
    gpio_init(MAX7219_PIN_CS);
    gpio_set_dir(MAX7219_PIN_CS, GPIO_OUT);
    gpio_put(MAX7219_PIN_CS, 1);

    max7219_send_all(REG_DISPLAY_TEST, 0x00);
    max7219_send_all(REG_DECODE_MODE,  0x00);
    max7219_send_all(REG_SCAN_LIMIT,   0x07);
    max7219_send_all(REG_INTENSITY,    0x04);
    max7219_send_all(REG_SHUTDOWN,     0x01);
}

void display_clear(void) {
    for (int row = 0; row < 8; row++) {
        max7219_send_all(REG_ROW_BASE + row, 0x00);
    }
}

static void render_columns(int offset) {
    for (int row = 0; row < 8; row++) {
        uint8_t buf[NUM_DEVICES * 2];
        for (int d = 0; d < NUM_DEVICES; d++) {
            int col_idx = offset + (NUM_DEVICES - 1 - d) * 8 + row;
            uint8_t col_byte = (col_idx < s_bitmap_len) ? s_bitmap[col_idx] : 0;
            buf[d * 2]     = REG_ROW_BASE + row;
            buf[d * 2 + 1] = col_byte;
        }
        gpio_put(MAX7219_PIN_CS, 0);
        spi_write_blocking(MAX7219_SPI, buf, sizeof(buf));
        gpio_put(MAX7219_PIN_CS, 1);
    }
}

static int build_text_bitmap(const char *text) {
    int idx = 0;
    for (int i = 0; text[i] != '\0' && idx < MAX_BITMAP_LEN - (NUM_DEVICES * 8 + 1); i++) {
        unsigned char c = (unsigned char)text[i];
        int font_idx = c - 0x20;
        if (font_idx < 0 || font_idx >= FONT_TABLE_SIZE) font_idx = 0;
        for (int col = 0; col < FONT_CHAR_WIDTH; col++) {
            s_bitmap[idx++] = font5x8[font_idx][col];
        }
        s_bitmap[idx++] = 0x00;
    }
    int trailing = NUM_DEVICES * 8;
    for (int i = 0; i < trailing && idx < MAX_BITMAP_LEN; i++) {
        s_bitmap[idx++] = 0x00;
    }
    return idx;
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    if (speed_ms <= 0) speed_ms = 1;
    if (speed_ms > 5000) speed_ms = 5000;
    if (blink_period_ms <= 0) blink_period_ms = 500;
    if (fade_steps <= 0) fade_steps = 8;

    int display_w = NUM_DEVICES * 8;

    if (strcmp(effect, "SCROLL") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        int offset = 0;
        while (offset <= s_bitmap_len - display_w) {
            render_columns(offset);
            sleep_ms((uint32_t)speed_ms);
            offset++;
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        render_columns(0);
        for (int i = 0; i < 6; i++) {
            sleep_ms((uint32_t)blink_period_ms);
            max7219_send_all(REG_SHUTDOWN, 0x00);
            sleep_ms((uint32_t)blink_period_ms);
            max7219_send_all(REG_SHUTDOWN, 0x01);
            render_columns(0);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        int rev_offset = (s_bitmap_len > display_w) ? s_bitmap_len - display_w : 0;
        while (rev_offset >= 0) {
            render_columns(rev_offset);
            sleep_ms((uint32_t)speed_ms);
            rev_offset--;
        }
    } else if (strcmp(effect, "FADE") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        render_columns(0);
        int div = (fade_steps > 1) ? fade_steps - 1 : 1;
        for (int step = 0; step < fade_steps; step++) {
            max7219_send_all(REG_INTENSITY, (uint8_t)((step * 15) / div));
            sleep_ms((uint32_t)(speed_ms / fade_steps));
        }
        for (int step = fade_steps - 1; step >= 0; step--) {
            max7219_send_all(REG_INTENSITY, (uint8_t)((step * 15) / div));
            sleep_ms((uint32_t)(speed_ms / fade_steps));
        }
        max7219_send_all(REG_INTENSITY, 0x04);
    } else {
        s_bitmap_len = build_text_bitmap(text);
        render_columns(0);
        sleep_ms(2000);
    }
}
