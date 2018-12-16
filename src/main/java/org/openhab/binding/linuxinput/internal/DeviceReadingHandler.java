package org.openhab.binding.linuxinput.internal;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;

public abstract class DeviceReadingHandler extends BaseThingHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeviceReadingHandler.class);

    private Future<Void> worker = null;

    abstract boolean immediateSetup() throws IOException;
    abstract boolean delayedSetup() throws IOException;
    abstract void handleEventsInThread() throws IOException;
    abstract void closeDevice() throws IOException;

    public DeviceReadingHandler(Thing thing) {
        super(thing);
    }

    @SuppressWarnings("deprecation")
    @Override
    public final void initialize() {
        boolean performDelayedSetup = performImmediateSetup();
        if (performDelayedSetup) {
            scheduler.execute(() -> {
                boolean handleEvents = performDelayedSetup();
                if (handleEvents) {
                    worker = scheduler.submit(() -> {
                        handleEventsInThread();
                        return null;
                    });
                }
            });
        }
    }

    private boolean performImmediateSetup() {
        try {
            return immediateSetup();
        } catch (IOException e) {
            handleSetupError(e);
            return false;
        }
    }

    private boolean performDelayedSetup() {
        try {
            return delayedSetup();
        } catch (IOException e) {
            handleSetupError(e);
            return false;
        }
    }

    private void handleSetupError(Exception e) {
        logger.error("ERROR", e);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                e.getMessage());
    }

    @Override
    public final void dispose() {
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

    private void stopWorker() throws InterruptedException, ExecutionException, TimeoutException {
        logger.info("interrupting worker {}", worker);
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
}
