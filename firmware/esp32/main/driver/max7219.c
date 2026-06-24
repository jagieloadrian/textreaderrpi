#include "max7219.h"
#include "font/font5x8.h"
#include "config.h"
#include "driver/spi_master.h"
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>
#include <stdlib.h>

#define REG_NOOP         0x00
#define REG_DECODE_MODE  0x09
#define REG_INTENSITY    0x0A
#define REG_SCAN_LIMIT   0x0B
#define REG_SHUTDOWN     0x0C
#define REG_DISPLAY_TEST 0x0F
#define REG_ROW_BASE     0x01

#define MAX_BITMAP_LEN  (256 + NUM_DEVICES * 8)

static spi_device_handle_t s_spi;
static uint8_t s_bitmap[MAX_BITMAP_LEN];
static int     s_bitmap_len;

static void spi_write_reg(uint8_t reg, uint8_t val) {
    uint8_t buf[NUM_DEVICES * 2];
    for (int i = 0; i < NUM_DEVICES; i++) {
        buf[i * 2]     = reg;
        buf[i * 2 + 1] = val;
    }
    spi_transaction_t t = {
        .length    = (size_t)(NUM_DEVICES * 16),
        .tx_buffer = buf,
    };
    spi_device_polling_transmit(s_spi, &t);
}

void display_init(void) {
    spi_bus_config_t bus = {
        .mosi_io_num   = SPI_MOSI_PIN,
        .miso_io_num   = -1,
        .sclk_io_num   = SPI_CLK_PIN,
        .quadwp_io_num = -1,
        .quadhd_io_num = -1,
    };
    spi_bus_initialize(SPI2_HOST, &bus, SPI_DMA_CH_AUTO);

    spi_device_interface_config_t dev = {
        .clock_speed_hz = 10 * 1000 * 1000,
        .mode           = 0,
        .spics_io_num   = SPI_CS_PIN,
        .queue_size     = 4,
    };
    spi_bus_add_device(SPI2_HOST, &dev, &s_spi);

    spi_write_reg(REG_DISPLAY_TEST, 0x00);
    spi_write_reg(REG_DECODE_MODE,  0x00);
    spi_write_reg(REG_SCAN_LIMIT,   0x07);
    spi_write_reg(REG_INTENSITY,    0x04);
    spi_write_reg(REG_SHUTDOWN,     0x01);
}

void display_clear(void) {
    for (int row = 0; row < 8; row++) {
        spi_write_reg(REG_ROW_BASE + row, 0x00);
    }
}

static int build_text_bitmap(const char *text) {
    int idx = 0;
    for (int i = 0; text[i] != '\0' && idx < MAX_BITMAP_LEN - (NUM_DEVICES * 8 + 1); i++) {
        unsigned char c  = (unsigned char)text[i];
        int           fi = c - 0x20;
        if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
        for (int col = 0; col < FONT_CHAR_WIDTH; col++) {
            s_bitmap[idx++] = font5x8[fi][col];
        }
        s_bitmap[idx++] = 0x00;
    }
    int trailing = NUM_DEVICES * 8;
    for (int i = 0; i < trailing && idx < MAX_BITMAP_LEN; i++) {
        s_bitmap[idx++] = 0x00;
    }
    return idx;
}

static void render_columns(int offset) {
    for (int row = 0; row < 8; row++) {
        uint8_t buf[NUM_DEVICES * 2];
        for (int d = 0; d < NUM_DEVICES; d++) {
            int     col_idx = offset + (NUM_DEVICES - 1 - d) * 8 + row;
            uint8_t b       = (col_idx < s_bitmap_len) ? s_bitmap[col_idx] : 0;
            buf[d * 2]     = REG_ROW_BASE + row;
            buf[d * 2 + 1] = b;
        }
        spi_transaction_t t = {
            .length    = (size_t)(NUM_DEVICES * 16),
            .tx_buffer = buf,
        };
        spi_device_polling_transmit(s_spi, &t);
    }
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
            vTaskDelay(pdMS_TO_TICKS((uint32_t)speed_ms));
            offset++;
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        render_columns(0);
        for (int i = 0; i < 6; i++) {
            vTaskDelay(pdMS_TO_TICKS((uint32_t)blink_period_ms));
            spi_write_reg(REG_SHUTDOWN, 0x00);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)blink_period_ms));
            spi_write_reg(REG_SHUTDOWN, 0x01);
            render_columns(0);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        int rev_offset = (s_bitmap_len > display_w) ? s_bitmap_len - display_w : 0;
        while (rev_offset >= 0) {
            render_columns(rev_offset);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)speed_ms));
            rev_offset--;
        }
    } else if (strcmp(effect, "FADE") == 0) {
        s_bitmap_len = build_text_bitmap(text);
        render_columns(0);
        int div = (fade_steps > 1) ? fade_steps - 1 : 1;
        for (int step = 0; step < fade_steps; step++) {
            spi_write_reg(REG_INTENSITY, (uint8_t)((step * 15) / div));
            vTaskDelay(pdMS_TO_TICKS((uint32_t)(speed_ms / fade_steps)));
        }
        for (int step = fade_steps - 1; step >= 0; step--) {
            spi_write_reg(REG_INTENSITY, (uint8_t)((step * 15) / div));
            vTaskDelay(pdMS_TO_TICKS((uint32_t)(speed_ms / fade_steps)));
        }
        spi_write_reg(REG_INTENSITY, 0x04);
    } else {
        s_bitmap_len = build_text_bitmap(text);
        render_columns(0);
        vTaskDelay(pdMS_TO_TICKS(2000));
    }
}
