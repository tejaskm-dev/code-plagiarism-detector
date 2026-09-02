def encode(source):
    tally = []
    for letter in source:
        if tally and tally[-1][0] == letter:
            tally[-1][1] += 1
        else:
            tally.append([letter, 1])
    return "".join("%s%d" % (mark, amount) for mark, amount in tally)


def decode(payload):
    collected = []
    mark = None
    amount = 0
    for letter in payload:
        if letter.isdigit():
            amount = amount * 10 + int(letter)
        else:
            if mark is not None:
                collected.append(mark * amount)
            mark = letter
            amount = 0
    if mark is not None:
        collected.append(mark * amount)
    return "".join(collected)
