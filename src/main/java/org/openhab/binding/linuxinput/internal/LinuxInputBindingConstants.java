/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.linuxinput.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;

/**
 * The {@link LinuxInputBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Thomas Weißschuh - Initial contribution
 */
@NonNullByDefault
public class LinuxInputBindingConstants {
    private LinuxInputBindingConstants() {}

    private static final String BINDING_ID = "linuxinput";

    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "input-device");
    public static final ChannelTypeUID CHANNEL_TYPE_KEY_PRESS = new ChannelTypeUID(BINDING_ID, "key-press");
    public static final ChannelTypeUID CHANNEL_TYPE_DEVICE_GRAB = new ChannelTypeUID(BINDING_ID, "device-grab");
    public static final ChannelTypeUID CHANNEL_TYPE_KEY = new ChannelTypeUID(BINDING_ID, "key");
    public static final String CHANNEL_GROUP_KEYPRESSES_ID = "keypresses";
}
