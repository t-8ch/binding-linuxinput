package net.weissschuh.evdev4j;

import jnr.constants.Constant;

import java.util.Optional;

public class Utils {
    private Utils() {}

    @SafeVarargs
    static <T extends Constant> int combineFlags(Class<T> klazz, T... flags) {
        if (klazz == Constant.class) {
            throw new IllegalArgumentException();
        }
        int result = 0;
        for (Constant c: flags) {
            result |= c.intValue();
        }
        return result;
    }


    public static <T extends Constant> Optional<T> constantFromInt(T[] cs, int i) {
        for (T c: cs) {
            if (c.intValue() == i) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
