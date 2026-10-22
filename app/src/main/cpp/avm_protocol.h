#ifndef VOYAH_AVM_PROTOCOL_H
#define VOYAH_AVM_PROTOCOL_H
/* Verified EventManager::SendViewSelect from the tested H97X firmware.
 * A fixed view request, not an API for arbitrary CAN traffic.
 * 1 = 2D front, 2 = 2D rear. */
static inline int avm_packet(int view, unsigned char packet[10]) {
    if (view != 1 && view != 2) return 0;
    for (int i = 0; i < 10; ++i) packet[i] = 0;
    packet[0] = 0x1c;
    packet[1] = 8;
    packet[6] = (unsigned char)(view << 1);
    return 10;
}
#endif
