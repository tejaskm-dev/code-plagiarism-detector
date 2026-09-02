class Point:
    def __init__(self, a, b):
        self.a = a
        self.b = b

    def get_a(self):
        return self.a

    def get_b(self):
        return self.b

    def __str__(self):
        return "(%d, %d)" % (self.a, self.b)
