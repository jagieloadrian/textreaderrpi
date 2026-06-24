#include <Arduino.h>
#include <ArduinoOTA.h>
#include "captive_portal.h"
#include "ota.h"
#include "ws_client.h"
#include "driver/display.h"

void setup() {
    Serial.begin(115200);
    setup_wifi();
    display_init();
    setup_ota();
    setup_ws();
}

void loop() {
    ArduinoOTA.handle();
    ws_loop();
}
