#include <stdio.h>

struct point {
    int x;
    int y;
};

struct point make_point(int x, int y) {
    struct point p;
    p.x = x;
    p.y = y;
    return p;
}

void print_point(struct point p) {
    printf("(%d, %d)\n", p.x, p.y);
}
