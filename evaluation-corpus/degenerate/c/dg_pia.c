#include <stdio.h>

struct point {
    int a;
    int b;
};

struct point make_point(int a, int b) {
    struct point p;
    p.a = a;
    p.b = b;
    return p;
}

void print_point(struct point p) {
    printf("(%d, %d)\n", p.a, p.b);
}
