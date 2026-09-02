def encode(text):

    """Compress a string using run-length encoding."""

    if not text:

            return ""

    pieces = []

    current = text[0]

    run = 1

    for character in text[1:]:

            if character == current:

                    run += 1

            else:

                    pieces.append(current + str(run))

                    current = character

                    run = 1

    pieces.append(current + str(run))

    return "".join(pieces)


def decode(encoded):

    result = ""

    index = 0

    while index < len(encoded):

            symbol = encoded[index]

            index += 1

            digits = ""

            while index < len(encoded) and encoded[index].isdigit():

                    digits += encoded[index]

                    index += 1

            result += symbol * int(digits)

    return result
