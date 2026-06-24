#include "json_parser.h"
#include "ota.h"
#include "driver/display.h"
#include "cJSON.h"
#include <string.h>

void parse_message(const char *data) {
    cJSON *root = cJSON_Parse(data);
    if (!root) return;

    cJSON *cmd = cJSON_GetObjectItemCaseSensitive(root, "command");
    if (cJSON_IsString(cmd) && strcmp(cmd->valuestring, "ota") == 0) {
        cJSON_Delete(root);
        ota_trigger();
        return;
    }

    cJSON *text_j   = cJSON_GetObjectItemCaseSensitive(root, "text");
    cJSON *effect_j = cJSON_GetObjectItemCaseSensitive(root, "effect");
    cJSON *speed_j  = cJSON_GetObjectItemCaseSensitive(root, "speed");
    cJSON *blink_j  = cJSON_GetObjectItemCaseSensitive(root, "blinkPeriod");
    cJSON *fade_j   = cJSON_GetObjectItemCaseSensitive(root, "fadeSteps");

    const char *text   = cJSON_IsString(text_j)   ? text_j->valuestring   : "";
    const char *effect = cJSON_IsString(effect_j)  ? effect_j->valuestring : "SCROLL";
    int speed          = cJSON_IsNumber(speed_j)   ? (int)speed_j->valuedouble  : 50;
    int blink_period   = cJSON_IsNumber(blink_j)   ? (int)blink_j->valuedouble  : 500;
    int fade_steps     = cJSON_IsNumber(fade_j)    ? (int)fade_j->valuedouble   : 8;

    display_text(text, effect, speed, blink_period, fade_steps);
    cJSON_Delete(root);
}
