import re



def encode(text):
        out = []
        for match in re.finditer(r"(.)\1*", text):
                out.append(match.group(1))
                out.append(str(len(match.group(0))))
        return "".join(out)



def decode(encoded):
        expanded = []
        for match in re.finditer(r"(\D)(\d+)", encoded):
                expanded.append(match.group(1) * int(match.group(2)))
        return "".join(expanded)
