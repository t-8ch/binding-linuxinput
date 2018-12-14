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
}
