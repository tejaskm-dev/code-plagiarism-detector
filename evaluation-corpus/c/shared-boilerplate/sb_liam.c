#include <stdio.h>
#include <stdlib.h>

/* ---- provided starter code: do not modify ---- */

struct node {
    int value;
    struct node *next;
};

struct node *push(struct node *head, int value) {
    struct node *fresh = malloc(sizeof(struct node));
    if (fresh == NULL) {
        return head;
    }
    fresh->value = value;
    fresh->next = head;
    return fresh;
}

void release(struct node *head) {
    while (head != NULL) {
        struct node *next = head->next;
        free(head);
        head = next;
    }
}

int length(struct node *head) {
    int count = 0;
    while (head != NULL) {
        count++;
        head = head->next;
    }
    return count;
}

/* ---- end of provided code ---- */

int solve(struct node *head) {
    int evens = 0;
    for (struct node *p = head; p != NULL; p = p->next) {
        if (p->value % 2 == 0) {
            evens = evens + 1;
        }
    }
    return evens;
}

int main(void) {
    struct node *list = NULL;
    list = push(list, 3);
    list = push(list, 1);
    list = push(list, 2);
    printf("%d %d\\n", length(list), solve(list));
    release(list);
    return 0;
}
