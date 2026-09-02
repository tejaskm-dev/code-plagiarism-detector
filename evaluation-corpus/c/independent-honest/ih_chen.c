#include <stdio.h>
#include <string.h>
#include <ctype.h>

static int check(const char *text, int low, int high) {
    if (low >= high) {
        return 1;
    }
    if (!isalnum((unsigned char) text[low])) {
        return check(text, low + 1, high);
    }
    if (!isalnum((unsigned char) text[high])) {
        return check(text, low, high - 1);
    }
    if (tolower((unsigned char) text[low]) != tolower((unsigned char) text[high])) {
        return 0;
    }
    return check(text, low + 1, high - 1);
}

int is_palindrome(const char *text) {
    return check(text, 0, (int) strlen(text) - 1);
}

int main(void) {
    printf("%d\n", is_palindrome("Was it a car or a cat I saw"));
    return 0;
}
