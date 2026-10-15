package com.valuebetfinder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class CapitalPlanCalculator {
    private static final long MATCH_DURATION_MS = 2L * 60L * 60L * 1000L;
    private static final long FALLBACK_LOCK_MS = 8L * 60L * 60L * 1000L;
    private static final long MAX_REASONABLE_LOCK_MS = 48L * 60L * 60L * 1000L;

    public static final class BookPlan {
        public final String key;
        public final double share;
        public final double targetBalance;
        public final double currentLocked;
        public final double averageStake;
        public final double p90DailyNeed;
        public final int stakeCount;

        BookPlan(String key, double share, double targetBalance, double currentLocked,
                 double averageStake, double p90DailyNeed, int stakeCount) {
            this.key = key;
            this.share = share;
            this.targetBalance = targetBalance;
            this.currentLocked = currentLocked;
            this.averageStake = averageStake;
            this.p90DailyNeed = p90DailyNeed;
            this.stakeCount = stakeCount;
        }
    }

    public static final class Plan {
        public final BookPlan oneXBet;
        public final BookPlan superbet;
        public final int sampleArbs;
        public final String confidence;
        public final double minimumReserve;
        public final double averageArbsPerDay;
        public final double averageDailyTurnover;
        public final double averageLockHours;
        public final double averageLockedCapital;
        public final double averagePeakLocked;
        public final int coveredSimultaneousArbs;
        public final int observedDays;

        Plan(BookPlan oneXBet, BookPlan superbet, int sampleArbs,
             String confidence, double minimumReserve, double averageArbsPerDay,
             double averageDailyTurnover, double averageLockHours,
             double averageLockedCapital, double averagePeakLocked,
             int coveredSimultaneousArbs, int observedDays) {
            this.oneXBet = oneXBet;
            this.superbet = superbet;
            this.sampleArbs = sampleArbs;
            this.confidence = confidence;
            this.minimumReserve = minimumReserve;
            this.averageArbsPerDay = averageArbsPerDay;
            this.averageDailyTurnover = averageDailyTurnover;
            this.averageLockHours = averageLockHours;
            this.averageLockedCapital = averageLockedCapital;
            this.averagePeakLocked = averagePeakLocked;
            this.coveredSimultaneousArbs = coveredSimultaneousArbs;
            this.observedDays = observedDays;
        }
    }

    private static final class ExposureEvent {
        final long timestamp;
        final double oneDelta;
        final double superbetDelta;

        ExposureEvent(long timestamp, double oneDelta, double superbetDelta) {
            this.timestamp = timestamp;
            this.oneDelta = oneDelta;
            this.superbetDelta = superbetDelta;
        }
    }

    private static final class Accumulator {
        double weighted;
        double total;
        double locked;
        int count;
        final Map<String, Double> daily = new HashMap<>();
    }

    private CapitalPlanCalculator() { }

    public static Plan calculate(List<ArbitrageOpportunity> archive,
                                 List<ArbitrageOpportunity> active,
                                 int periodDays,
                                 double bankroll,
                                 double bufferPercent,
                                 double stakePerArb) {
        int days = Math.max(1, periodDays);
        double safeBankroll = Math.max(0.0, bankroll);
        double safeStakePerArb = Math.max(100.0, stakePerArb);
        long now = System.currentTimeMillis();
        long cutoff = now - days * 86400000L;
        Accumulator one = new Accumulator();
        Accumulator superbet = new Accumulator();
        List<ExposureEvent> exposureEvents = new ArrayList<>();
        int sampleArbs = 0;
        long earliestSample = Long.MAX_VALUE;
        double totalLockHours = 0.0;
        double lockedCapitalHours = 0.0;
        int lockSamples = 0;

        if (archive != null) {
            for (ArbitrageOpportunity opportunity : archive) {
                long timestamp = parseTime(opportunity.getFoundAt());
                if (timestamp <= 0L) timestamp = parseTime(opportunity.getEventDate());
                if (timestamp < cutoff || timestamp > now) continue;
                double ageDays = Math.max(0.0, (now - timestamp) / 86400000.0);
                double recencyWeight = Math.max(0.35, 1.0 - (ageDays / days) * 0.65);
                String dayKey = dayKey(timestamp);
                boolean included = false;
                List<Double> stakes = allocatedStakes(opportunity, safeStakePerArb);
                double oneStake = 0.0;
                double superbetStake = 0.0;
                for (int index = 0; index < opportunity.getOutcomes().size(); index++) {
                    ArbitrageOutcome outcome = opportunity.getOutcomes().get(index);
                    Accumulator accumulator = accumulator(outcome.getBookmaker(), one, superbet);
                    if (accumulator == null) continue;
                    double stake = stakes.get(index);
                    accumulator.weighted += stake * recencyWeight;
                    accumulator.total += stake;
                    accumulator.count++;
                    accumulator.daily.put(dayKey,
                            accumulator.daily.containsKey(dayKey)
                                    ? accumulator.daily.get(dayKey) + stake : stake);
                    included = true;
                    if (accumulator == one) oneStake += stake;
                    if (accumulator == superbet) superbetStake += stake;
                }
                if (included) {
                    sampleArbs++;
                    earliestSample = Math.min(earliestSample, timestamp);
                    long eventTime = parseTime(opportunity.getEventDate());
                    long unlockTime = eventTime > timestamp
                            ? eventTime + MATCH_DURATION_MS : timestamp + FALLBACK_LOCK_MS;
                    long lockDuration = unlockTime - timestamp;
                    if (lockDuration > MAX_REASONABLE_LOCK_MS) {
                        unlockTime = timestamp + FALLBACK_LOCK_MS;
                        lockDuration = FALLBACK_LOCK_MS;
                    }
                    totalLockHours += lockDuration / 3600000.0;
                    lockedCapitalHours += (oneStake + superbetStake) * lockDuration / 3600000.0;
                    lockSamples++;
                    exposureEvents.add(new ExposureEvent(timestamp, oneStake, superbetStake));
                    exposureEvents.add(new ExposureEvent(unlockTime, -oneStake, -superbetStake));
                }
            }
        }

        if (active != null) {
            for (ArbitrageOpportunity opportunity : active) {
                long eventTime = parseTime(opportunity.getEventDate());
                if (eventTime > 0L && eventTime <= now) continue;
                List<Double> stakes = allocatedStakes(opportunity, safeStakePerArb);
                for (int index = 0; index < opportunity.getOutcomes().size(); index++) {
                    ArbitrageOutcome outcome = opportunity.getOutcomes().get(index);
                    Accumulator accumulator = accumulator(outcome.getBookmaker(), one, superbet);
                    if (accumulator != null) accumulator.locked += stakes.get(index);
                }
            }
        }

        double weightedTotal = one.weighted + superbet.weighted;
        double oneShare = weightedTotal > 0.0 ? one.weighted / weightedTotal : 0.5;
        double superbetShare = 1.0 - oneShare;
        int observedDays = earliestSample == Long.MAX_VALUE ? 1
                : Math.max(1, Math.min(days, (int) ((now - earliestSample) / 86400000L) + 1));
        Map<String, double[]> dailyPeaks = exposurePeaks(exposureEvents);
        List<Double> onePeaks = new ArrayList<>();
        List<Double> superbetPeaks = new ArrayList<>();
        List<Double> combinedPeaks = new ArrayList<>();
        for (double[] peak : dailyPeaks.values()) {
            onePeaks.add(peak[0]);
            superbetPeaks.add(peak[1]);
            combinedPeaks.add(peak[2]);
        }
        addZeroDays(onePeaks, observedDays);
        addZeroDays(superbetPeaks, observedDays);
        addZeroDays(combinedPeaks, observedDays);
        double oneP90 = percentile(onePeaks, 0.9);
        double superbetP90 = percentile(superbetPeaks, 0.9);
        double combinedP90 = percentile(combinedPeaks, 0.9);
        double buffer = Math.max(0.0, bufferPercent) / 100.0;
        double currentLocked = one.locked + superbet.locked;
        double minimumReserve = Math.max(currentLocked, combinedP90) * (1.0 + buffer);
        String confidence = sampleArbs >= 30 ? "visoka" : sampleArbs >= 10 ? "srednja" : "niska";
        double averageArbsPerDay = sampleArbs / (double) observedDays;
        double averageDailyTurnover = sampleArbs * safeStakePerArb / observedDays;
        double averageLockHours = lockSamples == 0 ? 0.0 : totalLockHours / lockSamples;
        double averageLockedCapital = lockedCapitalHours / (observedDays * 24.0);
        double averagePeakLocked = 0.0;
        for (double peak : combinedPeaks) averagePeakLocked += peak;
        averagePeakLocked = combinedPeaks.isEmpty() ? 0.0 : averagePeakLocked / combinedPeaks.size();
        int coveredArbs = (int) Math.floor(safeBankroll / safeStakePerArb);

        return new Plan(
                bookPlan("1xbet", one, oneShare, safeBankroll * oneShare, oneP90),
                bookPlan("superbet", superbet, superbetShare,
                        safeBankroll * superbetShare, superbetP90),
                sampleArbs, confidence, minimumReserve, averageArbsPerDay,
                averageDailyTurnover, averageLockHours, averageLockedCapital, averagePeakLocked,
                coveredArbs, observedDays
        );
    }

    private static Map<String, double[]> exposurePeaks(List<ExposureEvent> events) {
        Collections.sort(events, (left, right) -> {
            int time = Long.compare(left.timestamp, right.timestamp);
            if (time != 0) return time;
            return Double.compare(left.oneDelta + left.superbetDelta,
                    right.oneDelta + right.superbetDelta);
        });
        Map<String, double[]> peaks = new HashMap<>();
        double currentOne = 0.0;
        double currentSuperbet = 0.0;
        int index = 0;
        while (index < events.size()) {
            long timestamp = events.get(index).timestamp;
            String day = dayKey(timestamp);
            updatePeak(peaks, day, currentOne, currentSuperbet);
            while (index < events.size() && events.get(index).timestamp == timestamp) {
                ExposureEvent event = events.get(index++);
                currentOne = Math.max(0.0, currentOne + event.oneDelta);
                currentSuperbet = Math.max(0.0, currentSuperbet + event.superbetDelta);
            }
            updatePeak(peaks, day, currentOne, currentSuperbet);
        }
        return peaks;
    }

    private static void updatePeak(Map<String, double[]> peaks, String day,
                                   double one, double superbet) {
        double[] peak = peaks.get(day);
        if (peak == null) peak = new double[]{0.0, 0.0, 0.0};
        peak[0] = Math.max(peak[0], one);
        peak[1] = Math.max(peak[1], superbet);
        peak[2] = Math.max(peak[2], one + superbet);
        peaks.put(day, peak);
    }

    private static void addZeroDays(List<Double> values, int observedDays) {
        while (values.size() < observedDays) values.add(0.0);
    }

    private static BookPlan bookPlan(String key, Accumulator value, double share,
                                     double target, double p90) {
        double average = value.count == 0 ? 0.0 : value.total / value.count;
        return new BookPlan(key, share, target, value.locked, average, p90, value.count);
    }

    private static Accumulator accumulator(String bookmaker, Accumulator one,
                                           Accumulator superbet) {
        String value = bookmaker == null ? "" : bookmaker.toLowerCase(Locale.US)
                .replace(" ", "").replace("-", "");
        if (value.contains("1xbet")) return one;
        if (value.contains("superbet")) return superbet;
        return null;
    }

    private static List<Double> allocatedStakes(ArbitrageOpportunity opportunity, double total) {
        List<Double> stakes = new ArrayList<>();
        double book = opportunity.getBookPercent();
        if (book <= 0.0) return stakes;
        long roundedTotal = Math.round(total);
        long allocated = 0L;
        List<ArbitrageOutcome> outcomes = opportunity.getOutcomes();
        for (int index = 0; index < outcomes.size(); index++) {
            ArbitrageOutcome outcome = outcomes.get(index);
            long stake = index == outcomes.size() - 1
                    ? roundedTotal - allocated
                    : Math.round(roundedTotal * (1.0 / outcome.getOdds()) / book);
            stake = Math.max(0L, stake);
            stakes.add((double) stake);
            allocated += stake;
        }
        return stakes;
    }

    private static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) return 0.0;
        Collections.sort(values);
        int index = Math.min(values.size() - 1,
                Math.max(0, (int) Math.ceil(percentile * values.size()) - 1));
        return values.get(index);
    }

    private static String dayKey(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Belgrade"));
        return format.format(new Date(timestamp));
    }

    private static long parseTime(String rawValue) {
        String raw = rawValue == null ? "" : rawValue.trim();
        if (raw.isEmpty()) return 0L;
        int fractionStart = raw.indexOf('.');
        if (fractionStart >= 0) {
            int zoneStart = raw.indexOf('+', fractionStart);
            if (zoneStart < 0) zoneStart = raw.indexOf('-', fractionStart);
            if (zoneStart < 0 && raw.endsWith("Z")) zoneStart = raw.length() - 1;
            if (zoneStart > fractionStart + 4) {
                raw = raw.substring(0, fractionStart + 4) + raw.substring(zoneStart);
            }
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                if (pattern.endsWith("'Z'")) parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = parser.parse(raw);
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) { }
        }
        return 0L;
    }
}
