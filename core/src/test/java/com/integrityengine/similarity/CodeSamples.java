package com.integrityengine.similarity;

/**
 * Real source files used by the end-to-end tests.
 *
 * <p>Each language has three samples: an ORIGINAL, a PLAGIARISED copy of it (renamed,
 * reformatted, members reordered, comments rewritten) and an INDEPENDENT program that
 * solves an unrelated problem in the same language. The independent sample is the
 * control: without it, a comparator that returned 1.0 for everything would look perfect.
 */
final class CodeSamples {

    private CodeSamples() {
    }

    static final String JAVA_ORIGINAL = """
            public class GradeBook {
                private final int[] scores;

                // Wraps a set of raw exam scores.
                public GradeBook(int[] scores) {
                    this.scores = scores;
                }

                public double average() {
                    int total = 0;
                    for (int score : scores) {
                        total += score;
                    }
                    return (double) total / scores.length;
                }

                public int highest() {
                    int best = scores[0];
                    for (int i = 1; i < scores.length; i++) {
                        if (scores[i] > best) {
                            best = scores[i];
                        }
                    }
                    return best;
                }

                public int countAbove(int limit) {
                    int matches = 0;
                    for (int score : scores) {
                        if (score > limit) {
                            matches++;
                        }
                    }
                    return matches;
                }
            }
            """;

    /** Renamed throughout, reformatted, methods reordered, comments replaced. */
    static final String JAVA_PLAGIARISED = """
            public class MarkRegister
            {
                private final int[] values;

                public MarkRegister(int[] values) { this.values = values; }

                public int tally(int bound)
                {
                    int hits = 0;
                    for (int value : values) {
                        if (value > bound) { hits++; }
                    }
                    return hits;
                }

                /* Largest mark on record. */
                public int peak()
                {
                    int top = values[0];
                    for (int k = 1; k < values.length; k++) {
                        if (values[k] > top) { top = values[k]; }
                    }
                    return top;
                }

                public double mean()
                {
                    int sum = 0;
                    for (int value : values) { sum += value; }
                    return (double) sum / values.length;
                }
            }
            """;

    static final String JAVA_INDEPENDENT = """
            public class TaskQueue {
                private static class Node {
                    String label;
                    Node next;
                    Node(String label) { this.label = label; }
                }

                private Node head;
                private Node tail;
                private int size;

                public void enqueue(String label) {
                    Node node = new Node(label);
                    if (tail == null) {
                        head = node;
                        tail = node;
                    } else {
                        tail.next = node;
                        tail = node;
                    }
                    size++;
                }

                public String dequeue() {
                    if (head == null) {
                        throw new IllegalStateException("queue is empty");
                    }
                    String label = head.label;
                    head = head.next;
                    if (head == null) {
                        tail = null;
                    }
                    size--;
                    return label;
                }

                public boolean isEmpty() {
                    return size == 0;
                }
            }
            """;

    static final String PYTHON_ORIGINAL = """
            class GradeBook:
                \"\"\"Simple statistics over a list of exam scores.\"\"\"

                def __init__(self, scores):
                    self.scores = scores

                def average(self):
                    total = 0
                    for score in self.scores:
                        total += score
                    return total / len(self.scores)

                def highest(self):
                    best = self.scores[0]
                    for score in self.scores:
                        if score > best:
                            best = score
                    return best

                # How many scores clear the given bar?
                def count_above(self, limit):
                    matches = 0
                    for score in self.scores:
                        if score > limit:
                            matches += 1
                    return matches
            """;

    static final String PYTHON_PLAGIARISED = """
            class MarkRegister:
              \"\"\"Totally different docstring wording here.\"\"\"

              def __init__(self, values):
                self.values = values

              def tally(self, bound):
                hits = 0
                for value in self.values:
                  if value > bound:
                    hits += 1
                return hits

              def peak(self):
                top = self.values[0]
                for value in self.values:
                  if value > top:
                    top = value
                return top

              def mean(self):
                sum_so_far = 0
                for value in self.values:
                  sum_so_far += value
                return sum_so_far / len(self.values)
            """;

    static final String PYTHON_INDEPENDENT = """
            class TaskQueue:
                def __init__(self):
                    self.items = []
                    self.processed = 0

                def enqueue(self, label):
                    self.items.append(label)

                def dequeue(self):
                    if not self.items:
                        raise IndexError("queue is empty")
                    head = self.items.pop(0)
                    self.processed += 1
                    return head

                def is_empty(self):
                    return len(self.items) == 0

                def report(self):
                    return "processed %d items" % self.processed
            """;

    static final String C_ORIGINAL = """
            #include <stdio.h>

            /* Sums every element of the array. */
            int total(const int values[], int count) {
                int sum = 0;
                for (int i = 0; i < count; i++) {
                    sum += values[i];
                }
                return sum;
            }

            int largest(const int values[], int count) {
                int best = values[0];
                for (int i = 1; i < count; i++) {
                    if (values[i] > best) {
                        best = values[i];
                    }
                }
                return best;
            }
            """;

