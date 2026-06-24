#ifdef DISPLAY_DRIVER_PCD8544

#include "pcd8544.h"
#include "display.h"
#include "font/font5x8.h"
#include "driver/spi_master.h"
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "config.h"
#include <string.h>
#include <stdlib.h>

#define PCD8544_WIDTH   84
#define PCD8544_ROWS    6

static spi_device_handle_t s_spi;
static uint8_t s_fb[PCD8544_WIDTH * PCD8544_ROWS];

static void pcd8544_write_byte(uint8_t byte, int is_data) {
    gpio_set_level(SPI_DC_PIN, is_data ? 1 : 0);
    spi_transaction_t t = { .length = 8, .tx_buffer = &byte };
    spi_device_polling_transmit(s_spi, &t);
}

static void pcd8544_flush(void) {
    pcd8544_write_byte(0x40, 0);
    pcd8544_write_byte(0x80, 0);
    for (int i = 0; i < PCD8544_WIDTH * PCD8544_ROWS; i++) pcd8544_write_byte(s_fb[i], 1);
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
    for (int i = 0; i < PCD8544_WIDTH && idx < max_bytes; i++) out[idx++] = 0x00;
    return idx;
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

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
        .clock_speed_hz = 4000000,
        .mode           = 0,
        .spics_io_num   = SPI_CS_PIN,
        .queue_size     = 4,
    };
    spi_bus_add_device(SPI2_HOST, &dev, &s_spi);

    gpio_set_level(SPI_RST_PIN, 0); vTaskDelay(pdMS_TO_TICKS(10));
    gpio_set_level(SPI_RST_PIN, 1);

    pcd8544_write_byte(0x21, 0);
    pcd8544_write_byte(0x13, 0);
    pcd8544_write_byte(0xBF, 0);
    pcd8544_write_byte(0x20, 0);
    pcd8544_write_byte(0x0C, 0);
    memset(s_fb, 0, sizeof(s_fb));
}

void display_clear(void) {
    memset(s_fb, 0, sizeof(s_fb));
    pcd8544_flush();
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);
    static uint8_t bmp[512];
    int bmp_len;

    if (strcmp(effect, "SCROLL") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - PCD8544_WIDTH; offset++) {
            memset(s_fb, 0, sizeof(s_fb));
            for (int col = 0; col < PCD8544_WIDTH; col++) {
                int bi = offset + col;
                s_fb[col] = (bi < bmp_len) ? bmp[bi] : 0x00;
            }
            pcd8544_flush();
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        memset(s_fb, 0, sizeof(s_fb));
        for (int col = 0; col < PCD8544_WIDTH; col++) s_fb[col] = (col < bmp_len) ? bmp[col] : 0x00;
        pcd8544_flush();
        for (int i = 0; i < 6; i++) {
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp)); pcd8544_write_byte(0x09, 0);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp)); pcd8544_write_byte(0x0C, 0);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128]; int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        bmp_len = build_bitmap(rev, bmp, sizeof(bmp));
        for (int offset = 0; offset <= bmp_len - PCD8544_WIDTH; offset++) {
            memset(s_fb, 0, sizeof(s_fb));
            for (int col = 0; col < PCD8544_WIDTH; col++) {
                int bi = offset + col;
                s_fb[col] = (bi < bmp_len) ? bmp[bi] : 0x00;
            }
            pcd8544_flush();
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    } else if (strcmp(effect, "FADE") == 0) {
        bmp_len = build_bitmap(text, bmp, sizeof(bmp));
        memset(s_fb, 0, sizeof(s_fb));
        for (int col = 0; col < PCD8544_WIDTH; col++) s_fb[col] = (col < bmp_len) ? bmp[col] : 0x00;
        pcd8544_flush();
        int step_delay = sp / 64 + 1;
        for (int v = 0x00; v <= 0x3F; v += 2) {
            pcd8544_write_byte(0x21, 0); pcd8544_write_byte((uint8_t)(0x80 | v), 0); pcd8544_write_byte(0x20, 0);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        for (int v = 0x3F; v >= 0x00; v -= 2) {
            pcd8544_write_byte(0x21, 0); pcd8544_write_byte((uint8_t)(0x80 | v), 0); pcd8544_write_byte(0x20, 0);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        pcd8544_write_byte(0x21, 0); pcd8544_write_byte(0xBF, 0); pcd8544_write_byte(0x20, 0);
    }
}

#endif
