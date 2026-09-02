#include <stdio.h>
#include <string.h>
#include <ctype.h>

#define BUFFER_CAP 512

/* Builds a cleaned copy, reverses it, and compares the two. */
int is_palindrome(const char *phrase) {
    char cleaned[BUFFER_CAP];
    char mirrored[BUFFER_CAP];
    int kept = 0;

    for (int i = 0; phrase[i] != '\0' && kept < BUFFER_CAP - 1; i++) {
        if (isalnum((unsigned char) phrase[i])) {
            cleaned[kept] = (char) tolower((unsigned char) phrase[i]);
            kept++;
        }
    }
    cleaned[kept] = '\0';

    for (int i = 0; i < kept; i++) {
        mirrored[i] = cleaned[kept - 1 - i];
    }
    mirrored[kept] = '\0';

    return strcmp(cleaned, mirrored) == 0;
}

int main(void) {
    printf("%d\n", is_palindrome("Do geese see God"));
    return 0;
}
