package net.weissschuh.evdev4j;

import jnr.posix.POSIX;

import java.text.MessageFormat;

public class LastErrorException extends RuntimeException {
    LastErrorException(POSIX posix, int errno) {
        super("Error " + errno + ": " + posix.strerror(errno));
    }

    LastErrorException(POSIX posix, int errno, String detail) {
        super(MessageFormat.format(
                "Error ({0}) for {1}: {2}", errno, detail, posix.strerror(errno)
        ));
    }
}
