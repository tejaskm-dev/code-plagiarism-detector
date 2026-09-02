import csv
import sys


class Dataset:
    """Provided starter code - do not modify this class."""

    def __init__(self, path):
        self.path = path
        self.rows = []

    def load(self):
        with open(self.path, newline="") as handle:
            reader = csv.DictReader(handle)
            for row in reader:
                self.rows.append(row)
        return self

    def column(self, name):
        return [row[name] for row in self.rows]

    def row_count(self):
        return len(self.rows)


def main():
    if len(sys.argv) < 2:
        print("usage: prog <file.csv>")
        return 1
    data = Dataset(sys.argv[1]).load()
    print(summarise(data))
    return 0


def summarise(data):
    values = [float(v) for v in data.column("amount")]
    total = 0.0
    for value in values:
        total += value
    return "rows=%d total=%.2f" % (data.row_count(), total)


if __name__ == "__main__":
    sys.exit(main())
