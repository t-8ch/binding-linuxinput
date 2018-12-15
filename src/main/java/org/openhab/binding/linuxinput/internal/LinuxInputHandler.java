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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;

import static org.openhab.binding.linuxinput.internal.LinuxInputBindingConstants.*;

@NonNullByDefault
public class LinuxInputHandler extends BaseThingHandler {

    private static final Logger logger = LoggerFactory.getLogger(LinuxInputHandler.class);

    private Map<Integer, Channel> channels;
    private Channel grabbingChannel;
    private Channel keyChannel;
    private Future<Void> worker = null;
    private EvdevDevice device;
    private final String defaultLabel;
    private static final long ID = 15;

    @Nullable
    private LinuxInputConfiguration config;

    public LinuxInputHandler(Thing thing, String defaultLabel) {
        super(thing);
        this.defaultLabel = defaultLabel;
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

    @SuppressWarnings("deprecation")
    @Override
    public void initialize() {
        logger.warn("Initialize: {}", ID);
        config = getConfigAs(LinuxInputConfiguration.class);
        channels = Collections.synchronizedMap(
                new HashMap<>()
        );
        grabbingChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "grab"), CoreItemFactory.SWITCH)
                .withType(CHANNEL_TYPE_DEVICE_GRAB)
                .build();
        keyChannel = ChannelBuilder.create(new ChannelUID(thing.getUID(), "key"), CoreItemFactory.STRING)
                .withType(CHANNEL_TYPE_KEY)
                .build();
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            ThingBuilder customizer = editThing();
            List<Channel> newChannels = new ArrayList<>();
            newChannels.add(grabbingChannel);
            newChannels.add(keyChannel);
            try {
                device = new EvdevDevice(config.path);
                for (EvdevDevice.Key o: device.enumerateKeys()) {
                    String name = o.getName();
                    Channel channel = ChannelBuilder
                            .create(new ChannelUID(thing.getUID(), CHANNEL_GROUP_KEYPRESSES_ID, name), CoreItemFactory.CONTACT)
                            .withLabel(name)
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
            String msg = "Could not find channel for code {}";
            if (isInitialized()) {
                logger.error(msg, event.getCode());
            } else {
                logger.debug(msg, event.getCode());
            }
            return;
        }
        logger.debug("Got event: {}", event);
        // Documented in README.md
        int eventValue = event.getValue();
        switch (eventValue) {
            case EvdevLibrary.KeyEventValue.DOWN:
                String keyCode = channel.getUID().getIdWithoutGroup();
                updateState(keyChannel.getUID(), new StringType(keyCode));
                updateState(channel.getUID(), OpenClosedType.CLOSED);
                triggerChannel(keyChannel.getUID(), keyCode);
                triggerChannel(channel.getUID(), CommonTriggerEvents.PRESSED);
                updateState(keyChannel.getUID(), new StringType());
                break;
            case EvdevLibrary.KeyEventValue.UP:
                updateState(channel.getUID(), OpenClosedType.OPEN);
                triggerChannel(channel.getUID(), CommonTriggerEvents.RELEASED);
                break;
            case EvdevLibrary.KeyEventValue.REPEAT:
                /* Ignored */
                break;
            default:
                logger.error("Unexpected event value for channel {}: {}", channel, eventValue);
                break;
        }
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
}
