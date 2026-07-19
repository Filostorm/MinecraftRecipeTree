#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr,
            "Usage: darwin-publish-directory <validated-staging> <fresh-output>\n");
    return EXIT_FAILURE;
  }

  if (renamex_np(argv[1], argv[2], RENAME_EXCL) != 0) {
    fprintf(stderr,
            "[publish-directory] Exclusive atomic rename from %s to %s failed: %s\n",
            argv[1], argv[2], strerror(errno));
    return EXIT_FAILURE;
  }

  fprintf(stdout,
          "[publish-directory] Exclusive atomic publication completed without overwrite.\n");
  return EXIT_SUCCESS;
}
