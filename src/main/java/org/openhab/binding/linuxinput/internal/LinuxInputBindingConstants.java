package org.openhab.binding.linuxinput.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;

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
