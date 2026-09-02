#include <stdio.h>

struct point {
    int row;
    int col;
};

struct point make_point(int row, int col) {
    struct point p;
    p.row = row;
    p.col = col;
    return p;
}

void print_point(struct point p) {
    printf("(%d, %d)\n", p.row, p.col);
}
