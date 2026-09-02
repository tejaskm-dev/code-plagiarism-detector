#include <stdio.h>
#include <string.h>
#include <ctype.h>

int is_palindrome(const char *text) {
    int left = 0;
    int right = (int) strlen(text) - 1;

    while (left < right) {
        while (left < right && !isalnum((unsigned char) text[left])) {
            left++;
        }
        while (left < right && !isalnum((unsigned char) text[right])) {
            right--;
        }
        if (tolower((unsigned char) text[left]) != tolower((unsigned char) text[right])) {
            return 0;
        }
        left++;
        right--;
    }
    return 1;
}

int main(void) {
    printf("%d\n", is_palindrome("A man, a plan, a canal: Panama"));
    return 0;
}
