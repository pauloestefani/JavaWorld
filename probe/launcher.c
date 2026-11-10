#include <dlfcn.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>

typedef int (*jli_launch_fn)(int, char **, int, const char **, int, const char **,
                             const char *, const char *, const char *, const char *,
                             unsigned char, unsigned char, unsigned char, int);

int main(int argc, char **argv) {
    const char *jli = getenv("JW_LIBJLI");
    const char *jvm = getenv("JW_LIBJVM");
    if (jli == NULL) { fprintf(stderr, "[launcher] JW_LIBJLI not set\n"); return 90; }
    if (jvm != NULL && dlopen(jvm, RTLD_NOW | RTLD_GLOBAL) == NULL)
        fprintf(stderr, "[launcher] warning: libjvm: %s\n", dlerror());
    void *h = dlopen(jli, RTLD_NOW | RTLD_GLOBAL);
    if (h == NULL) { fprintf(stderr, "[launcher] libjli: %s\n", dlerror()); return 91; }
    jli_launch_fn launch = (jli_launch_fn) dlsym(h, "JLI_Launch");
    if (launch == NULL) { fprintf(stderr, "[launcher] JLI_Launch not found\n"); return 92; }
    for (int s = 1; s < 32; s++) if (s != SIGKILL && s != SIGSTOP) signal(s, SIG_DFL);
    sigset_t none; sigemptyset(&none); sigprocmask(SIG_SETMASK, &none, NULL);
    return launch(argc, argv, 0, NULL, 0, NULL, "1.8.0-internal", "1.8", "java", "openjdk", 0, 1, 0, 0);
}
