#include "ota.h"
#include "picowota/reboot.h"

void ota_trigger(void) {
    picowota_reboot(true);
}
