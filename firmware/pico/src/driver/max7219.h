#ifndef MAX7219_H
#define MAX7219_H

#include "display.h"

void max7219_send(uint8_t reg, uint8_t data, int device_index);
void max7219_send_all(uint8_t reg, uint8_t data);

#endif