    static final String C_PLAGIARISED = """
            #include <stdio.h>
            int peak(const int data[],int n)
            {
                int top=data[0];
                for(int k=1;k<n;k++){
                    if(data[k]>top){ top=data[k]; }
                }
                return top;
            }

            /* Adds everything up. */
            int accumulate(const int data[],int n)
            {
                int acc=0;
                for(int k=0;k<n;k++){ acc+=data[k]; }
                return acc;
            }
            """;

    static final String C_INDEPENDENT = """
            #include <stdlib.h>
            #include <string.h>

            struct node {
                char *label;
                struct node *next;
            };

            struct node *push(struct node *head, const char *label) {
                struct node *fresh = malloc(sizeof(struct node));
                fresh->label = strdup(label);
                fresh->next = head;
                return fresh;
            }

            void release(struct node *head) {
                while (head != NULL) {
                    struct node *next = head->next;
                    free(head->label);
                    free(head);
                    head = next;
                }
            }
            """;

    static final String CPP_ORIGINAL = """
            #include <vector>
            using namespace std;

            // Largest element of the vector.
            int largest(const vector<int>& items) {
                int best = items[0];
                for (size_t i = 1; i < items.size(); ++i) {
                    if (items[i] > best) {
                        best = items[i];
                    }
                }
                return best;
            }

            int total(const vector<int>& items) {
                int sum = 0;
                for (size_t i = 0; i < items.size(); ++i) {
                    sum += items[i];
                }
                return sum;
            }
            """;

    static final String CPP_PLAGIARISED = """
            #include <vector>
            using namespace std;
            int accumulate(const vector<int>& data)
            {
                int acc = 0;
                for (size_t k = 0; k < data.size(); ++k) { acc += data[k]; }
                return acc;
            }

            /* Peak value. */
            int peak(const vector<int>& data)
            {
                int top = data[0];
                for (size_t k = 1; k < data.size(); ++k) {
                    if (data[k] > top) { top = data[k]; }
                }
                return top;
            }
            """;

    static final String CPP_INDEPENDENT = """
            #include <string>
            #include <map>
            using namespace std;

            class Registry {
            public:
                void record(const string& key) {
                    counts[key] += 1;
                }

                int lookup(const string& key) const {
                    auto found = counts.find(key);
                    if (found == counts.end()) {
                        return 0;
                    }
                    return found->second;
                }

            private:
                map<string, int> counts;
            };
            """;

    static final String JS_ORIGINAL = """
            // Statistics helpers for a list of scores.
            function average(scores) {
                let total = 0;
                for (const score of scores) {
                    total += score;
                }
                return total / scores.length;
            }

            function highest(scores) {
                let best = scores[0];
                for (const score of scores) {
                    if (score > best) {
                        best = score;
                    }
                }
                return best;
            }

            function countAbove(scores, limit) {
                let matches = 0;
                for (const score of scores) {
                    if (score > limit) {
                        matches += 1;
                    }
                }
                return matches;
            }
            """;

    static final String JS_PLAGIARISED = """
            function tally(values, bound)
            {
                let hits = 0;
                for (const value of values) {
                    if (value > bound) { hits += 1; }
                }
                return hits;
            }

            /* Highest of the lot. */
            function peak(values)
            {
                let top = values[0];
                for (const value of values) {
                    if (value > top) { top = value; }
                }
                return top;
            }

            function mean(values)
            {
                let sum = 0;
                for (const value of values) { sum += value; }
                return sum / values.length;
            }
            """;

    static final String JS_INDEPENDENT = """
            class TaskQueue {
                constructor() {
                    this.items = [];
                    this.processed = 0;
                }

                enqueue(label) {
                    this.items.push(label);
                }

                dequeue() {
                    if (this.items.length === 0) {
                        throw new Error("queue is empty");
                    }
                    this.processed += 1;
                    return this.items.shift();
                }

                report() {
                    return `processed ${this.processed} items`;
                }
            }
            """;

    /** A short helper, and a much larger file with that helper pasted into it verbatim. */
    static final String JAVA_SNIPPET = """
            public class Helper {
                public static int gcd(int a, int b) {
                    while (b != 0) {
                        int temp = b;
                        b = a % b;
                        a = temp;
                    }
                    return a;
                }
            }
            """;

    static final String JAVA_HOST_CONTAINING_SNIPPET = """
            public class BigApplication {
                private final java.util.List<String> log = new java.util.ArrayList<>();

                public void record(String message) {
                    log.add(message);
                    if (log.size() > 100) {
                        log.remove(0);
                    }
                }

                public int gcd(int a, int b) {
                    while (b != 0) {
                        int temp = b;
                        b = a % b;
                        a = temp;
                    }
                    return a;
                }

                public String render() {
                    StringBuilder builder = new StringBuilder();
                    for (String entry : log) {
                        builder.append(entry).append('\\n');
                    }
                    return builder.toString();
                }

                public void reset() {
                    log.clear();
                }

                public boolean isBusy() {
                    return log.size() > 50;
                }
            }
            """;
}
