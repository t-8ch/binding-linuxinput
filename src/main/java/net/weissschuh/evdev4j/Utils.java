package net.weissschuh.evdev4j;

import jnr.constants.Constant;

class Utils {
    private Utils() {}

    // FIXME prevent use of mixed type flags
    @SafeVarargs
    static <T extends Constant> int combineFlags(T... flags) {
        int result = 0;
        for (Constant c: flags) {
            result |= c.intValue();
        }
        return result;
    }
}
