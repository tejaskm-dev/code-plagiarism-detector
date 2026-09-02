#include <stdio.h>

int is_even(int candidate) {
    int remainder = candidate % 2;
    return remainder == 0;
}

int main(void) {
    printf("%d\n", is_even(13));
    return 0;
}
