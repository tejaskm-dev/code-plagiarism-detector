#include <stdio.h>

int is_even(int n) {
    return (n & 1) == 0;
}

int main(void) {
    printf("%d\n", is_even(10));
    return 0;
}
