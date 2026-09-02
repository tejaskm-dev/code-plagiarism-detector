#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

static char *normalise(const char *input) {
    size_t length = strlen(input);
    char *buffer = malloc(length + 1);
    size_t out = 0;
    for (size_t i = 0; i < length; i++) {
        if (isalnum((unsigned char) input[i])) {
            buffer[out++] = (char) tolower((unsigned char) input[i]);
        }
    }
    buffer[out] = '\0';
    return buffer;
}

int is_palindrome(const char *text) {
    char *clean = normalise(text);
    size_t length = strlen(clean);
    int verdict = 1;
    for (size_t i = 0; i < length / 2; i++) {
        if (clean[i] != clean[length - 1 - i]) {
            verdict = 0;
            break;
        }
    }
    free(clean);
    return verdict;
}

int main(void) {
    printf("%d\n", is_palindrome("No lemon, no melon"));
    return 0;
}
