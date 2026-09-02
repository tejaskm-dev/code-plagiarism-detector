import re as regex


def decode(compressed):
    """Expand symbol-count pairs back into the original text."""
    rebuilt = []
    for found in regex.finditer(r"(\D)(\d+)", compressed):
        rebuilt.append(found.group(1) * int(found.group(2)))
    return "".join(rebuilt)


def encode(original):
    """Collapse each run of identical characters into a symbol and a count."""
    collected = []
    for found in regex.finditer(r"(.)\1*", original):
        collected.append(found.group(1))
        collected.append(str(len(found.group(0))))
    return "".join(collected)
