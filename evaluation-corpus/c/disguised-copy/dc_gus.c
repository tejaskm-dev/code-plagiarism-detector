#include <stdio.h>
#include <string.h>
#include <ctype.h>

/*
 * Two-pointer palindrome test.
 * Skips anything that is not a letter or a digit.
 */
int is_palindrome(const char *phrase) {
    int head = 0;
    int tail = (int) strlen(phrase) - 1;

    while (head < tail) {
        /* advance past punctuation on the left */
        while (head < tail && !isalnum((unsigned char) phrase[head])) {
            head++;
        }
        /* and on the right */
        while (head < tail && !isalnum((unsigned char) phrase[tail])) {
            tail--;
        }
        if (tolower((unsigned char) phrase[head]) != tolower((unsigned char) phrase[tail])) {
            return 0;
        }
        head++;
        tail--;
    }
    return 1;
}

int main(void) {
    printf("%d\n", is_palindrome("Never odd or even"));
    return 0;
}
