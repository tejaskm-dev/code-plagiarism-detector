#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

int is_palindrome(const char *phrase);

static char *strip(const char *raw) {
    size_t span = strlen(raw);
    char *scratch = malloc(span + 1);
    size_t cursor = 0;
    for (size_t i = 0; i < span; i++) {
        if (isalnum((unsigned char) raw[i])) {
            scratch[cursor++] = (char) tolower((unsigned char) raw[i]);
        }
    }
    scratch[cursor] = '\0';
    return scratch;
}

int main(void) {
    printf("%d\n", is_palindrome("Rats live on no evil star"));
    return 0;
}

int is_palindrome(const char *phrase) {
    char *tidy = strip(phrase);
    size_t span = strlen(tidy);
    int answer = 1;
    for (size_t i = 0; i < span / 2; i++) {
        if (tidy[i] != tidy[span - 1 - i]) {
            answer = 0;
            break;
        }
    }
    free(tidy);
    return answer;
}
