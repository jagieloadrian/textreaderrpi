#ifdef DISPLAY_DRIVER_ST7735

#include "st7735.h"
#include "display.h"
#include "../../font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include "hardware/gpio.h"
#include <string.h>
#include <stdlib.h>

#define ST7735_SPI      spi0
#define ST7735_MOSI     19
#define ST7735_CLK      18
#define ST7735_CS       17
#define ST7735_DC       20
#define ST7735_RST      21
#define ST7735_WIDTH    128
#define ST7735_HEIGHT   160

#define ST7735_NOP      0x00
#define ST7735_SWRESET  0x01
#define ST7735_SLPOUT   0x11
#define ST7735_COLMOD   0x3A
#define ST7735_MADCTL   0x36
#define ST7735_CASET    0x2A
#define ST7735_RASET    0x2B
#define ST7735_RAMWR    0x2C
#define ST7735_DISPON   0x29

static void st7735_cmd(uint8_t cmd) {
    gpio_put(ST7735_DC, 0); gpio_put(ST7735_CS, 0);
    spi_write_blocking(ST7735_SPI, &cmd, 1);
    gpio_put(ST7735_CS, 1);
}

static void st7735_data(const uint8_t *buf, size_t len) {
    gpio_put(ST7735_DC, 1); gpio_put(ST7735_CS, 0);
    spi_write_blocking(ST7735_SPI, buf, len);
    gpio_put(ST7735_CS, 1);
}

static void st7735_data8(uint8_t v)  { st7735_data(&v, 1); }
static void st7735_data16(uint16_t v) {
    uint8_t buf[2] = {(uint8_t)(v >> 8), (uint8_t)(v & 0xFF)};
    st7735_data(buf, 2);
}

static void st7735_set_window(uint8_t x0, uint8_t y0, uint8_t x1, uint8_t y1) {
    st7735_cmd(ST7735_CASET);
    st7735_data8(0); st7735_data8(x0); st7735_data8(0); st7735_data8(x1);
    st7735_cmd(ST7735_RASET);
    st7735_data8(0); st7735_data8(y0); st7735_data8(0); st7735_data8(y1);
    st7735_cmd(ST7735_RAMWR);
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void fill_rect(uint8_t x, uint8_t y, uint8_t w, uint8_t h, uint16_t color) {
    st7735_set_window(x, y, (uint8_t)(x + w - 1), (uint8_t)(y + h - 1));
    gpio_put(ST7735_DC, 1); gpio_put(ST7735_CS, 0);
    uint8_t buf[2] = {(uint8_t)(color >> 8), (uint8_t)(color & 0xFF)};
    for (int i = 0; i < w * h; i++) spi_write_blocking(ST7735_SPI, buf, 2);
    gpio_put(ST7735_CS, 1);
}

static void draw_char(uint8_t x, uint8_t y, unsigned char c, uint16_t fg, uint16_t bg) {
    int fi = c - 0x20;
    if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
    for (int col = 0; col < FONT_CHAR_WIDTH; col++) {
        uint8_t column_byte = font5x8[fi][col];
        for (int row = 0; row < 8; row++) {
            uint16_t color = ((column_byte >> row) & 1) ? fg : bg;
            fill_rect((uint8_t)(x + col), (uint8_t)(y + row), 1, 1, color);
        }
    }
}

static void render_text(const char *text, int x_offset, uint16_t fg, uint16_t bg) {
    fill_rect(0, 0, ST7735_WIDTH, 8, bg);
    int x = -x_offset;
    for (int i = 0; text[i] != '\0'; i++) {
        if (x >= ST7735_WIDTH) break;
        if (x + FONT_CHAR_WIDTH > 0) {
            draw_char((uint8_t)(x < 0 ? 0 : x), 0, (unsigned char)text[i], fg, bg);
        }
        x += FONT_CHAR_WIDTH + FONT_CHAR_GAP;
    }
}

void display_init(void) {
    spi_init(ST7735_SPI, 8000000);
    gpio_set_function(ST7735_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(ST7735_CLK,  GPIO_FUNC_SPI);
    gpio_init(ST7735_CS);  gpio_set_dir(ST7735_CS, GPIO_OUT);  gpio_put(ST7735_CS, 1);
    gpio_init(ST7735_DC);  gpio_set_dir(ST7735_DC, GPIO_OUT);  gpio_put(ST7735_DC, 0);
    gpio_init(ST7735_RST); gpio_set_dir(ST7735_RST, GPIO_OUT); gpio_put(ST7735_RST, 1);
    gpio_put(ST7735_RST, 0); sleep_ms(10); gpio_put(ST7735_RST, 1); sleep_ms(120);
    st7735_cmd(ST7735_SWRESET); sleep_ms(150);
    st7735_cmd(ST7735_SLPOUT);  sleep_ms(255);
    st7735_cmd(ST7735_COLMOD);  st7735_data8(0x05);
    st7735_cmd(ST7735_MADCTL);  st7735_data8(0x00);
    st7735_cmd(ST7735_DISPON);
    fill_rect(0, 0, ST7735_WIDTH, ST7735_HEIGHT, 0x0000);
}

void display_clear(void) {
    fill_rect(0, 0, ST7735_WIDTH, ST7735_HEIGHT, 0x0000);
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
            sleep_ms((uint32_t)sp); st7735_cmd(0x28);
            sleep_ms((uint32_t)sp); st7735_cmd(0x29);
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
            render_text(text, 0, c, 0x0000);
            sleep_ms((uint32_t)step_delay);
        }
        for (int v = 31; v >= 0; v--) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render_text(text, 0, c, 0x0000);
            sleep_ms((uint32_t)step_delay);
        }
        render_text(text, 0, 0xFFFF, 0x0000);
    }
}

#endif
