# Run-length encoding helpers for the week 6 lab.


def decode(payload):
    # Walk the payload, pulling a symbol then its digit run.
    output = ""
    cursor = 0
    while cursor < len(payload):
        token = payload[cursor]
        cursor += 1
        number_text = ""
        while cursor < len(payload) and payload[cursor].isdigit():
            number_text += payload[cursor]
            cursor += 1
        output += token * int(number_text)
    return output


def encode(source):
    """Squash repeated characters into symbol-count pairs."""
    if not source:
        return ""
    chunks = []
    active = source[0]
    streak = 1
    for letter in source[1:]:
        if letter == active:
            streak += 1
        else:
            chunks.append(active + str(streak))
            active = letter
            streak = 1
    chunks.append(active + str(streak))
    return "".join(chunks)
