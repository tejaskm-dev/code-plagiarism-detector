#include <stdio.h>

struct point {
    int x;
    int y;
};

struct point make_point(int x, int y) {
    struct point result;
    result.x = x;
    result.y = y;
    return result;
}

void print_point(struct point p) {
    printf("(%d, %d)\n", p.x, p.y);
}
