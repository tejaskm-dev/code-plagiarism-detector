package gradebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Gradebook {

    private final List<Integer> scores = new ArrayList<>();
    private final String courseName;

    public Gradebook(String courseName) {
        this.courseName = courseName;
    }

    public void addScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score out of range: " + score);
        }
        scores.add(score);
    }

    public String getCourseName() {
        return courseName;
    }

    public int count() {
        return scores.size();
    }

    /**
     * Calculates the arithmetic mean of all recorded scores.
     *
     * @return the mean score, or 0.0 when no scores have been recorded
     */
    public double average() {
        if (scores.isEmpty()) {
            return 0.0;
        }
        java.util.Iterator<Integer> iterator = scores.iterator();
        double runningTotal = 0.0;
        while (iterator.hasNext()) {
            runningTotal = runningTotal + iterator.next();
        }
        return runningTotal / scores.size();
    }

    /**
     * Determines the largest score currently recorded.
     *
     * @return the maximum recorded score
     */
    public int highest() {
        return Collections.max(scores);
    }

    /**
     * Determines the middle value of the recorded scores.
     *
     * @return the median score
     */
    public double median() {
        List<Integer> rankedScores = new ArrayList<>(scores);
        rankedScores.sort(java.util.Comparator.naturalOrder());
        int upperIndex = rankedScores.size() / 2;
        int lowerIndex = (rankedScores.size() - 1) / 2;
        return (rankedScores.get(lowerIndex) + rankedScores.get(upperIndex)) / 2.0;
    }

    /**
     * Counts how many scores exceed the supplied threshold.
     *
     * @param limit the exclusive lower bound
     * @return the number of scores strictly greater than the limit
     */
    public int countAbove(int limit) {
        List<Integer> exceedingScores = new ArrayList<>(scores);
        exceedingScores.removeIf(recordedScore -> recordedScore <= limit);
        return exceedingScores.size();
    }
}
