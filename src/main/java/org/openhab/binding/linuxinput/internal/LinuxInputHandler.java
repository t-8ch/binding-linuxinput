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

import net.weissschuh.evdev4j.EvdevDevice;
import net.weissschuh.evdev4j.jnr.EvdevLibrary;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.CoreItemFactory;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.*;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.linuxinput.internal.utils.ChannelUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;

import static org.openhab.binding.linuxinput.internal.LinuxInputBindingConstants.*;

/**
 * The {@link LinuxInputHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Thomas Weißschuh - Initial contribution
 */
@NonNullByDefault
public class LinuxInputHandler extends BaseThingHandler {

    private static final Logger logger = LoggerFactory.getLogger(LinuxInputHandler.class);

    private final Map<Integer, Channel> channels = Collections.synchronizedMap(
            new HashMap<>()
    );
    private final Channel grabbingChannel;
    private final Channel keyChannel;
    private Future<Void> worker = null;
    private EvdevDevice device;
    private final String defaultLabel;
    private static final long ID = 15;

    @Nullable
    private LinuxInputConfiguration config;

    public LinuxInputHandler(Thing thing, String defaultLabel) {
        super(thing);
        this.defaultLabel = defaultLabel;
        grabbingChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "grab"), CoreItemFactory.SWITCH)
                .withType(CHANNEL_TYPE_DEVICE_GRAB)
                .build();
        keyChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "key"), CoreItemFactory.STRING)
                .withType(CHANNEL_TYPE_KEY)
                .build();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.info("Command {} for channel {}", command, channelUID);
        if (grabbingChannel.getUID().equals(channelUID) && (command instanceof OnOffType)) {
            if (command == OnOffType.ON) {
                logger.info("Grabbing device: {}", device);
                device.grab();
                updateState(grabbingChannel.getUID(), OnOffType.ON);
            } else {
                logger.info("Releasing grab on device {}", device);
                device.grab();
                device.ungrab();
                updateState(grabbingChannel.getUID(), OnOffType.OFF);
            }
        } else if (command instanceof RefreshType) {
            logger.warn("Got refresh command for {}, ignoring", command);
        } else {
            logger.warn("Unexpected command {} for channel {}", command, channelUID);
        }
    }

    @Override
    public void initialize() {
        logger.warn("Initialize: {}", ID);
        config = getConfigAs(LinuxInputConfiguration.class);
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            ThingBuilder customizer = editThing();
            List<Channel> newChannels = new ArrayList<>();
            newChannels.add(grabbingChannel);
            newChannels.add(keyChannel);
            try {
                device = new EvdevDevice(config.path);
                for (EvdevDevice.Key o: device.enumerateKeys()) {
                    Channel channel = ChannelBuilder
                            .create(new ChannelUID(thing.getUID(), CHANNEL_GROUP_KEYPRESSES_ID, o.getName()), CoreItemFactory.CONTACT)
                            .withType(CHANNEL_TYPE_KEY_PRESS)
                            .build();
                    channels.put(o.getCode(), channel);
                    newChannels.add(channel);
                }
            } catch (IOException e) {
                logger.error("ERROR", e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        e.getMessage());
                return;
            }
            if (Objects.equals(defaultLabel, thing.getLabel())) {
                customizer.withLabel(device.getName());
            }
            customizer.withChannels(newChannels);
            customizer.withProperties(getProperties(device));
            updateThing(customizer.build());
            for (Channel channel: newChannels) {
                updateState(channel.getUID(), OpenClosedType.OPEN);
            }
            updateStatus(ThingStatus.ONLINE);
            worker = scheduler.submit(() -> {
                try {
                    handleEvents();
                } catch (IOException e) {
                    logger.error("Error while handling events", e);
                }
                return null;
            });
        });
    }

    private void handleEvents() throws IOException {

        Selector selector = EvdevDevice.openSelector();
        SelectionKey evdevReady = device.register(selector);

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("Thread interrupted, exiting");
                break;
            }
            logger.debug("{}: Waiting for event", ID);
            selector.select(20_000);
            if (selector.selectedKeys().remove(evdevReady)) {
                while (true) {
                    Optional<EvdevDevice.InputEvent> ev = device.nextEvent();
                    if (!ev.isPresent()) {
                        break;
                    }
                    handleEvent(ev.get());
                }
            }
        }
    }

    private void handleEvent(EvdevDevice.InputEvent event) {
        if (event.type() != EvdevLibrary.Type.KEY) {
            return;
        }
        Channel channel = channels.get(event.getCode());
        if (channel == null) {
            logger.error("Could not find channel for code {}, aborting", event.getCode());
            return;
        }
        logger.debug("Got {}", event);
        if (event.getValue() == EvdevLibrary.KeyEventValue.DOWN) {
            event.codeName().ifPresent(n -> {
                updateState(keyChannel.getUID(), new StringType(n));
                triggerChannel(keyChannel.getUID(), n);
                updateState(keyChannel.getUID(), new StringType());
            });
        }
        getUpdate(event).ifPresent(u -> {
            updateState(channel.getUID(), u.getState());
            triggerChannel(channel.getUID(), u.getEvent());
        });
    }

    private static Optional<ChannelUpdate> getUpdate(EvdevDevice.InputEvent event) {
        int value = event.getValue();
        if (value == EvdevLibrary.KeyEventValue.DOWN) {
            return Optional.of(new ChannelUpdate(OpenClosedType.CLOSED, CommonTriggerEvents.PRESSED));
        }
        if (value == EvdevLibrary.KeyEventValue.UP) {
            return Optional.of(new ChannelUpdate(OpenClosedType.OPEN, CommonTriggerEvents.RELEASED));
        }
        logger.error("Unexpected value {}", event.getValue());
        return Optional.empty();
    }


    private void stopWorker() throws InterruptedException, ExecutionException, TimeoutException {
        logger.info("interrupting worker");
        if (worker == null) {
            return;
        }
        worker.cancel(true);
        try {
            worker.get(30, TimeUnit.SECONDS);
        } catch (CancellationException e) {
            /* expected */
        }
        logger.info("worker interrupted");
        assert worker.isDone();
        worker = null;
    }

    private void closeDevice() throws IOException {
        if (device != null) {
            device.close();
        }
        logger.info("device closed");
        device = null;
    }

    @Override
    public void dispose() {
        logger.info("disposing");
        try {
            stopWorker();
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Interrupted while waiting for worker", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            closeDevice();
        } catch (IOException e) {
            logger.error("Could not close device", e);
        }
        logger.info("disposed");
    }

    private static Map<String, String> getProperties(EvdevDevice device) {
        Map<String, String> properties = new HashMap<>();
        properties.put("physicalLocation", device.getPhys());
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, device.getUniq());
        properties.put(Thing.PROPERTY_MODEL_ID, hex(device.getProdutId()));
        properties.put(Thing.PROPERTY_VENDOR, hex(device.getVendorId()));
        properties.put("busType", device.getBusType().map(Object::toString).orElseGet(() ->
                hex(device.getBusId())
        ));
        properties.put(Thing.PROPERTY_FIRMWARE_VERSION, hex(device.getVersionId()));
        properties.put("driverVersion", hex(device.getDriverVersion()));
        return properties;
    }

    private static String hex(int i) {
        return String.format("%04x", i);
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
