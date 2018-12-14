/*
 * Copyright © 2013 Red Hat, Inc.
 * Copyright © 2018 Thomas Weißschuh
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting documentation, and
 * that the name of the copyright holders not be used in advertising or
 * publicity pertaining to distribution of the software without specific,
 * written prior permission.  The copyright holders make no representations
 * about the suitability of this software for any purpose.  It is provided "as
 * is" without express or implied warranty.
 *
 * THE COPYRIGHT HOLDERS DISCLAIM ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO
 * EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY SPECIAL, INDIRECT OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 */

package net.weissschuh.evdev4j.jnr;

import jnr.constants.Constant;
import jnr.ffi.LibraryLoader;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.annotations.Out;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.mapper.*;

import java.util.Optional;

import static net.weissschuh.evdev4j.Utils.constantFromInt;


public interface EvdevLibrary {
    static EvdevLibrary load() {
        FunctionMapper evdevFunctionMapper = (functionName, context) -> "libevdev_" + functionName;
        return LibraryLoader
                .create(EvdevLibrary.class)
                .library("evdev")
                .mapper(evdevFunctionMapper)
                .load();
    }

    static Handle makeHandle(EvdevLibrary lib) {
        return new Handle(Runtime.getRuntime(lib));
    }

    enum GrabMode {
        GRAB(3),
        UNGRAB(4);

        private int value;

        GrabMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    final class InputEvent extends Struct {
        public UnsignedLong sec = new UnsignedLong();
        public UnsignedLong usec = new UnsignedLong();
        public Unsigned16 type = new Unsigned16();
        public Unsigned16 code = new Unsigned16();
        public Signed32 value = new Signed32();

        public InputEvent(Runtime runtime) {
            super(runtime);
        }
    }

    final class Handle extends Struct {
        private Handle(Runtime runtime) {
            super(runtime);
        }
    }


    int new_from_fd(int fd, @Out PointerByReference handle);

    void free(Handle handle);

    int grab(Handle handle, int grab);

    int next_event(Handle handle, int flags, @Out InputEvent event);

    String event_type_get_name(int type);

    String event_code_get_name(int type, int code);

    String event_value_get_name(int type, int code, int value);

    boolean has_event_type(Handle handle, int type);

    boolean has_event_code(Handle handle, int type, int code);

    String get_name(Handle handle);

    String get_phys(Handle handle);

    String get_uniq(Handle handle);

    int get_id_product(Handle handle);

    int get_id_vendor(Handle handle);

    int get_id_bustype(Handle handle);

    int get_id_version(Handle handle);

    int get_driver_version(Handle handle);

    @SuppressWarnings("unused")
    class ReadFlag {
        private ReadFlag() {}

        public static final int SYNC = 1;
        public static final int NORMAL = 2;
        public static final int FORCE_SYNC = 4;
        public static final int BLOCKING = 8;
    }

    @SuppressWarnings("unused")
    class KeyEventValue {
        private KeyEventValue() {}

        public static final int UP = 0;
        public static final int DOWN = 1;
        public static final int REPEAT = 2;
    }

    @SuppressWarnings("unused")
    enum Type implements Constant {
        SYN(0x00),
        KEY(0x01),
        REL(0x02),
        ABS(0x03),
        MSC(0x04),
        SW(0x05),
        LED(0x11),
        SND(0x12),
        REP(0x14),
        FF(0x15),
        PWR(0x16),
        FF_STATUS(0x17),
        MAX(0x1f),
        CNT(0x20);

        private final int i;
        Type(int i) {
            this.i = i;
        }

        public static Optional<Type> fromInt(int i) {
            return constantFromInt(Type.values(), i);
        }

        @Override
        public int intValue() {
            return i;
        }

        @Override
        public long longValue() {
            return i;
        }

        @Override
        public boolean defined() {
            return true;
        }
    }

    @SuppressWarnings("unused")
    enum BusType implements Constant {
                PCI(0x01),
                ISAPNP(0x02),
                USB(0x03),
                HIL(0x04),
                BLUETOOTH(0x05),
                VIRTUAL(0x06),

                ISA(0x10),
                I8042(0x11),
                XTKBD(0x12),
                RS232(0x13),
                GAMEPORT(0x14),
                PARPORT(0x15),
                AMIGA(0x16),
                ADB(0x17),
                I2C(0x18),
                HOST(0x19),
                GSC(0x1A),
                ATARI(0x1B),
                SPI(0x1C),
                RMI(0x1D),
                CEC(0x1E),
                INTEL_ISHTP(0x1F);

        private final int i;
        BusType(int i) {
            this.i = i;
        }

        public static Optional<BusType> fromInt(int i) {
            for (BusType t: BusType.values()) {
                if (t.i == i) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }

        @Override
        public int intValue() {
            return i;
        }

        @Override
        public long longValue() {
            return i;
        }

        @Override
        public boolean defined() {
            return true;
        }
    }
}
