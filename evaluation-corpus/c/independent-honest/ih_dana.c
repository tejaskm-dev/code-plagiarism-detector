#include <stdio.h>
#include <string.h>
#include <ctype.h>

#define MAX_LEN 512

int is_palindrome(const char *text) {
    char forward[MAX_LEN];
    char backward[MAX_LEN];
    int count = 0;

    for (int i = 0; text[i] != '\0' && count < MAX_LEN - 1; i++) {
        if (isalnum((unsigned char) text[i])) {
            forward[count] = (char) tolower((unsigned char) text[i]);
            count++;
        }
    }
    forward[count] = '\0';

    for (int i = 0; i < count; i++) {
        backward[i] = forward[count - 1 - i];
    }
    backward[count] = '\0';

    return strcmp(forward, backward) == 0;
}

int main(void) {
    printf("%d\n", is_palindrome("Step on no pets"));
    return 0;
}
