#ifdef DISPLAY_DRIVER_SSD1327

#include "ssd1327.h"
#include "display.h"
#include "font/font5x8.h"
#include "driver/i2c.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "config.h"
#include <string.h>
#include <stdlib.h>

#define SSD1327_I2C     I2C_NUM_0
#define SSD1327_ADDR    0x3C
#define SSD1327_WIDTH   128
#define SSD1327_HEIGHT  128
#define SSD1327_STRIDE  (SSD1327_WIDTH / 2)

static uint8_t s_fb[SSD1327_HEIGHT * SSD1327_STRIDE];

static void ssd1327_write(const uint8_t *buf, size_t len) {
    i2c_master_write_to_device(SSD1327_I2C, SSD1327_ADDR, buf, len, pdMS_TO_TICKS(100));
}

static void ssd1327_cmd(uint8_t cmd) {
    uint8_t buf[2] = {0x00, cmd};
    ssd1327_write(buf, 2);
}

static void ssd1327_flush(void) {
    ssd1327_cmd(0x15); ssd1327_cmd(0); ssd1327_cmd(63);
    ssd1327_cmd(0x75); ssd1327_cmd(0); ssd1327_cmd(127);
    uint8_t buf[SSD1327_STRIDE + 1];
    buf[0] = 0x40;
    for (int row = 0; row < SSD1327_HEIGHT; row++) {
        memcpy(buf + 1, &s_fb[row * SSD1327_STRIDE], SSD1327_STRIDE);
        ssd1327_write(buf, sizeof(buf));
    }
}

static int clamp_speed(int s) { return s < 1 ? 1 : s > 5000 ? 5000 : s; }

static void render_text_to_fb(const char *text, uint8_t brightness) {
    memset(s_fb, 0, sizeof(s_fb));
    int x = 0;
    for (int i = 0; text[i] != '\0'; i++) {
        unsigned char c = (unsigned char)text[i];
        int fi = c - 0x20;
        if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
        for (int col = 0; col < FONT_CHAR_WIDTH && x < SSD1327_WIDTH; col++, x++) {
            uint8_t column_byte = font5x8[fi][col];
            for (int row = 0; row < 8; row++) {
                if ((column_byte >> row) & 1) {
                    int byte_idx = row * SSD1327_STRIDE + x / 2;
                    if (x % 2 == 0)
                        s_fb[byte_idx] = (s_fb[byte_idx] & 0x0F) | (brightness << 4);
                    else
                        s_fb[byte_idx] = (s_fb[byte_idx] & 0xF0) | (brightness & 0x0F);
                }
            }
        }
        x++;
        if (x >= SSD1327_WIDTH) break;
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
    i2c_param_config(SSD1327_I2C, &conf);
    i2c_driver_install(SSD1327_I2C, conf.mode, 0, 0, 0);
    vTaskDelay(pdMS_TO_TICKS(100));

    ssd1327_cmd(0xAE);
    ssd1327_cmd(0xA0); ssd1327_cmd(0x53);
    ssd1327_cmd(0xA1); ssd1327_cmd(0x00);
    ssd1327_cmd(0xA2); ssd1327_cmd(0x00);
    ssd1327_cmd(0xA4);
    ssd1327_cmd(0xA8); ssd1327_cmd(0x7F);
    ssd1327_cmd(0xB1); ssd1327_cmd(0x11);
    ssd1327_cmd(0xB3); ssd1327_cmd(0x00);
    ssd1327_cmd(0xAB); ssd1327_cmd(0x01);
    ssd1327_cmd(0xB6); ssd1327_cmd(0x04);
    ssd1327_cmd(0xBE); ssd1327_cmd(0x0F);
    ssd1327_cmd(0xBC); ssd1327_cmd(0x08);
    ssd1327_cmd(0xD5); ssd1327_cmd(0x62);
    ssd1327_cmd(0xFD); ssd1327_cmd(0x12);
    ssd1327_cmd(0xAF);
    memset(s_fb, 0, sizeof(s_fb));
}

void display_clear(void) {
    memset(s_fb, 0, sizeof(s_fb));
    ssd1327_flush();
}

void display_text(const char *text, const char *effect, int speed_ms, int blink_period_ms, int fade_steps) {
    int sp = clamp_speed(speed_ms);

    if (strcmp(effect, "SCROLL") == 0) {
        int text_px = (int)strlen(text) * (FONT_CHAR_WIDTH + 1);
        for (int offset = 0; offset < text_px; offset++) {
            render_text_to_fb(text, offset);
            ssd1327_flush();
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    } else if (strcmp(effect, "BLINK") == 0) {
        render_text_to_fb(text, 0x0F);
        ssd1327_flush();
        for (int i = 0; i < 6; i++) {
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp)); ssd1327_cmd(0xAE);
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp)); ssd1327_cmd(0xAF);
        }
    } else if (strcmp(effect, "REVERSE") == 0) {
        int len = (int)strlen(text);
        char rev[128];
        int cl = len < 127 ? len : 127;
        for (int i = 0; i < cl; i++) rev[i] = text[cl - 1 - i];
        rev[cl] = '\0';
        render_text_to_fb(rev, 0x0F);
        ssd1327_flush();
        vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
    } else if (strcmp(effect, "FADE") == 0) {
        render_text_to_fb(text, 0x0F);
        ssd1327_flush();
        int step_delay = sp / 32 + 1;
        for (int v = 0; v <= 0x0F; v++) {
            ssd1327_cmd(0x81); ssd1327_cmd((uint8_t)(v * 16));
            vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        for (int v = 0x0F; v >= 0; v--) {
            ssd1327_cmd(0x81); ssd1327_cmd((uint8_t)(v * 16));
            vTaskDelay(pdMS_TO_TICKS((uint32_t)step_delay));
        }
        ssd1327_cmd(0x81); ssd1327_cmd(0x80);
    } else {
        int text_px = (int)strlen(text) * (FONT_CHAR_WIDTH + 1);
        for (int offset = 0; offset < text_px; offset++) {
            memset(s_fb, 0, sizeof(s_fb));
            int x = -offset;
            for (int i = 0; text[i] != '\0'; i++) {
                unsigned char c = (unsigned char)text[i];
                int fi = c - 0x20;
                if (fi < 0 || fi >= FONT_TABLE_SIZE) fi = 0;
                for (int col = 0; col < FONT_CHAR_WIDTH; col++, x++) {
                    if (x < 0 || x >= SSD1327_WIDTH) continue;
                    uint8_t column_byte = font5x8[fi][col];
                    for (int row = 0; row < 8; row++) {
                        if ((column_byte >> row) & 1) {
                            int byte_idx = row * SSD1327_STRIDE + x / 2;
                            if (x % 2 == 0) s_fb[byte_idx] = (s_fb[byte_idx] & 0x0F) | 0xF0;
                            else            s_fb[byte_idx] = (s_fb[byte_idx] & 0xF0) | 0x0F;
                        }
                    }
                }
                x++;
            }
            ssd1327_flush();
            vTaskDelay(pdMS_TO_TICKS((uint32_t)sp));
        }
    }
}

#endif
