#include "captive_portal.h"
#include <WiFiManager.h>
#include <Arduino.h>

void setup_wifi(void) {
    WiFiManager wm;
    wm.setTitle("TextReader Setup");

    bool connected = wm.autoConnect("TextReader-Setup");
    if (!connected) {
        ESP.restart();
    }
}
