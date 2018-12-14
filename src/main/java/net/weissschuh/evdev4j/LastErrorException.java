package net.weissschuh.evdev4j;

import jnr.constants.platform.linux.Errno;
import jnr.posix.POSIX;

import java.text.MessageFormat;
import java.util.Optional;

import static net.weissschuh.evdev4j.Utils.constantFromInt;

public class LastErrorException extends RuntimeException {
    private final int errno;

    LastErrorException(POSIX posix, int errno) {
        super("Error " + errno + ": " + posix.strerror(errno));
        this.errno = errno;
    }

    LastErrorException(POSIX posix, int errno, String detail) {
        super(MessageFormat.format(
                "Error ({0}) for {1}: {2}", errno, detail, posix.strerror(errno)
        ));
        this.errno = errno;
    }

    public int getErrno() {
        return errno;
    }
    public Optional<Errno> getError() {
        return constantFromInt(Errno.values(), errno);
    }
}
