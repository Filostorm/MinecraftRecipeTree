#include <copyfile.h>
#include <errno.h>
#include <fts.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

static int report_path_error(const char *operation, const char *path) {
  fprintf(stderr, "[clone-tree] %s failed for %s: %s\n", operation, path,
          strerror(errno));
  return EXIT_FAILURE;
}

int main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "Usage: darwin-clone-tree <source-directory> <destination-directory>\n");
    return EXIT_FAILURE;
  }

  const char *source_root = argv[1];
  const char *destination_root = argv[2];
  const size_t source_length = strlen(source_root);
  char *roots[] = {argv[1], NULL};
  FTS *tree = fts_open(roots, FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV, NULL);
  if (tree == NULL) {
    return report_path_error("fts_open", source_root);
  }

  int result = EXIT_SUCCESS;
  FTSENT *entry = NULL;
  for (;;) {
    errno = 0;
    entry = fts_read(tree);
    if (entry == NULL) break;
    const char *suffix = entry->fts_path + source_length;
    if (strncmp(entry->fts_path, source_root, source_length) != 0 ||
        (*suffix != '\0' && *suffix != '/')) {
      fprintf(stderr, "[clone-tree] Traversal escaped the verified source root: %s\n",
              entry->fts_path);
      result = EXIT_FAILURE;
      break;
    }

    const size_t destination_length = strlen(destination_root) + strlen(suffix) + 1;
    char *destination = malloc(destination_length);
    if (destination == NULL) {
      fprintf(stderr, "[clone-tree] Could not allocate a destination path.\n");
      result = EXIT_FAILURE;
      break;
    }
    snprintf(destination, destination_length, "%s%s", destination_root, suffix);

    switch (entry->fts_info) {
      case FTS_D:
        if (mkdir(destination, 0700) != 0) {
          result = report_path_error("mkdir", destination);
        }
        break;
      case FTS_DP:
        if (chmod(destination, entry->fts_statp->st_mode & 07777) != 0) {
          result = report_path_error("chmod", destination);
        }
        break;
      case FTS_F:
        if (copyfile(entry->fts_path, destination, NULL,
                     COPYFILE_CLONE_FORCE | COPYFILE_ACL) != 0) {
          result = report_path_error("COPYFILE_CLONE_FORCE", entry->fts_path);
        }
        break;
      case FTS_DNR:
      case FTS_ERR:
      case FTS_NS:
        errno = entry->fts_errno;
        result = report_path_error("filesystem traversal", entry->fts_path);
        break;
      default:
        fprintf(stderr,
                "[clone-tree] Refusing symlink or special filesystem entry: %s\n",
                entry->fts_path);
        result = EXIT_FAILURE;
        break;
    }

    free(destination);
    if (result != EXIT_SUCCESS) break;
  }

  if (errno != 0 && result == EXIT_SUCCESS) {
    result = report_path_error("fts_read", source_root);
  }
  if (fts_close(tree) != 0 && result == EXIT_SUCCESS) {
    result = report_path_error("fts_close", source_root);
  }
  if (result == EXIT_SUCCESS) {
    fprintf(stdout, "[clone-tree] Copy-on-write clone completed without fallback.\n");
  }
  return result;
}
