class Point:
    def __init__(self, row, col):
        self.row = row
        self.col = col

    def get_row(self):
        return self.row

    def get_col(self):
        return self.col

    def __str__(self):
        return "(%d, %d)" % (self.row, self.col)
