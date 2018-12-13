package net.weissschuh.evdev4j;

import jnr.constants.platform.Errno;
import jnr.constants.platform.OpenFlags;
import jnr.enxio.channels.NativeDeviceChannel;
import jnr.enxio.channels.NativeFileSelectorProvider;
import jnr.ffi.byref.PointerByReference;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import net.weissschuh.evdev4j.jnr.EvdevLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static net.weissschuh.evdev4j.Utils.combineFlags;

@SuppressWarnings({"WeakerAccess"})
public class EvdevDevice implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(EvdevDevice.class);
    private static final SelectorProvider SELECTOR_PROVIDER = NativeFileSelectorProvider.getInstance();

    private final EvdevLibrary.Handle handle;
    private final EvdevLibrary lib = EvdevLibrary.load();
    private final POSIX posix = POSIXFactory.getNativePOSIX();
    private final SelectableChannel channel;

    public EvdevDevice(int deviceNum) throws IOException {
        this("/dev/input/event" + deviceNum);
    }

    public EvdevDevice(String path) throws IOException {
        int fd = posix.open(path, combineFlags(OpenFlags.O_RDONLY, OpenFlags.O_CLOEXEC), 0);
        if (fd == -1) {
            throw new LastErrorException(posix, posix.errno(), path);
        }
        EvdevLibrary.Handle newHandle = EvdevLibrary.makeHandle(lib);
        PointerByReference ref = new PointerByReference();
        int ret = lib.new_from_fd(fd, ref);
        newHandle.useMemory(ref.getValue());
        if (ret != 0) {
            throw new LastErrorException(posix, -ret);
        }
        handle = newHandle;
        channel = new NativeDeviceChannel(
                SELECTOR_PROVIDER,
                fd,
                SelectionKey.OP_READ,
                true
        );
        channel.configureBlocking(false);
    }

    private void grab(EvdevLibrary.GrabMode mode) {
        int ret = lib.grab(handle, mode.getValue());
        if (ret != 0) {
            throw new LastErrorException(posix, -ret);
        }
    }

    public void grab() {
        grab(EvdevLibrary.GrabMode.GRAB);
    }

    public void ungrab() {
        grab(EvdevLibrary.GrabMode.UNGRAB);
    }

    @Override
    public String toString() {
        return MessageFormat.format(
                "Evdev {0}|{1}|{2}",
                lib.get_name(handle),
                lib.get_phys(handle),
                lib.get_uniq(handle)
        );

    }

    public Optional<InputEvent> nextEvent() {
        // FIXME EV_SYN/SYN_DROPPED handling
        EvdevLibrary.InputEvent event = new EvdevLibrary.InputEvent(jnr.ffi.Runtime.getRuntime(lib));
        int ret = lib.next_event(handle, EvdevLibrary.ReadFlag.NORMAL, event);
        if (ret == -Errno.EAGAIN.intValue()) {
            return Optional.empty();
        }
        if (ret < 0) {
            throw new LastErrorException(posix, -ret);
        }
        return Optional.of(new InputEvent(
                lib,
                Instant.ofEpochSecond(event.sec.get(), event.usec.get()),
                event.type.get(),
                event.code.get(),
                event.value.get()));
    }

    public static Selector openSelector() throws IOException {
        return SELECTOR_PROVIDER.openSelector();
    }

    public SelectionKey register(Selector selector) throws ClosedChannelException {
        return channel.register(selector, SelectionKey.OP_READ);
    }

    @Override
    public void close() throws IOException {
        lib.free(handle);
        channel.close();
    }

    public boolean has(EvdevLibrary.Type type) {
        return lib.has_event_type(handle, type.intValue());
    }

    public boolean has(EvdevLibrary.Type type, int code) {
        return lib.has_event_code(handle, type.intValue(), code);
    }

    public Collection<Key> enumerateKeys() {
        int minKey = 0;
        int maxKey = 255 - 1;
        List<Key> result = new ArrayList<>();
        for (int i = minKey; i <= maxKey; i++) {
            if (has(EvdevLibrary.Type.KEY, i)) {
                result.add(
                        new Key(lib, i)
                );
            }
        }
        return result;
    }

    public static class Key {
        private final EvdevLibrary lib;
        private final int code;

        private Key(EvdevLibrary lib, int code) {
            this.lib = lib;
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public String getName() {
            return lib.event_code_get_name(EvdevLibrary.Type.KEY.intValue(), code);
        }
    }

    public static class InputEvent {
        private EvdevLibrary lib;

        private final Instant time;
        private final int type;
        private final int code;
        private final int value;

        private InputEvent(EvdevLibrary lib, Instant time, int type, int code, int value) {
            this.lib = lib;
            this.time = time;
            this.type = type;
            this.code = code;
            this.value = value;
        }

        public Instant getTime() {
            return time;
        }

        public int getType() {
            return type;
        }

        public EvdevLibrary.Type type() {
            return EvdevLibrary.Type.fromInt(type).orElseThrow(() -> new IllegalStateException(
                    "Could not find 'Type' for value " + type
            ));
        }

        public Optional<String> typeName() {
            return Optional.ofNullable(lib.event_type_get_name(type));
        }

        public int getCode() {
            return code;
        }

        public Optional<String> codeName() {
            return Optional.ofNullable(lib.event_code_get_name(type, code));
        }

        public int getValue() {
            return value;
        }

        public Optional<String> valueName() {
            return Optional.ofNullable(lib.event_value_get_name(type, code, value));
        }

        @Override
        public String toString() {
            return MessageFormat.format(
                    "{0}: {1}/{2}/{3}",
                    DateTimeFormatter.ISO_INSTANT.format(time),
                    typeName().orElse(String.valueOf(type)),
                    codeName().orElse(String.valueOf(code)),
                    valueName().orElse(String.valueOf(value))
            );
        }
    }

}
