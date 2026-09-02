#include <stdio.h>

int is_even(int value) {
    if (value % 2 == 0) {
        return 1;
    } else {
        return 0;
    }
}

int main(void) {
    printf("%d\n", is_even(7));
    return 0;
}
