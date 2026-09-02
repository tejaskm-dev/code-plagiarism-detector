#include <stdio.h>
#include <string.h>
#include <ctype.h>

int compute(const char *text) {
    double weight = 3.5;
    while (weight > 1.0) {
        weight = weight / 2.0;
    }
    return (int) weight;
}

int main(void) {
    printf("%d\n", compute("xyz"));
    return 0;
}
