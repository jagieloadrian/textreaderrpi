#ifdef DISPLAY_DRIVER_ILI9225

#include "ili9225.h"
#include "display.h"
#include "font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include "hardware/gpio.h"
#include <string.h>
#include <stdlib.h>

#define ILI9225_SPI     spi0
#define ILI9225_MOSI    19
#define ILI9225_CLK     18
#define ILI9225_CS      17
#define ILI9225_RS      20
#define ILI9225_RST     21
#define ILI9225_WIDTH   176
#define ILI9225_HEIGHT  220

static void ili9225_write_reg(uint8_t reg, uint16_t data) {
    gpio_put(ILI9225_RS, 0); gpio_put(ILI9225_CS, 0);
    uint8_t r[2] = {0x00, reg};
    spi_write_blocking(ILI9225_SPI, r, 2);
    gpio_put(ILI9225_RS, 1);
    uint8_t d[2] = {(uint8_t)(data >> 8), (uint8_t)(data & 0xFF)};
    spi_write_blocking(ILI9225_SPI, d, 2);
    gpio_put(ILI9225_CS, 1);
}

static void ili9225_write_data16(uint16_t data) {
    gpio_put(ILI9225_RS, 1); gpio_put(ILI9225_CS, 0);
    uint8_t d[2] = {(uint8_t)(data >> 8), (uint8_t)(data & 0xFF)};
    spi_write_blocking(ILI9225_SPI, d, 2);
    gpio_put(ILI9225_CS, 1);
}

static void ili9225_set_window(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1) {
    ili9225_write_reg(0x36, x1);
    ili9225_write_reg(0x37, x0);
    ili9225_write_reg(0x38, y1);
    ili9225_write_reg(0x39, y0);
    ili9225_write_reg(0x20, x0);
    ili9225_write_reg(0x21, y0);
    gpio_put(ILI9225_RS, 0); gpio_put(ILI9225_CS, 0);
    uint8_t r[2] = {0x00, 0x22};
    spi_write_blocking(ILI9225_SPI, r, 2);
    gpio_put(ILI9225_RS, 1);
    gpio_put(ILI9225_CS, 1);
}

static void fill_rect(uint16_t x, uint16_t y, uint16_t w, uint16_t h, uint16_t color) {
    ili9225_set_window(x, y, (uint16_t)(x + w - 1), (uint16_t)(y + h - 1));
    for (int i = 0; i < w * h; i++) ili9225_write_data16(color);
}

static void draw_char(uint16_t x, uint16_t y, unsigned char c, uint16_t fg, uint16_t bg) {
    int fi = c - 0x20;
    if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
    for (int col = 0; col < FONT_CHAR_WIDTH; col++) {
        uint8_t cb = font5x8[fi][col];
        for (int row = 0; row < 8; row++) {
            uint16_t color = ((cb >> row) & 1) ? fg : bg;
            fill_rect((uint16_t)(x + col), (uint16_t)(y + row), 1, 1, color);
        }
    }
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render_text(const char *text, int x_offset, uint16_t fg, uint16_t bg) {
    fill_rect(0, 0, ILI9225_WIDTH, 16, bg);
    int x = -x_offset;
    for (int i = 0; text[i] != '\0'; i++) {
        if (x >= ILI9225_WIDTH) break;
        if (x + FONT_CHAR_WIDTH > 0)
            draw_char((uint16_t)(x < 0 ? 0 : x), 4, (unsigned char)text[i], fg, bg);
        x += FONT_CHAR_WIDTH + FONT_CHAR_GAP;
    }
}

void display_init(void) {
    spi_init(ILI9225_SPI, 8000000);
    gpio_set_function(ILI9225_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(ILI9225_CLK,  GPIO_FUNC_SPI);
    gpio_init(ILI9225_CS);  gpio_set_dir(ILI9225_CS, GPIO_OUT);  gpio_put(ILI9225_CS, 1);
    gpio_init(ILI9225_RS);  gpio_set_dir(ILI9225_RS, GPIO_OUT);  gpio_put(ILI9225_RS, 1);
    gpio_init(ILI9225_RST); gpio_set_dir(ILI9225_RST, GPIO_OUT); gpio_put(ILI9225_RST, 1);
    gpio_put(ILI9225_RST, 0); sleep_ms(10); gpio_put(ILI9225_RST, 1); sleep_ms(50);

    ili9225_write_reg(0x01, 0x011C);
    ili9225_write_reg(0x02, 0x0100);
    ili9225_write_reg(0x03, 0x1030);
    ili9225_write_reg(0x08, 0x0808);
    ili9225_write_reg(0x0B, 0x1100);
    ili9225_write_reg(0x0C, 0x0000);
    ili9225_write_reg(0x0F, 0x0A01);
    ili9225_write_reg(0x15, 0x0020);
    ili9225_write_reg(0x20, 0x0000);
    ili9225_write_reg(0x21, 0x0000);
    sleep_ms(50);
    ili9225_write_reg(0x10, 0x0800);
    sleep_ms(10);
    ili9225_write_reg(0x11, 0x1F3F);
    sleep_ms(10);
    ili9225_write_reg(0x12, 0x0121);
    ili9225_write_reg(0x13, 0x006F);
    ili9225_write_reg(0x14, 0x4349);
    ili9225_write_reg(0x30, 0x0000);
    ili9225_write_reg(0x31, 0x00DB);
    ili9225_write_reg(0x32, 0x0000);
    ili9225_write_reg(0x33, 0x0000);
    ili9225_write_reg(0x34, 0x00DB);
    ili9225_write_reg(0x35, 0x0000);
    ili9225_write_reg(0x36, 0x00AF);
    ili9225_write_reg(0x37, 0x0000);
    ili9225_write_reg(0x38, 0x00DB);
    ili9225_write_reg(0x39, 0x0000);
    ili9225_write_reg(0x07, 0x1017);
    fill_rect(0, 0, ILI9225_WIDTH, ILI9225_HEIGHT, 0x0000);
}

void display_clear(void) {
    fill_rect(0, 0, ILI9225_WIDTH, ILI9225_HEIGHT, 0x0000);
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);
    int text_px = (int)strlen(text) * (FONT_CHAR_WIDTH + FONT_CHAR_GAP);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int offset = 0; offset <= text_px; offset++) {
            render_text(text, offset, 0xFFFF, 0x0000);
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render_text(text, 0, 0xFFFF, 0x0000);
        for (int i = 0; i < 6; i++) {
            sleep_ms((uint32_t)sp); ili9225_write_reg(0x07, 0x1013);
            sleep_ms((uint32_t)sp); ili9225_write_reg(0x07, 0x1017);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        int rev_px = cl * (FONT_CHAR_WIDTH + FONT_CHAR_GAP);
        for (int offset = 0; offset <= rev_px; offset++) {
            render_text(rev, offset, 0xFFFF, 0x0000);
            sleep_ms((uint32_t)sp);
        }
    } else if (strcmp(effect, "FADE") == 0) {
        render_text(text, 0, 0xFFFF, 0x0000);
        int step_delay = sp / 32 + 1;
        for (int v = 0; v <= 31; v++) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render_text(text, 0, c, 0x0000); sleep_ms((uint32_t)step_delay);
        }
        for (int v = 31; v >= 0; v--) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render_text(text, 0, c, 0x0000); sleep_ms((uint32_t)step_delay);
        }
        render_text(text, 0, 0xFFFF, 0x0000);
    }
}

#endif
