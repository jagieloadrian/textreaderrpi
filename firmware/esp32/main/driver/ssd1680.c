#ifdef DISPLAY_DRIVER_SSD1680

#include "ssd1680.h"
#include "display.h"
#include "font/font5x8.h"
#include "driver/spi_master.h"
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "config.h"
#include <string.h>
#include <stdlib.h>

#define SSD1680_WIDTH   250
#define SSD1680_HEIGHT  122
#define SSD1680_STRIDE  ((SSD1680_WIDTH + 7) / 8)

static spi_device_handle_t s_spi;
static uint8_t s_fb[SSD1680_HEIGHT * SSD1680_STRIDE];

static void ssd1680_wait_busy(void) {
    while (gpio_get_level(SPI_BUSY_PIN)) vTaskDelay(pdMS_TO_TICKS(10));
}

static void ssd1680_cmd(uint8_t cmd) {
    gpio_set_level(SPI_DC_PIN, 0);
    spi_transaction_t t = { .length = 8, .tx_buffer = &cmd };
    spi_device_polling_transmit(s_spi, &t);
}

static void ssd1680_data(const uint8_t *buf, size_t len) {
    gpio_set_level(SPI_DC_PIN, 1);
    spi_transaction_t t = { .length = len * 8, .tx_buffer = buf };
    spi_device_polling_transmit(s_spi, &t);
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
    gpio_config_t out_io = {
        .pin_bit_mask = (1ULL << SPI_DC_PIN) | (1ULL << SPI_RST_PIN),
        .mode         = GPIO_MODE_OUTPUT,
    };
    gpio_config(&out_io);

    gpio_config_t in_io = {
        .pin_bit_mask = (1ULL << SPI_BUSY_PIN),
        .mode         = GPIO_MODE_INPUT,
    };
    gpio_config(&in_io);

    gpio_set_level(SPI_DC_PIN, 0);
    gpio_set_level(SPI_RST_PIN, 1);

    spi_bus_config_t bus = {
        .mosi_io_num   = SPI_MOSI_PIN,
        .miso_io_num   = -1,
        .sclk_io_num   = SPI_CLK_PIN,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
    };
    spi_bus_initialize(SPI2_HOST, &bus, SPI_DMA_CH_AUTO);

    spi_device_interface_config_t dev = {
        .clock_speed_hz = 4000000,
        .mode           = 0,
        .spics_io_num   = SPI_CS_PIN,
        .queue_size     = 4,
    };
    spi_bus_add_device(SPI2_HOST, &dev, &s_spi);

    gpio_set_level(SPI_RST_PIN, 0); vTaskDelay(pdMS_TO_TICKS(10));
    gpio_set_level(SPI_RST_PIN, 1); vTaskDelay(pdMS_TO_TICKS(10));
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

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);
    int text_px = (int)strlen(text) * (FONT_CHAR_WIDTH + 1);

    if (strcmp(effect, "BLINK") == 0) {
        render_text_to_fb(text, 0); ssd1680_write_ram(); ssd1680_full_refresh();
        vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        memset(s_fb, 0xFF, sizeof(s_fb)); ssd1680_write_ram(); ssd1680_full_refresh();
        vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        render_text_to_fb(text, 0); ssd1680_write_ram(); ssd1680_full_refresh();
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        render_text_to_fb(rev, 0); ssd1680_write_ram(); ssd1680_full_refresh();
    } else if (strcmp(effect, "FADE") == 0) {
        memset(s_fb, 0xFF, sizeof(s_fb)); ssd1680_write_ram(); ssd1680_full_refresh();
        vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        render_text_to_fb(text, 0); ssd1680_write_ram(); ssd1680_full_refresh();
    } else if (strcmp(effect, "SCROLL") == 0) {
        for (int offset = 0; offset <= text_px; offset += 8) {
            render_text_to_fb(text, offset); ssd1680_write_ram();
#ifdef EPAPER_PARTIAL_REFRESH
            ssd1680_partial_refresh();
#else
            ssd1680_full_refresh();
#endif
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    }
}

#endif
