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
    src/driver/max7219.c
    lib/mongoose/mongoose.c
)

target_compile_definitions(textreader PRIVATE
    DISPLAY_DRIVER_MAX7219=1
)

target_include_directories(textreader PRIVATE
    src
    lib/mongoose
    font
    ${cjson_SOURCE_DIR}
)

target_link_libraries(textreader
    pico_stdlib
    pico_cyw43_arch_lwip_threadsafe_background
    hardware_spi
    cjson
    picowota_reboot
)

pico_enable_stdio_usb(textreader 1)
pico_enable_stdio_uart(textreader 0)
pico_add_extra_outputs(textreader)
picowota_build_combined(textreader)
