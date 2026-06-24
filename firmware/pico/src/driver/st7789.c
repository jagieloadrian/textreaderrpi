#ifdef DISPLAY_DRIVER_ST7789

#include "st7789.h"
#include "display.h"
#include "../../font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include "hardware/gpio.h"
#include <string.h>
#include <stdlib.h>

#define ST7789_SPI      spi0
#define ST7789_MOSI     19
#define ST7789_CLK      18
#define ST7789_CS       17
#define ST7789_DC       20
#define ST7789_RST      21
#define ST7789_WIDTH    240
#define ST7789_HEIGHT   240

static void st7789_cmd(uint8_t cmd) {
    gpio_put(ST7789_DC, 0); gpio_put(ST7789_CS, 0);
    spi_write_blocking(ST7789_SPI, &cmd, 1);
    gpio_put(ST7789_CS, 1);
}

static void st7789_data(const uint8_t *buf, size_t len) {
    gpio_put(ST7789_DC, 1); gpio_put(ST7789_CS, 0);
    spi_write_blocking(ST7789_SPI, buf, len);
    gpio_put(ST7789_CS, 1);
}

static void st7789_data8(uint8_t v)  { st7789_data(&v, 1); }

static void st7789_set_window(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1) {
    st7789_cmd(0x2A);
    st7789_data8((uint8_t)(x0 >> 8)); st7789_data8((uint8_t)(x0 & 0xFF));
    st7789_data8((uint8_t)(x1 >> 8)); st7789_data8((uint8_t)(x1 & 0xFF));
    st7789_cmd(0x2B);
    st7789_data8((uint8_t)(y0 >> 8)); st7789_data8((uint8_t)(y0 & 0xFF));
    st7789_data8((uint8_t)(y1 >> 8)); st7789_data8((uint8_t)(y1 & 0xFF));
    st7789_cmd(0x2C);
}

static void fill_rect(uint16_t x, uint16_t y, uint16_t w, uint16_t h, uint16_t color) {
    st7789_set_window(x, y, (uint16_t)(x + w - 1), (uint16_t)(y + h - 1));
    gpio_put(ST7789_DC, 1); gpio_put(ST7789_CS, 0);
    uint8_t buf[2] = {(uint8_t)(color >> 8), (uint8_t)(color & 0xFF)};
    for (int i = 0; i < w * h; i++) spi_write_blocking(ST7789_SPI, buf, 2);
    gpio_put(ST7789_CS, 1);
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
    fill_rect(0, 0, ST7789_WIDTH, 16, bg);
    int x = -x_offset;
    for (int i = 0; text[i] != '\0'; i++) {
        if (x >= ST7789_WIDTH) break;
        if (x + FONT_CHAR_WIDTH > 0)
            draw_char((uint16_t)(x < 0 ? 0 : x), 4, (unsigned char)text[i], fg, bg);
        x += FONT_CHAR_WIDTH + FONT_CHAR_GAP;
    }
}

void display_init(void) {
    spi_init(ST7789_SPI, 16000000);
    gpio_set_function(ST7789_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(ST7789_CLK,  GPIO_FUNC_SPI);
    gpio_init(ST7789_CS);  gpio_set_dir(ST7789_CS, GPIO_OUT);  gpio_put(ST7789_CS, 1);
    gpio_init(ST7789_DC);  gpio_set_dir(ST7789_DC, GPIO_OUT);  gpio_put(ST7789_DC, 0);
    gpio_init(ST7789_RST); gpio_set_dir(ST7789_RST, GPIO_OUT); gpio_put(ST7789_RST, 1);
    gpio_put(ST7789_RST, 0); sleep_ms(10); gpio_put(ST7789_RST, 1); sleep_ms(120);
    st7789_cmd(0x01); sleep_ms(150);
    st7789_cmd(0x11); sleep_ms(120);
    st7789_cmd(0x3A); st7789_data8(0x05);
    st7789_cmd(0x36); st7789_data8(0x00);
    st7789_cmd(0x21);
    st7789_cmd(0x29);
    fill_rect(0, 0, ST7789_WIDTH, ST7789_HEIGHT, 0x0000);
}

void display_clear(void) {
    fill_rect(0, 0, ST7789_WIDTH, ST7789_HEIGHT, 0x0000);
}

void display_text(const char *text, const char *effect, int speed_ms) {
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
            sleep_ms((uint32_t)sp); st7789_cmd(0x28);
            sleep_ms((uint32_t)sp); st7789_cmd(0x29);
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
