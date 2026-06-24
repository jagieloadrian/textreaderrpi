cmake_minimum_required(VERSION 3.13)

if (NOT DEFINED PICO_BOARD)
    set(PICO_BOARD pico_w)
endif()

include(pico_sdk_import.cmake)

project(textreader C CXX ASM)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

pico_sdk_init()

include(FetchContent)
FetchContent_Declare(cjson
    GIT_REPOSITORY https://github.com/DaveGamble/cJSON.git
    GIT_TAG        v1.7.19
    GIT_SHALLOW    ON
)
set(ENABLE_CJSON_TEST OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(cjson)

add_subdirectory(picowota)

add_executable(textreader
    src/main.c
    src/ws_client.c
    src/json_parser.c
    src/captive_portal.c
    src/ota.c
    lib/mongoose/mongoose.c
)

if(NOT DEFINED DISPLAY_DRIVER)
    set(DISPLAY_DRIVER "MAX7219")
endif()

if(DISPLAY_DRIVER STREQUAL "SSD1306")
    target_sources(textreader PRIVATE src/driver/ssd1306.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_SSD1306=1)
    target_link_libraries(textreader hardware_i2c)
elseif(DISPLAY_DRIVER STREQUAL "SSD1309")
    target_sources(textreader PRIVATE src/driver/ssd1309.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_SSD1309=1)
    target_link_libraries(textreader hardware_i2c)
elseif(DISPLAY_DRIVER STREQUAL "SSD1327")
    target_sources(textreader PRIVATE src/driver/ssd1327.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_SSD1327=1)
    target_link_libraries(textreader hardware_i2c)
elseif(DISPLAY_DRIVER STREQUAL "SH1106")
    target_sources(textreader PRIVATE src/driver/sh1106.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_SH1106=1)
    target_link_libraries(textreader hardware_spi hardware_gpio)
elseif(DISPLAY_DRIVER STREQUAL "HT16K33")
    target_sources(textreader PRIVATE src/driver/ht16k33.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_HT16K33=1)
    target_link_libraries(textreader hardware_i2c)
elseif(DISPLAY_DRIVER STREQUAL "ST7735")
    target_sources(textreader PRIVATE src/driver/st7735.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_ST7735=1)
    target_link_libraries(textreader hardware_spi hardware_gpio)
elseif(DISPLAY_DRIVER STREQUAL "ST7789")
    target_sources(textreader PRIVATE src/driver/st7789.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_ST7789=1)
    target_link_libraries(textreader hardware_spi hardware_gpio)
elseif(DISPLAY_DRIVER STREQUAL "ILI9225")
    target_sources(textreader PRIVATE src/driver/ili9225.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_ILI9225=1)
    target_link_libraries(textreader hardware_spi hardware_gpio)
elseif(DISPLAY_DRIVER STREQUAL "PCD8544")
    target_sources(textreader PRIVATE src/driver/pcd8544.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_PCD8544=1)
    target_link_libraries(textreader hardware_spi hardware_gpio)
elseif(DISPLAY_DRIVER STREQUAL "SSD1680")
    target_sources(textreader PRIVATE src/driver/ssd1680.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_SSD1680=1)
    target_link_libraries(textreader hardware_spi hardware_gpio)
else()
    target_sources(textreader PRIVATE src/driver/max7219.c)
    target_compile_definitions(textreader PRIVATE DISPLAY_DRIVER_MAX7219=1)
    target_link_libraries(textreader hardware_spi)
endif()

target_include_directories(textreader PRIVATE
    src
    lib/mongoose
    font
    ${cjson_SOURCE_DIR}
)

target_link_libraries(textreader
    pico_stdlib
    pico_cyw43_arch_lwip_threadsafe_background
    cjson
    picowota_reboot
)

pico_enable_stdio_usb(textreader 1)
pico_enable_stdio_uart(textreader 0)
pico_add_extra_outputs(textreader)
picowota_build_combined(textreader)
