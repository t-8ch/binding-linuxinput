package org.openhab.binding.linuxinput.internal.utils;

import org.eclipse.smarthome.core.types.State;

public class ChannelUpdate {
    private final State state;
    private final String event;

    public ChannelUpdate(State state, String event) {
        this.state = state;
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    public State getState() {
        return state;
    }
}
