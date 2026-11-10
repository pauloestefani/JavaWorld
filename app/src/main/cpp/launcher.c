/*
 * JavaWorld — launcher do Java.
 *
 * O Android não deixa um app executar arquivos baixados para a pasta de dados.
 * Este pequeno programa vai dentro do APK (como libjwlauncher.so, que o Android
 * permite executar) e carrega o Java baixado chamando JLI_Launch, que é o mesmo
 * ponto de entrada do comando "java" normal.
 *
 * Variáveis de ambiente usadas:
 *   JW_LIBJLI  caminho do libjli.so do Java
 *   JW_LIBJVM  caminho do libjvm.so (opcional, pré-carrega)
 *   JW_PRELOAD biblioteca para carregar antes de tudo (o libc++_shared.so do app)
 */
#include <dlfcn.h>
#include <malloc.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

typedef int (*jli_launch_fn)(int argc, char **argv,
                             int jargc, const char **jargv,
                             int appclassc, const char **appclassv,
                             const char *fullversion, const char *dotversion,
                             const char *pname, const char *lname,
                             unsigned char javaargs, unsigned char cpwildcard,
                             unsigned char javaw, int ergo);

/*
 * Celulares ARM64 com Android 11+ marcam os ponteiros do malloc ("tagged pointers").
 * O Java (HotSpot) não espera isso e o processo aborta (sinal 6). Os apps Android
 * desligam isso pelo manifesto, mas um programa executado separado precisa desligar
 * sozinho, logo no começo.
 */
static void disable_android_memory_checks(void) {
#if defined(__aarch64__)
    mallopt(-204 /* M_BIONIC_SET_HEAP_TAGGING_LEVEL */, 0 /* M_HEAP_TAGGING_LEVEL_NONE */);
#endif
    /* fdsan: o Java fecha descritores de um jeito que o Android às vezes considera erro. */
    typedef int (*fdsan_fn)(int);
    fdsan_fn set_level = (fdsan_fn) dlsym(RTLD_DEFAULT, "android_fdsan_set_error_level");
    if (set_level != NULL) set_level(0 /* ANDROID_FDSAN_ERROR_LEVEL_DISABLED */);
}

int main(int argc, char **argv) {
    disable_android_memory_checks();
    printf("[jw-pid] %d\n", (int) getpid());
    fflush(stdout);

    const char *jli = getenv("JW_LIBJLI");
    const char *jvm = getenv("JW_LIBJVM");
    const char *preload = getenv("JW_PRELOAD");
    if (preload != NULL && preload[0] != '\0' && dlopen(preload, RTLD_NOW | RTLD_GLOBAL) == NULL) {
        fprintf(stderr, "[launcher] aviso ao carregar %s: %s\n", preload, dlerror());
    }
    if (jli == NULL) {
        fprintf(stderr, "[launcher] JW_LIBJLI não definido\n");
        return 90;
    }
    if (jvm != NULL && dlopen(jvm, RTLD_NOW | RTLD_GLOBAL) == NULL) {
        fprintf(stderr, "[launcher] aviso ao carregar libjvm: %s\n", dlerror());
    }
    void *handle = dlopen(jli, RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        fprintf(stderr, "[launcher] não consegui carregar o Java: %s\n", dlerror());
        return 91;
    }
    jli_launch_fn launch = (jli_launch_fn) dlsym(handle, "JLI_Launch");
    if (launch == NULL) {
        fprintf(stderr, "[launcher] JLI_Launch não encontrado\n");
        return 92;
    }

    /* Começa com os sinais limpos para o Java instalar os próprios tratadores. */
    for (int s = 1; s < 32; s++) {
        if (s != SIGKILL && s != SIGSTOP) signal(s, SIG_DFL);
    }
    sigset_t none;
    sigemptyset(&none);
    sigprocmask(SIG_SETMASK, &none, NULL);

    return launch(argc, argv, 0, NULL, 0, NULL,
                  "1.8.0-internal", "1.8", "java", "openjdk",
                  0 /* javaargs */, 1 /* cpwildcard */, 0 /* javaw */, 0 /* ergo */);
}
