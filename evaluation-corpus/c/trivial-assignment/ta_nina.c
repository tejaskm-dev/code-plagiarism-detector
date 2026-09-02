#include <stdio.h>

int is_even(int number) {
    return number % 2 == 0;
}

int main(void) {
    printf("%d\n", is_even(4));
    return 0;
}
