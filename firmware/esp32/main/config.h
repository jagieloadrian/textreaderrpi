#ifndef CONFIG_H
#define CONFIG_H

#define WIFI_SSID              ""
#define WIFI_PASS              ""
#define SERVER_HOST            ""
#define SERVER_PORT            8080
#define ZONE_ID                "esp32"
#define NUM_DEVICES            4
#define RECONNECT_INTERVAL_MS  5000

#if defined(CONFIG_IDF_TARGET_ESP32S2) || defined(CONFIG_IDF_TARGET_ESP32S3)
/* GPIO19/20 reserved for USB on S2/S3 */
#define SPI_MOSI_PIN  35
#define SPI_CLK_PIN   36
#define SPI_CS_PIN    34
#define SPI_DC_PIN    37
#define SPI_RST_PIN   38
#define SPI_BUSY_PIN  33
#define I2C_SDA_PIN   8
#define I2C_SCL_PIN   9
#elif defined(CONFIG_IDF_TARGET_ESP32C2) || defined(CONFIG_IDF_TARGET_ESP32C3) || \
      defined(CONFIG_IDF_TARGET_ESP32C6) || defined(CONFIG_IDF_TARGET_ESP32H2)
/* RISC-V chips — fewer GPIOs */
#define SPI_MOSI_PIN  7
#define SPI_CLK_PIN   6
#define SPI_CS_PIN    10
#define SPI_DC_PIN    3
#define SPI_RST_PIN   4
#define SPI_BUSY_PIN  5
#define I2C_SDA_PIN   8
#define I2C_SCL_PIN   9
#else
/* ESP32 classic (default) */
#define SPI_MOSI_PIN  23
#define SPI_CLK_PIN   18
#define SPI_CS_PIN    5
#define SPI_DC_PIN    2
#define SPI_RST_PIN   4
#define SPI_BUSY_PIN  15
#define I2C_SDA_PIN   21
#define I2C_SCL_PIN   22
#endif

#endif
