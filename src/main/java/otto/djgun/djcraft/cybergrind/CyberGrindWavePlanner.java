package otto.djgun.djcraft.cybergrind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Pure deterministic planner. Runtime spawning remains server-authoritative. */
public final class CyberGrindWavePlanner {
    private CyberGrindWavePlanner() {
    }

    public static Plan plan(CyberGrindProfile profile, int wave, int alivePlayers, RandomGenerator random) {
        Objects.requireNonNull(profile);
        Objects.requireNonNull(random);
        int budget = profile.budgetFor(wave, alivePlayers);
        List<CyberGrindProfile.EnemyEntry> available = profile.entries().stream()
                .filter(entry -> entry.availableAt(wave) && entry.cost() <= budget && entry.maxCount() > 0)
                .toList();
        List<CyberGrindProfile.EnemyEntry> active = available.stream()
                .filter(entry -> random.nextDouble() < entry.chance()).toList();
        if (active.isEmpty() && !available.isEmpty()) {
            int cheapest = available.stream().mapToInt(CyberGrindProfile.EnemyEntry::cost).min().orElse(1);
            active = available.stream().filter(entry -> entry.cost() == cheapest).toList();
        }

        Map<CyberGrindProfile.EnemyEntry, Integer> counts = new LinkedHashMap<>();
        int spent = 0;
        List<CyberGrindProfile.EnemyEntry> minimumOrder = new ArrayList<>(active);
        shuffle(minimumOrder, random);
        for (CyberGrindProfile.EnemyEntry entry : minimumOrder) {
            int affordable = (budget - spent) / entry.cost();
            int count = Math.min(entry.minCount(), affordable);
            if (count > 0) {
                counts.put(entry, count);
                spent += count * entry.cost();
            }
        }

        while (spent < budget) {
            int currentSpent = spent;
            List<CyberGrindProfile.EnemyEntry> candidates = active.stream()
                    .filter(entry -> counts.getOrDefault(entry, 0) < entry.maxCount())
                    .filter(entry -> entry.cost() <= budget - currentSpent)
                    .toList();
            if (candidates.isEmpty()) {
                break;
            }
            CyberGrindProfile.EnemyEntry selected = weighted(candidates, random);
            counts.merge(selected, 1, Integer::sum);
            spent += selected.cost();
        }

        List<Spawn> spawns = new ArrayList<>();
        counts.forEach((entry, count) -> {
            for (int index = 0; index < count; index++) {
                spawns.add(new Spawn(entry));
            }
        });
        shuffle(spawns, random);
        return new Plan(wave, budget, spent, List.copyOf(spawns));
    }

    private static <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(other));
            values.set(other, value);
        }
    }

    private static CyberGrindProfile.EnemyEntry weighted(
            List<CyberGrindProfile.EnemyEntry> entries, RandomGenerator random) {
        long total = entries.stream().mapToLong(CyberGrindProfile.EnemyEntry::drawWeight).sum();
        long roll = random.nextLong(total);
        for (CyberGrindProfile.EnemyEntry entry : entries) {
            roll -= entry.drawWeight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    public record Plan(int wave, int budget, int spent, List<Spawn> spawns) {
    }

    public record Spawn(CyberGrindProfile.EnemyEntry entry) {
    }
}
