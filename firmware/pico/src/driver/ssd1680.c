#ifdef DISPLAY_DRIVER_SSD1680

#include "ssd1680.h"
#include "display.h"
#include "font/font5x8.h"
#include "pico/stdlib.h"
#include "hardware/spi.h"
#include "hardware/gpio.h"
#include <string.h>
#include <stdlib.h>

#define SSD1680_SPI     spi0
#define SSD1680_MOSI    19
#define SSD1680_CLK     18
#define SSD1680_CS      17
#define SSD1680_DC      20
#define SSD1680_RST     21
#define SSD1680_BUSY    22

#define SSD1680_WIDTH   250
#define SSD1680_HEIGHT  122
#define SSD1680_STRIDE  ((SSD1680_WIDTH + 7) / 8)

static uint8_t s_fb[SSD1680_HEIGHT * SSD1680_STRIDE];

static void ssd1680_wait_busy(void) {
    while (gpio_get(SSD1680_BUSY)) sleep_ms(10);
}

static void ssd1680_cmd(uint8_t cmd) {
    gpio_put(SSD1680_DC, 0); gpio_put(SSD1680_CS, 0);
    spi_write_blocking(SSD1680_SPI, &cmd, 1);
    gpio_put(SSD1680_CS, 1);
}

static void ssd1680_data(const uint8_t *buf, size_t len) {
    gpio_put(SSD1680_DC, 1); gpio_put(SSD1680_CS, 0);
    spi_write_blocking(SSD1680_SPI, buf, len);
    gpio_put(SSD1680_CS, 1);
}

static void ssd1680_data8(uint8_t v) { ssd1680_data(&v, 1); }

static void ssd1680_full_refresh(void) {
    ssd1680_cmd(0x22); ssd1680_data8(0xF7);
    ssd1680_cmd(0x20);
    ssd1680_wait_busy();
}

static void ssd1680_partial_refresh(void) {
    ssd1680_cmd(0x22); ssd1680_data8(0xF4);
    ssd1680_cmd(0x20);
    ssd1680_wait_busy();
}

static void ssd1680_write_ram(void) {
    ssd1680_cmd(0x4E); ssd1680_data8(0);
    ssd1680_cmd(0x4F); ssd1680_data8(0); ssd1680_data8(0);
    ssd1680_cmd(0x24);
    ssd1680_data(s_fb, sizeof(s_fb));
}

static void set_pixel(int x, int y, int black) {
    if (x < 0 || x >= SSD1680_WIDTH || y < 0 || y >= SSD1680_HEIGHT) return;
    int byte_idx = y * SSD1680_STRIDE + x / 8;
    int bit = 7 - (x % 8);
    if (black) s_fb[byte_idx] &= ~(1 << bit);
    else       s_fb[byte_idx] |=  (1 << bit);
}

static void render_text_to_fb(const char *text, int x_offset) {
    memset(s_fb, 0xFF, sizeof(s_fb));
    int x = -x_offset;
    for (int i = 0; text[i] != '\0'; i++) {
        unsigned char c = (unsigned char)text[i];
        int fi = c - 0x20;
        if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
        for (int col = 0; col < FONT_CHAR_WIDTH; col++, x++) {
            if (x < 0 || x >= SSD1680_WIDTH) continue;
            uint8_t cb = font5x8[fi][col];
            for (int row = 0; row < 8; row++) {
                if ((cb >> row) & 1) set_pixel(x, row + 8, 1);
            }
        }
        x++;
        if (x >= SSD1680_WIDTH) break;
    }
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

void display_init(void) {
    spi_init(SSD1680_SPI, 4000000);
    gpio_set_function(SSD1680_MOSI, GPIO_FUNC_SPI);
    gpio_set_function(SSD1680_CLK,  GPIO_FUNC_SPI);
    gpio_init(SSD1680_CS);   gpio_set_dir(SSD1680_CS,   GPIO_OUT); gpio_put(SSD1680_CS, 1);
    gpio_init(SSD1680_DC);   gpio_set_dir(SSD1680_DC,   GPIO_OUT); gpio_put(SSD1680_DC, 0);
    gpio_init(SSD1680_RST);  gpio_set_dir(SSD1680_RST,  GPIO_OUT); gpio_put(SSD1680_RST, 1);
    gpio_init(SSD1680_BUSY); gpio_set_dir(SSD1680_BUSY, GPIO_IN);

    gpio_put(SSD1680_RST, 0); sleep_ms(10); gpio_put(SSD1680_RST, 1); sleep_ms(10);
    ssd1680_wait_busy();

    ssd1680_cmd(0x12); ssd1680_wait_busy();
    ssd1680_cmd(0x01); ssd1680_data8(0x79); ssd1680_data8(0x00); ssd1680_data8(0x00);
    ssd1680_cmd(0x11); ssd1680_data8(0x03);
    ssd1680_cmd(0x44); ssd1680_data8(0); ssd1680_data8(0x0F);
    ssd1680_cmd(0x45); ssd1680_data8(0); ssd1680_data8(0); ssd1680_data8(0x79); ssd1680_data8(0x00);
    ssd1680_cmd(0x3C); ssd1680_data8(0x05);
    ssd1680_cmd(0x18); ssd1680_data8(0x80);
    ssd1680_cmd(0x22); ssd1680_data8(0xB1);
    ssd1680_cmd(0x20); ssd1680_wait_busy();

    memset(s_fb, 0xFF, sizeof(s_fb));
    ssd1680_write_ram();
    ssd1680_full_refresh();
}

void display_clear(void) {
    memset(s_fb, 0xFF, sizeof(s_fb));
    ssd1680_write_ram();
    ssd1680_full_refresh();
}

void display_text(const char *text, const char *effect, int speed_ms) {
    int sp = clamp_speed(speed_ms);
    int text_px = (int)strlen(text) * (FONT_CHAR_WIDTH + 1);

    if (strcmp(effect, "BLINK") == 0) {
        render_text_to_fb(text, 0); ssd1680_write_ram(); ssd1680_full_refresh();
        sleep_ms((uint32_t)sp);
        memset(s_fb, 0xFF, sizeof(s_fb)); ssd1680_write_ram(); ssd1680_full_refresh();
        sleep_ms((uint32_t)sp);
        render_text_to_fb(text, 0); ssd1680_write_ram(); ssd1680_full_refresh();
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        render_text_to_fb(rev, 0); ssd1680_write_ram(); ssd1680_full_refresh();
    } else if (strcmp(effect, "FADE") == 0) {
        memset(s_fb, 0xFF, sizeof(s_fb)); ssd1680_write_ram(); ssd1680_full_refresh();
        sleep_ms((uint32_t)sp);
        render_text_to_fb(text, 0); ssd1680_write_ram(); ssd1680_full_refresh();
    } else if (strcmp(effect, "SCROLL") == 0) {
        for (int offset = 0; offset <= text_px; offset += 8) {
            render_text_to_fb(text, offset); ssd1680_write_ram();
#ifdef EPAPER_PARTIAL_REFRESH
            ssd1680_partial_refresh();
#else
            ssd1680_full_refresh();
#endif
            sleep_ms((uint32_t)sp);
        }
    }
}

#endif
