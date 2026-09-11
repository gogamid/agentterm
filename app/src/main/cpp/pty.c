// AgentTerm PTY bridge: open a real pseudo-terminal for the local shell.
// Uses bionic's openpty + fork + exec — no root, no Termux code, no GPL binaries.
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <pty.h>
#include <termios.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <errno.h>

static char** string_array(JNIEnv* env, jobjectArray arr) {
    if (arr == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, arr);
    char** out = (char**)calloc(n + 1, sizeof(char*));
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        const char* s = (*env)->GetStringUTFChars(env, js, NULL);
        out[i] = strdup(s);
        (*env)->ReleaseStringUTFChars(env, js, s);
        (*env)->DeleteLocalRef(env, js);
    }
    return out;
}

static void free_string_array(char** arr) {
    if (arr == NULL) return;
    for (int i = 0; arr[i] != NULL; i++) free(arr[i]);
    free(arr);
}

static jint native_open(
    JNIEnv* env, jclass clazz,
    jstring jshell, jobjectArray jargv, jobjectArray jenvp,
    jint cols, jint rows) {

    int master = -1, slave = -1;
    if (openpty(&master, &slave, NULL, NULL, NULL) != 0) return -1;

    const char* shell = (*env)->GetStringUTFChars(env, jshell, NULL);
    char** argv = string_array(env, jargv);
    char** envp = string_array(env, jenvp);

    pid_t pid = fork();
    if (pid == 0) {
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, 0); dup2(slave, 1); dup2(slave, 2);
        close(master); close(slave);
        if (envp != NULL) for (int i = 0; envp[i] != NULL; i++) putenv(envp[i]);
        if (argv != NULL && argv[0] != NULL) {
            execvp(argv[0], argv);
        } else {
            execlp("sh", "sh", NULL);
        }
        _exit(127);
    }

    close(slave);
    (*env)->ReleaseStringUTFChars(env, jshell, shell);
    free_string_array(argv);
    free_string_array(envp);
    return pid < 0 ? -1 : master;
}

static void native_set_size(JNIEnv* env, jclass clazz, jint fd, jint cols, jint rows) {
    struct winsize ws;
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(fd, TIOCSWINSZ, &ws);
}

static void native_close(JNIEnv* env, jclass clazz, jint fd) { close(fd); }

static jint native_read(JNIEnv* env, jclass clazz, jint fd, jbyteArray buf, jint off, jint len) {
    jbyte* data = (*env)->GetByteArrayElements(env, buf, NULL);
    if (data == NULL) return -1;
    ssize_t n;
    do { n = read(fd, data + off, (size_t)len); } while (n < 0 && errno == EINTR);
    (*env)->ReleaseByteArrayElements(env, buf, data, 0);
    return (jint)n;
}

static jint native_write(JNIEnv* env, jclass clazz, jint fd, jbyteArray buf, jint off, jint len) {
    jbyte* data = (*env)->GetByteArrayElements(env, buf, NULL);
    if (data == NULL) return -1;
    ssize_t n;
    size_t total = 0;
    while (total < (size_t)len) {
        n = write(fd, data + off + total, (size_t)(len - total));
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        total += (size_t)n;
    }
    (*env)->ReleaseByteArrayElements(env, buf, data, 0);
    return (jint)total;
}

static const JNINativeMethod kMethods[] = {
    { "nativeOpen", "(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;II)I", (void*)native_open },
    { "nativeSetSize", "(III)V", (void*)native_set_size },
    { "nativeClose", "(I)V", (void*)native_close },
    { "nativeRead", "(I[BII)I", (void*)native_read },
    { "nativeWrite", "(I[BII)I", (void*)native_write },
};

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = (*env)->FindClass(env, "app/agentterm/pty/PtyBridge");
    if (clazz == NULL) return JNI_ERR;
    (*env)->RegisterNatives(env, clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0]));
    return JNI_VERSION_1_6;
}