#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "avm_protocol.h"

int main(int argc, char **argv) {
    int view = argc == 2 && strcmp(argv[1], "front") == 0 ? 1 :
               argc == 2 && strcmp(argv[1], "rear") == 0 ? 2 : 0;
    unsigned char packet[10];
    if (!avm_packet(view, packet)) {
        fputs("Only front or rear view is supported\n", stderr);
        return 64;
    }
    if (geteuid() != 0) {
        fputs("Existing root access is required for AVM control\n", stderr);
        return 77;
    }
    void *library = dlopen("/system/lib64/libqg_hal.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) { fprintf(stderr, "AVM library: %s\n", dlerror()); return 70; }
    int (*open_can)(void) = (int (*)(void))dlsym(library, "qg_canbus_open");
    int (*control_can)(int, char *) = (int (*)(int, char *))dlsym(library, "qg_canbus_control");
    void (*close_can)(void) = (void (*)(void))dlsym(library, "qg_canbus_close");
    if (!open_can || !control_can || !close_can) {
        fputs("Required AVM library interface is unavailable\n", stderr);
        dlclose(library); return 70;
    }
    if (open_can() < 0) {
        fputs("Could not open AVM control interface\n", stderr);
        dlclose(library); return 74;
    }
    int result = control_can(1, (char *)packet);
    close_can();
    dlclose(library);
    if (result < 0) { fprintf(stderr, "AVM control failed: %d\n", result); return 74; }
    printf("AVM_VIEW_OK=%d\n", view);
    return 0;
}
