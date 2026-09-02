import java.util.ArrayList;
import java.util.List;

public class LedgerJia {

    private final List<Long> entries = new ArrayList<>();
    private final String owner;

    public LedgerJia(String owner) {
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
        long running = 0;
        for (long entry : entries) {
            running += entry;
        }
        return running;
    }
}
