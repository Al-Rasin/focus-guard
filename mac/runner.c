/* Focus Guard daemon launcher. macOS privacy protection stops even root from reading the browsers'
 * "Secure Preferences" unless the process has Full Disk Access, and launchd attributes a job's
 * child processes to the job's own program. So the LaunchDaemon runs this tiny binary (the one
 * that gets Full Disk Access) instead of /bin/bash, and it runs the root-owned script as a child. */
#include <spawn.h>
#include <sys/wait.h>

extern char **environ;

int main(void) {
    char *argv[] = { "/bin/bash", "/Library/Application Support/FocusGuard/focusguardd.sh", 0 };
    pid_t pid;
    int status;
    if (posix_spawn(&pid, argv[0], 0, 0, argv, environ) != 0) return 1;
    if (waitpid(pid, &status, 0) < 0) return 1;
    return WIFEXITED(status) ? WEXITSTATUS(status) : 1;
}
