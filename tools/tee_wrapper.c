// tee_wrapper.c - transparent proxy between PM3 GUI.exe and real proxmark3.exe
// Logs all GUI->PM3 (stdin) and PM3->GUI (stdout) traffic to gui_comm_log.txt
#include <windows.h>
#include <stdio.h>
#include <string.h>

static FILE *g_log;
static HANDLE g_childStdin, g_childStdout;
static CRITICAL_SECTION g_lock;

static void logchunk(const char *tag, const char *buf, DWORD n) {
    EnterCriticalSection(&g_lock);
    if (g_log) {
        fwrite(tag, 1, strlen(tag), g_log);
        fwrite(buf, 1, n, g_log);
        fwrite("\n", 1, 1, g_log);
        fflush(g_log);
    }
    LeaveCriticalSection(&g_lock);
}

DWORD WINAPI stdin_pump(LPVOID p) {
    (void)p;
    char buf[4096];
    DWORD n;
    HANDLE in = GetStdHandle(STD_INPUT_HANDLE);
    while (ReadFile(in, buf, sizeof(buf), &n, NULL) && n > 0) {
        DWORD w;
        WriteFile(g_childStdin, buf, n, &w, NULL);
        logchunk("[GUI->PM3] ", buf, n);
    }
    return 0;
}

DWORD WINAPI stdout_pump(LPVOID p) {
    (void)p;
    char buf[4096];
    DWORD n;
    HANDLE out = GetStdHandle(STD_OUTPUT_HANDLE);
    while (ReadFile(g_childStdout, buf, sizeof(buf), &n, NULL) && n > 0) {
        DWORD w;
        WriteFile(out, buf, n, &w, NULL);
        FlushFileBuffers(out);
        logchunk("[PM3->GUI] ", buf, n);
    }
    return 0;
}

int main(int argc, char **argv) {
    InitializeCriticalSection(&g_lock);
    g_log = fopen("D:\\20260415MAX\\gui_comm_log.txt", "ab");
    logchunk("\n===== wrapper start =====\n", "", 0);

    // real exe lives next to this wrapper
    char dir[MAX_PATH];
    GetModuleFileNameA(NULL, dir, MAX_PATH);
    char *slash = strrchr(dir, '\\');
    if (slash) *slash = 0;

    char cmd[4096];
    snprintf(cmd, sizeof(cmd), "\"%s\\proxmark3_real.exe\"", dir);
    for (int i = 1; i < argc; i++) {
        strncat(cmd, " \"", sizeof(cmd) - strlen(cmd) - 1);
        strncat(cmd, argv[i], sizeof(cmd) - strlen(cmd) - 1);
        strncat(cmd, "\"", sizeof(cmd) - strlen(cmd) - 1);
    }
    logchunk("[args] ", cmd, strlen(cmd));

    SECURITY_ATTRIBUTES sa = {sizeof(sa), NULL, TRUE};
    HANDLE chInR, chInW, chOutR, chOutW;
    CreatePipe(&chInR, &chInW, &sa, 0);
    CreatePipe(&chOutR, &chOutW, &sa, 0);
    SetHandleInformation(chInW, HANDLE_FLAG_INHERIT, 0);
    SetHandleInformation(chOutR, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOA si;
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = chInR;
    si.hStdOutput = chOutW;
    si.hStdError = chOutW;
    PROCESS_INFORMATION pi;
    if (!CreateProcessA(NULL, cmd, NULL, NULL, TRUE, 0, NULL, dir, &si, &pi)) {
        logchunk("[wrapper] CreateProcess failed\n", "", 0);
        return 1;
    }
    CloseHandle(chInR);
    CloseHandle(chOutW);
    g_childStdin = chInW;
    g_childStdout = chOutR;

    CreateThread(NULL, 0, stdin_pump, NULL, 0, NULL);
    CreateThread(NULL, 0, stdout_pump, NULL, 0, NULL);

    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 0;
    GetExitCodeProcess(pi.hProcess, &code);
    logchunk("===== child exit =====\n", "", 0);
    if (g_log) fclose(g_log);
    return (int)code;
}
