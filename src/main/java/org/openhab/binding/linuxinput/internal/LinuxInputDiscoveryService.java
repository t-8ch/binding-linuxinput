package org.openhab.binding.linuxinput.internal;

import net.weissschuh.evdev4j.EvdevDevice;
import net.weissschuh.evdev4j.LastErrorException;
import net.weissschuh.evdev4j.jnr.EvdevLibrary;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.openhab.binding.linuxinput.internal.LinuxInputBindingConstants.THING_TYPE_DEVICE;

@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.linuxinput")
public class LinuxInputDiscoveryService extends AbstractDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(LinuxInputDiscoveryService.class);

    private ScheduledFuture<?> discoveryJob;
    private static final Duration refreshInterval = Duration.ofSeconds(50);
    private static final Duration timeout = Duration.ofSeconds(30);
    private static final Path deviceDirectory = FileSystems.getDefault().getPath("/dev/input");
    private Runnable runnable = this::startScan;

    public LinuxInputDiscoveryService() {
        super(Collections.singleton(THING_TYPE_DEVICE), (int) timeout.getSeconds(), true);
    }

    @Override
    protected void startScan() {
        logger.warn("startScan {}", deviceDirectory);
        File directory = deviceDirectory.toFile();
        if (directory == null) {
            logger.error("Could not open device directory {}", deviceDirectory);
            return;
        }
        File[] devices = directory.listFiles();
        if (devices == null) {
            throw new IllegalStateException(directory + " is not a directory");
        }
        for (File file: devices) {
            handleFile(file);
        }
    }

    private void handleFile(File file) {
        logger.trace("Discovering file {}", file);
        if (file.isDirectory()) {
            logger.trace("{} is not a file, ignoring", file);
            return;
        }
        if (!file.canRead()) {
            logger.trace("{} is not readable, ignoring", file);
            return;
        }
        DiscoveryResultBuilder result = DiscoveryResultBuilder
                .create(new ThingUID(THING_TYPE_DEVICE, file.getName()))
                .withProperty("path", file.getAbsolutePath())
                .withRepresentationProperty(file.getName())
                .withTTL(refreshInterval.multipliedBy(2).getSeconds());
        boolean shouldDiscover = enrichDevice(result, file);
        if (shouldDiscover) {
            DiscoveryResult thing = result.build();
            logger.debug("Discovered: {}", thing);
            thingDiscovered(thing);
        } else {
            logger.debug("{} is not a keyboard, ignoring", file);
        }
    }

    private boolean enrichDevice(DiscoveryResultBuilder builder, File inputDevice) {
        String label = inputDevice.getName();
        try {
            try (EvdevDevice device = new EvdevDevice(inputDevice.getAbsolutePath())) {
                String labelFromDevice = device.getName();
                boolean isKeyboard = device.has(EvdevLibrary.Type.KEY);
                if (labelFromDevice != null) {
                    label = labelFromDevice;
                }
                return isKeyboard;
            } finally {
                builder.withLabel(label);
            }
        } catch (IOException | LastErrorException e) {
            logger.debug("Could not open device {}", inputDevice, e);
            return false;
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Start background discovery");
        if (discoveryJob == null || discoveryJob.isCancelled()) {
            discoveryJob = scheduler.scheduleWithFixedDelay(runnable, 0, refreshInterval.getSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("Stop background discovery");
        if (discoveryJob != null && !discoveryJob.isCancelled()) {
            discoveryJob.cancel(true);
            discoveryJob = null;
        }
    }
}
