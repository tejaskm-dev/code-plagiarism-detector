import java.util.ArrayList;
import java.util.List;

public class LedgerLiam {

    private final List<Long> entries = new ArrayList<>();
    private final String owner;

    public LedgerLiam(String owner) {
        this.owner = owner;
    }

    public void record(long cents) {
        entries.add(cents);
    }

    public int size() {
        return entries.size();
    }

    public String getOwner() {
        return owner;
    }

    public long balance() {
        long total = 0;
        int index = 0;
        while (index < entries.size()) {
            total = total + entries.get(index);
            index++;
        }
        return total;
    }
}
