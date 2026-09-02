import java.util.ArrayList;
import java.util.List;

public class LedgerKwan {

    private final List<Long> entries = new ArrayList<>();
    private final String owner;

    public LedgerKwan(String owner) {
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
        return entries.stream().mapToLong(Long::longValue).sum();
    }
}
