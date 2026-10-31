/* Lets Wine run on Android: loaded into Wine's processes with LD_PRELOAD by /opt/boddy/studio.
 *
 * Wine loads a Windows DLL by mapping its file, fixing up addresses in the mapped copy, and then
 * making the code executable with mprotect(). Android's SELinux policy forbids exactly that for
 * apps targeting Android 8+ ("execmod": executable pages of a file mapping that have been written
 * to), so the mprotect() fails with EACCES and Wine dies running code it could not make executable
 * ("failed to set ... protection ... noexec filesystem?"). Executable anonymous memory is allowed
 * (Box64's own recompiler depends on it), so when that mprotect() fails, this moves the pages'
 * contents into anonymous memory, in place and in one step, and makes that executable instead.
 *
 * It also replaces the CPU model in /proc/cpuinfo when BODDY_GENERIC_CPU=1. Mesa's software renderers
 * (llvmpipe, lavapipe) compile shaders with LLVM for the CPU model named there; on Snapdragon phones
 * that model (e.g. Cortex-X4) implies SVE, which the kernel leaves switched off, so the first drawn
 * frame dies with SIGILL. The model becomes Cortex-A78 (ARMv8.2: LSE atomics, dot product, FP16, no
 * SVE) when the kernel lists all of its features, and is left out otherwise, which makes LLVM target
 * a generic ARMv8 CPU; either way LLVM adds only the features the kernel lists.
 *
 * Built without a C library's headers so it can be cross-compiled anywhere:
 *   clang --target=aarch64-linux-gnu -O2 -fPIC -shared -nostdlib -fuse-ld=lld -o libboddy-execmod.so execmod-fix.c
 */
typedef unsigned long size_t;

#define PROT_READ 1
#define PROT_WRITE 2
#define PROT_EXEC 4
#define MAP_PRIVATE 2
#define MAP_ANONYMOUS 0x20
#define MAP_FAILED ((void *)-1)
#define MREMAP_MAYMOVE 1
#define MREMAP_FIXED 2
#define EACCES 13
#define RTLD_NEXT ((void *)-1l)
#define SC_PAGESIZE 30

extern void *dlsym(void *handle, const char *name);
extern int *__errno_location(void);
extern long sysconf(int name);
extern void *mmap(void *addr, size_t len, int prot, int flags, int fd, long offset);
extern int munmap(void *addr, size_t len);
extern void *mremap(void *old, size_t old_len, size_t new_len, int flags, ...);
extern void *memcpy(void *to, const void *from, size_t n);
extern int memfd_create(const char *name, unsigned flags);
extern long read(int fd, void *buf, size_t n);
extern long write(int fd, const void *buf, size_t n);
extern long lseek(int fd, long offset, int whence);
extern int close(int fd);
extern char *getenv(const char *name);

static int (*real_mprotect)(void *, size_t, int);

