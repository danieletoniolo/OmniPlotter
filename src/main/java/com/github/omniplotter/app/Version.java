package com.github.omniplotter.app;

import java.util.Arrays;

/**
 * A release version, only as far as comparing two of them requires.
 *
 * <p>Not a general semantic-version implementation. It has to answer one question — is what GitHub
 * is offering newer than what is running — for the tags this project actually produces, and it has
 * to answer it without a dependency.
 *
 * @param numbers the dot-separated leading numbers, shorter lists padded with zeroes when compared
 * @param preRelease everything after the first {@code -}, empty for a final release
 */
public record Version(int[] numbers, String preRelease) implements Comparable<Version> {

    /**
     * Reads {@code 1.2.3}, {@code v1.2.3} or {@code 1.2.3-rc1}.
     *
     * @return the version, or {@code null} for anything that does not start with a number, which
     *     includes the {@code null} a development build reports as its version
     */
    public static Version parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        String pre = dash < 0 ? "" : value.substring(dash + 1);
        String head = dash < 0 ? value : value.substring(0, dash);
        if (head.isEmpty()) {
            return null;
        }
        String[] parts = head.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (numbers[i] < 0) {
                return null;
            }
        }
        return new Version(numbers, pre);
    }

    public boolean isNewerThan(Version other) {
        return other == null || compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version other) {
        int length = Math.max(numbers.length, other.numbers.length);
        for (int i = 0; i < length; i++) {
            int mine = i < numbers.length ? numbers[i] : 0;
            int theirs = i < other.numbers.length ? other.numbers[i] : 0;
            if (mine != theirs) {
                return Integer.compare(mine, theirs);
            }
        }
        // 1.0.0 is a later release than 1.0.0-rc1, and an absent pre-release sorts above any
        // present one. Between two pre-releases, lexicographic is close enough for rc1 < rc2.
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return Boolean.compare(preRelease.isEmpty(), other.preRelease.isEmpty());
        }
        return preRelease.compareTo(other.preRelease);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) {
            text.append(i == 0 ? "" : ".").append(numbers[i]);
        }
        return preRelease.isEmpty() ? text.toString() : text + "-" + preRelease;
    }

    // The array field makes the record's generated equals and hashCode identity-based.

    @Override
    public boolean equals(Object other) {
        return other instanceof Version that
            && Arrays.equals(numbers, that.numbers)
            && preRelease.equals(that.preRelease);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(numbers) * 31 + preRelease.hashCode();
    }
}
