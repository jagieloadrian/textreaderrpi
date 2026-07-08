#ifdef DISPLAY_DRIVER_ST7735

#include "st7735.h"
#include "display.h"
#include "font/font5x8.h"
#include "driver/spi_master.h"
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "config.h"
#include <string.h>
#include <stdlib.h>

#define ST7735_WIDTH    128
#define ST7735_HEIGHT   160

#define ST7735_SWRESET  0x01
#define ST7735_SLPOUT   0x11
#define ST7735_COLMOD   0x3A
#define ST7735_MADCTL   0x36
#define ST7735_CASET    0x2A
#define ST7735_RASET    0x2B
#define ST7735_RAMWR    0x2C
#define ST7735_DISPON   0x29

static spi_device_handle_t s_spi;

static void st7735_cmd(uint8_t cmd) {
    gpio_set_level(SPI_DC_PIN, 0);
    spi_transaction_t t = { .length = 8, .tx_buffer = &cmd };
    spi_device_polling_transmit(s_spi, &t);
}

static void st7735_data(const uint8_t *buf, size_t len) {
    gpio_set_level(SPI_DC_PIN, 1);
    spi_transaction_t t = { .length = len * 8, .tx_buffer = buf };
    spi_device_polling_transmit(s_spi, &t);
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

static void fill_rect(uint8_t x, uint8_t y, uint8_t w, uint8_t h, uint16_t color) {
    st7735_set_window(x, y, (uint8_t)(x + w - 1), (uint8_t)(y + h - 1));
    for (int i = 0; i < w * h; i++) st7735_data16(color);
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

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render_text(const char *text, int x_offset, uint16_t fg, uint16_t bg) {
    fill_rect(0, 0, ST7735_WIDTH, 8, bg);
    int x = -x_offset;
    for (int i = 0; text[i] != '\0'; i++) {
        if (x >= ST7735_WIDTH) break;
        if (x + FONT_CHAR_WIDTH > 0)
            draw_char((uint8_t)(x < 0 ? 0 : x), 0, (unsigned char)text[i], fg, bg);
        x += FONT_CHAR_WIDTH + FONT_CHAR_GAP;
    }
}

void display_init(void) {
    gpio_config_t io = {
        .pin_bit_mask = (1ULL << SPI_DC_PIN) | (1ULL << SPI_RST_PIN),
        .mode         = GPIO_MODE_OUTPUT,
    };
    gpio_config(&io);
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
        .clock_speed_hz = 16000000,
        .mode           = 0,
        .spics_io_num   = SPI_CS_PIN,
        .queue_size     = 4,
    };
    spi_bus_add_device(SPI2_HOST, &dev, &s_spi);

    gpio_set_level(SPI_RST_PIN, 0); vTaskDelay(pdMS_TO_TICKS(10));
    gpio_set_level(SPI_RST_PIN, 1); vTaskDelay(pdMS_TO_TICKS(120));

    st7735_cmd(ST7735_SWRESET); vTaskDelay(pdMS_TO_TICKS(150));
    st7735_cmd(ST7735_SLPOUT);  vTaskDelay(pdMS_TO_TICKS(255));
    st7735_cmd(ST7735_COLMOD);  st7735_data8(0x05);
    st7735_cmd(ST7735_MADCTL);  st7735_data8(0x00);
    st7735_cmd(ST7735_DISPON);
    fill_rect(0, 0, ST7735_WIDTH, ST7735_HEIGHT, 0x0000);
}

void display_clear(void) {
    fill_rect(0, 0, ST7735_WIDTH, ST7735_HEIGHT, 0x0000);
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);
    int text_px = (int)strlen(text) * (FONT_CHAR_WIDTH + FONT_CHAR_GAP);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int offset = 0; offset <= text_px; offset++) {
            render_text(text, offset, 0xFFFF, 0x0000);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render_text(text, 0, 0xFFFF, 0x0000);
        for (int i = 0; i < 6; i++) {
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp)); fill_rect(0, 0, ST7735_WIDTH, 8, 0x0000);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp)); render_text(text, 0, 0xFFFF, 0x0000);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        int rev_px = cl * (FONT_CHAR_WIDTH + FONT_CHAR_GAP);
        for (int offset = 0; offset <= rev_px; offset++) {
            render_text(rev, offset, 0xFFFF, 0x0000);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    } else if (strcmp(effect, "FADE") == 0) {
        render_text(text, 0, 0xFFFF, 0x0000);
        int step_delay = sp / 32 + 1;
        for (int v = 0; v <= 31; v++) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render_text(text, 0, c, 0x0000); vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        for (int v = 31; v >= 0; v--) {
            uint16_t c = (uint16_t)((v << 11) | (v * 2 << 5) | v);
            render_text(text, 0, c, 0x0000); vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        render_text(text, 0, 0xFFFF, 0x0000);
    }
}

#endif
