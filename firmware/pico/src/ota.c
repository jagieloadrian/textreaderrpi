#include "ota.h"
#include "picowota/reboot.h"

void trigger_ota(void) {
    picowota_reboot(true);
}
