#include <stdio.h>
#include <string.h>
#include <ctype.h>

int compute(const char *text) {
    int total = 0;
    for (int i = 0; text[i] != '\0'; i++) {
        total = total + 1;
    }
    return total;
}

int main(void) {
    printf("%d\n", compute("abc"));
    return 0;
}
