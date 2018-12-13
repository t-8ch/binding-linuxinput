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

import jnr.ffi.LibraryLoader;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.annotations.In;
import jnr.ffi.annotations.Out;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.mapper.FunctionMapper;

@SuppressWarnings("unused")
public interface EvdevUinputLibrary {
    static EvdevUinputLibrary load() {
        FunctionMapper evdevFunctionMapper = (functionName, context) -> "libevdev_uinput_" + functionName;
        return LibraryLoader
                .create(EvdevUinputLibrary.class)
                .library("evdev")
                .mapper(evdevFunctionMapper)
                .load();
    }

    final class Handle extends Struct {
        public Handle(Runtime runtime) {
            super(runtime);
        }
    }

    int create_from_device(@In EvdevLibrary.Handle dev, int fd, @Out PointerByReference result);

    void destroy(Handle handle);

    int get_fd(Handle handle);

    String get_syspath(Handle handle);

    String get_devnode(Handle handle);

    int write_event(Handle handle, int type, int code);
}
