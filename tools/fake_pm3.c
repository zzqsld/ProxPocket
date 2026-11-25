// fake_pm3.c - pretend to be the old vendor proxmark3 client (for probing GUI checks)
// Replies to "hw ver" with canned text from D:\20260415MAX\fake_hwver.txt
#include <stdio.h>
#include <string.h>

int main(void) {
    char line[1024];
    while (fgets(line, sizeof(line), stdin)) {
        // echo prompt + command like the real script-mode client
        printf("[usb|script] pm3 --> %s", line);
        if (strstr(line, "hw ver") || strstr(line, "hw version")) {
            FILE *f = fopen("D:\\20260415MAX\\fake_hwver.txt", "rb");
            if (f) {
                char buf[8192];
                size_t n = fread(buf, 1, sizeof(buf) - 1, f);
                fclose(f);
                fwrite(buf, 1, n, stdout);
            }
        }
        printf("\n");
        fflush(stdout);
    }
    return 0;
}
