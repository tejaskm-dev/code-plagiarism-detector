def encode(text):
    counts = []
    for character in text:
        if counts and counts[-1][0] == character:
            counts[-1][1] += 1
        else:
            counts.append([character, 1])
    return "".join("%s%d" % (symbol, total) for symbol, total in counts)


def decode(encoded):
    buffer = []
    symbol = None
    number = 0
    for character in encoded:
        if character.isdigit():
            number = number * 10 + int(character)
        else:
            if symbol is not None:
                buffer.append(symbol * number)
            symbol = character
            number = 0
    if symbol is not None:
        buffer.append(symbol * number)
    return "".join(buffer)
