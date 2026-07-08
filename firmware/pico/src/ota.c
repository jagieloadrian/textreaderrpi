#include "ota.h"

#ifdef PICOWOTA_AVAILABLE
#include "picowota/reboot.h"

void ota_trigger(void) {
    picowota_reboot(true);
}
#else
#include "hardware/watchdog.h"

void ota_trigger(void) {
    // ponytail: picowota not vendored, plain reboot instead of OTA bootloader entry
    watchdog_reboot(0, 0, 10);
}
#endif