int mprotect(void *addr, size_t len, int prot)
{
    if (!real_mprotect) real_mprotect = (int (*)(void *, size_t, int))dlsym(RTLD_NEXT, "mprotect");
    int ret = real_mprotect(addr, len, prot);
    if (!ret || !(prot & PROT_EXEC) || *__errno_location() != EACCES) return ret;

    size_t page = (size_t)sysconf(SC_PAGESIZE);
    len = (len + page - 1) & ~(page - 1);
    /* The protection asked for, minus execute and plus read so the bytes can be copied: allowed. */
    if (!len || real_mprotect(addr, len, (prot & ~PROT_EXEC) | PROT_READ)) goto fail;
    char *copy_to = mmap(0, len, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (copy_to == MAP_FAILED) goto fail;
    memcpy(copy_to, addr, len);
    /* Swap the copy in with mremap, so the range never reads as empty to another thread. */
    if (real_mprotect(copy_to, len, prot) ||
        mremap(copy_to, len, len, MREMAP_MAYMOVE | MREMAP_FIXED, addr) == MAP_FAILED)
    {
        munmap(copy_to, len);
        goto fail;
    }
    return 0;

fail:
    *__errno_location() = EACCES;
    return -1;
}

#define O_CREAT 0x40
#define O_TMPFILE_BIT 0x400000  /* __O_TMPFILE, the same on arm64 and x86-64 */
#define O_CLOEXEC 0x80000
#define MFD_CLOEXEC 1
#define AT_FDCWD (-100)
#ifndef CPUINFO_SOURCE
#define CPUINFO_SOURCE "/proc/cpuinfo"  /* tests point it at a sample file */
#endif

static int starts_with(const char *s, const char *prefix)
{
    while (*prefix) if (*s++ != *prefix++) return 0;
    return 1;
}

/* Whether the cpuinfo line [line, end) is a Features line holding every word in `words`. */
static int has_features(const char *line, const char *end, const char *const *words)
{
    if (!starts_with(line, "Features")) return 0;
    for (; *words; words++) {
        int found = 0;
        for (const char *p = line; p < end && !found; p++) {
            if (p != line && p[-1] != ' ' && p[-1] != '\t') continue;  /* word starts only */
            const char *w = *words, *q = p;
            while (*w && q < end && *q == *w) { q++; w++; }
            found = !*w && (q == end || *q == ' ' || *q == '\n' || *q == '\t');
        }
        if (!found) return 0;
    }
    return 1;
}

static int is_cpuinfo(const char *path)
{
    const char *want = "/proc/cpuinfo";
    while (*want) if (*path++ != *want++) return 0;
    if (*path) return 0;
    const char *on = getenv("BODDY_GENERIC_CPU");
    return on && on[0] == '1' && !on[1];
}

/* A memfd holding /proc/cpuinfo without the lines that name the CPU model; -1 if that fails. */
static int generic_cpuinfo(int flags)
{
    static int (*real_openat)(int, const char *, int, ...);
    static char in[65536], out[65536 + 8192];
    static const char *const a78[] = { "atomics", "asimddp", "fphp", "asimdhp", "lrcpc", "asimdrdm", 0 };
    static const char a78_model[] = "CPU implementer\t: 0x41\nCPU part\t: 0xd41\n";
    if (!real_openat) real_openat = (int (*)(int, const char *, int, ...))dlsym(RTLD_NEXT, "openat");
    int src = real_openat(AT_FDCWD, CPUINFO_SOURCE, 0);
    if (src < 0) return -1;
    long n = 0, got;
    while (n < (long)sizeof(in) - 1 && (got = read(src, in + n, sizeof(in) - 1 - n)) > 0) n += got;
    close(src);
    long o = 0;
    for (long i = 0; i < n;) {
        long end = i;
        while (end < n && in[end] != '\n') end++;
        if (end < n) end++;  /* keep the newline */
        const char *line = in + i;
        if (!starts_with(line, "CPU implementer") && !starts_with(line, "CPU part") &&
            !starts_with(line, "CPU variant") && !starts_with(line, "CPU revision")) {
            for (long j = i; j < end; j++) out[o++] = in[j];
            /* Each processor's block lists its features once; name the model right after them. */
            if (has_features(line, in + end, a78) && o + (long)sizeof(a78_model) < (long)sizeof(out)) {
                if (o && out[o - 1] != '\n') out[o++] = '\n';
                for (const char *m = a78_model; *m; m++) out[o++] = *m;
            }
        }
        i = end;
    }
    int fd = memfd_create("cpuinfo", (flags & O_CLOEXEC) ? MFD_CLOEXEC : 0);
    if (fd < 0) return -1;
    if (write(fd, out, o) != o || lseek(fd, 0, 0) != 0) { close(fd); return -1; }
    return fd;
}

#define OPEN_WRAPPER(name)                                                        \
int name(const char *path, int flags, ...)                                        \
{                                                                                 \
    static int (*real)(const char *, int, ...);                                   \
    unsigned mode = 0;                                                            \
    if (flags & (O_CREAT | O_TMPFILE_BIT)) {                                      \
        __builtin_va_list ap; __builtin_va_start(ap, flags);                      \
        mode = __builtin_va_arg(ap, unsigned); __builtin_va_end(ap);              \
    }                                                                             \
    if (is_cpuinfo(path)) { int fd = generic_cpuinfo(flags); if (fd >= 0) return fd; } \
    if (!real) real = (int (*)(const char *, int, ...))dlsym(RTLD_NEXT, #name);   \
    return real(path, flags, mode);                                               \
}
#define OPENAT_WRAPPER(name)                                                      \
int name(int dir, const char *path, int flags, ...)                               \
{                                                                                 \
    static int (*real)(int, const char *, int, ...);                              \
    unsigned mode = 0;                                                            \
    if (flags & (O_CREAT | O_TMPFILE_BIT)) {                                      \
        __builtin_va_list ap; __builtin_va_start(ap, flags);                      \
        mode = __builtin_va_arg(ap, unsigned); __builtin_va_end(ap);              \
    }                                                                             \
    if (is_cpuinfo(path)) { int fd = generic_cpuinfo(flags); if (fd >= 0) return fd; } \
    if (!real) real = (int (*)(int, const char *, int, ...))dlsym(RTLD_NEXT, #name); \
    return real(dir, path, flags, mode);                                          \
}
OPEN_WRAPPER(open)
OPEN_WRAPPER(open64)
OPENAT_WRAPPER(openat)
OPENAT_WRAPPER(openat64)
