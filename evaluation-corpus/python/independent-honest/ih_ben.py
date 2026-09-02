from itertools import groupby


def encode(text):
    return "".join(f"{letter}{len(list(group))}" for letter, group in groupby(text))


def decode(encoded):
    import re
    parts = re.findall(r"(\D)(\d+)", encoded)
    return "".join(letter * int(count) for letter, count in parts)
