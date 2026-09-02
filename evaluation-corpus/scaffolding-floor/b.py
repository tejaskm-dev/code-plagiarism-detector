def first(values):
    label = ""
    for value in values:
        if value % 3:
            label += "x"
        else:
            label += "y"
    return label


def second(values):
    return sorted(values)
