#include "json_parser.h"
#include "ota.h"
#include "driver/display.h"
#include <ArduinoJson.h>
#include <string.h>

void parse_message(const char *data) {
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, data);
    if (err) return;

    const char *cmd = doc["command"] | "";
    if (strcmp(cmd, "ota") == 0) {
        trigger_ota();
        return;
    }

    const char *text   = doc["text"]        | "";
    const char *effect = doc["effect"]      | "SCROLL";
    int speed          = doc["speed"]       | 50;
    int blinkPeriod    = doc["blinkPeriod"] | 500;
    int fadeSteps      = doc["fadeSteps"]   | 8;

    int speed_ms;
    if (strcmp(effect, "BLINK") == 0) {
        speed_ms = blinkPeriod;
    } else if (strcmp(effect, "FADE") == 0) {
        speed_ms = fadeSteps * 20;
    } else {
        speed_ms = speed;
    }

    display_text(text, effect, speed_ms);
}
