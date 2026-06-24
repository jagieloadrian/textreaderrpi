#ifdef DISPLAY_DRIVER_HT16K33

#include "ht16k33.h"
#include "display.h"
#include "font/font5x8.h"
#include "driver/i2c.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "config.h"
#include <string.h>
#include <stdlib.h>

#define HT16K33_I2C     I2C_NUM_0
#define HT16K33_ADDR    0x70
#define HT16K33_ROWS    8
#define HT16K33_COLS    8

static uint8_t s_rows[HT16K33_ROWS];

static void ht16k33_write(const uint8_t *buf, size_t len) {
    i2c_master_write_to_device(HT16K33_I2C, HT16K33_ADDR, buf, len, pdMS_TO_TICKS(100));
}

static void ht16k33_write_cmd(uint8_t cmd) {
    ht16k33_write(&cmd, 1);
}

static void ht16k33_flush(void) {
    uint8_t buf[17];
    buf[0] = 0x00;
    for (int i = 0; i < HT16K33_ROWS; i++) {
        buf[1 + i * 2]     = s_rows[i];
        buf[1 + i * 2 + 1] = 0x00;
    }
    ht16k33_write(buf, sizeof(buf));
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
    i2c_config_t conf = {
        .mode             = I2C_MODE_MASTER,
        .sda_io_num       = I2C_SDA_PIN,
        .scl_io_num       = I2C_SCL_PIN,
        .sda_pullup_en    = GPIO_PULLUP_ENABLE,
        .scl_pullup_en    = GPIO_PULLUP_ENABLE,
        .master.clk_speed = 400000,
    };
    i2c_param_config(HT16K33_I2C, &conf);
    i2c_driver_install(HT16K33_I2C, conf.mode, 0, 0, 0);
    vTaskDelay(pdMS_TO_TICKS(10));

    ht16k33_write_cmd(0x21);
    ht16k33_write_cmd(0x81);
    ht16k33_write_cmd(0xEF);
    memset(s_rows, 0, sizeof(s_rows));
}

void display_clear(void) {
    memset(s_rows, 0, sizeof(s_rows));
    ht16k33_flush();
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);

    if (strcmp(effect, "SCROLL") == 0) {
        for (int i = 0; text[i] != '\0'; i++) {
            render_char((unsigned char)text[i]);
            ht16k33_flush();
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
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
        vTaskDelay(pdMS_TO_TICKS((uint32_t)(sp * 6)));
        ht16k33_write_cmd(0x81);
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        for (int i = len - 1; i >= 0; i--) {
            render_char((unsigned char)text[i]);
            ht16k33_flush();
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    } else if (strcmp(effect, "FADE") == 0) {
        render_char((unsigned char)text[0]);
        ht16k33_flush();
        int step_delay = sp / 16 + 1;
        for (int v = 0; v <= 15; v++) {
            ht16k33_write_cmd((uint8_t)(0xE0 | v));
            vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        for (int v = 15; v >= 0; v--) {
            ht16k33_write_cmd((uint8_t)(0xE0 | v));
            vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        ht16k33_write_cmd(0xEF);
    }
}

#endif
