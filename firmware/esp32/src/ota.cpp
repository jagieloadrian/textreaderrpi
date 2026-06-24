#include "ota.h"
#include "../../config.h"
#include <ArduinoOTA.h>

void setup_ota(void) {
    ArduinoOTA.setHostname(ZONE_ID);
    ArduinoOTA.begin();
}

void trigger_ota(void) {
    ArduinoOTA.begin();
}
