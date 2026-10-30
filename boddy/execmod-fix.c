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
