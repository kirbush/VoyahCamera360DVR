#include "avm_protocol.h"
unsigned char packet[10];
int build_packet(int view) { return avm_packet(view, packet); }
unsigned long packet_address(void) { return (unsigned long)packet; }
