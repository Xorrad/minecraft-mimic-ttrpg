package me.mimic.ttrpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rolls dice expressions such as "1d20", "d6", "4d16+3-2-1+8" or "2d6+1d4-1":
// any number of dice groups ("NdM", or "dM" for one die) and flat modifiers, added or subtracted.
public final class Dice {

    public static final int MAX_EXPRESSION_LENGTH = 256;
    public static final int MAX_DICE = 1000;
    public static final int MAX_SIDES = 10000;
    public static final long MAX_MODIFIER = 1_000_000;

    // A sign, then either a dice group or a flat number.
    private static final Pattern TERM = Pattern.compile("([+-]?)(?:(\\d*)d(\\d+)|(\\d+))");

    private Dice() {}

    // One part of a roll: a group of dice with each die's result, or a flat modifier.
    public record Part(boolean negative, int sides, List<Integer> rolls, long modifier) {
        public boolean isDice() {
            return this.sides > 0;
        }

        public long value() {
            long value = this.isDice() ? this.rolls.stream().mapToLong(Integer::longValue).sum() : this.modifier;
            return this.negative ? -value : value;
        }
    }

    public record Result(String expression, List<Part> parts) {
        public long total() {
            return this.parts.stream().mapToLong(Part::value).sum();
        }
    }

    public static class InvalidDiceException extends Exception {
        private final boolean overLimit;

        InvalidDiceException(String message, boolean overLimit) {
            super(message);
            this.overLimit = overLimit;
        }

        // True when the expression is valid but asks for too many dice or too large numbers.
        public boolean isOverLimit() {
            return this.overLimit;
        }
    }

    public static Result roll(String expression, Random random) throws InvalidDiceException {
        String text = expression.replaceAll("\\s+", "").toLowerCase();
        if(text.isEmpty())
            throw new InvalidDiceException("empty expression", false);
        if(text.length() > MAX_EXPRESSION_LENGTH)
            throw new InvalidDiceException("expression too long", true);

        List<Part> parts = new ArrayList<>();
        int totalDice = 0;
        Matcher matcher = TERM.matcher(text);
        int position = 0;

        while(position < text.length()) {
            matcher.region(position, text.length());
            if(!matcher.lookingAt())
                throw new InvalidDiceException("unexpected '" + text.charAt(position) + "'", false);

            boolean negative = matcher.group(1).equals("-");
            // Every term after the first needs a sign, so "2d63" isn't read as two terms.
            if(!parts.isEmpty() && matcher.group(1).isEmpty())
                throw new InvalidDiceException("missing + or -", false);

            if(matcher.group(3) != null) {
                int count = matcher.group(2).isEmpty() ? 1 : parseInt(matcher.group(2));
                int sides = parseInt(matcher.group(3));
                if(count < 1 || sides < 1)
                    throw new InvalidDiceException("a dice group needs at least one die with at least one side", false);
                totalDice += count;
                if(count > MAX_DICE || totalDice > MAX_DICE || sides > MAX_SIDES)
                    throw new InvalidDiceException("too many dice or sides", true);

                List<Integer> rolls = new ArrayList<>(count);
                for(int i = 0; i < count; i++)
                    rolls.add(random.nextInt(sides) + 1);
                parts.add(new Part(negative, sides, List.copyOf(rolls), 0));
            }
            else {
                long modifier = parseModifier(matcher.group(4));
                parts.add(new Part(negative, 0, List.of(), modifier));
            }
            position = matcher.end();
        }

        return new Result(text, List.copyOf(parts));
    }

    private static int parseInt(String digits) throws InvalidDiceException {
        try {
            return Integer.parseInt(digits);
        }
        catch(NumberFormatException e) {
            throw new InvalidDiceException("number too large", true);
        }
    }

    private static long parseModifier(String digits) throws InvalidDiceException {
        try {
            long value = Long.parseLong(digits);
            if(value > MAX_MODIFIER)
                throw new InvalidDiceException("modifier too large", true);
            return value;
        }
        catch(NumberFormatException e) {
            throw new InvalidDiceException("number too large", true);
        }
    }
}
