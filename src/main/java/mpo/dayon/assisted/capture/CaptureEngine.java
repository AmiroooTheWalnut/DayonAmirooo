package mpo.dayon.assisted.capture;

import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;

import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.concurrent.RunnableEx;
import mpo.dayon.common.configuration.ReConfigurable;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.gui.common.Position;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.UnitUtilities;

public class CaptureEngine implements ReConfigurable<CaptureEngineConfiguration> {

    private static final Dimension TILE_DIMENSION = new Dimension(32, 32);

    private final CaptureFactory captureFactory;

    private final Listeners<CaptureEngineListener> listeners = new Listeners<>();

    private final Thread thread;

    /**
     * I keep only the checksum as I do not want to keep the referenceS to the
     * byte[] of the previous captureS.
     */
    private final long[] previousCapture;

    private final Object reconfigurationLOCK = new Object();

    private CaptureEngineConfiguration configuration;

    private boolean reconfigured;

    public CaptureEngine(CaptureFactory captureFactory) {
        this.captureFactory = captureFactory;

        final int x = Math.round((float) captureFactory.getDimension().width / TILE_DIMENSION.width);
        final int y = Math.round((float) captureFactory.getDimension().height / TILE_DIMENSION.height);

        this.previousCapture = new long[x * y + 200];
        resetPreviousCapture();

        this.thread = new Thread(new RunnableEx() {
            @Override
            protected void doRun() throws Exception {
                CaptureEngine.this.mainLoop();
            }
        }, "CaptureEngine");
    }

    @Override
    public void configure(CaptureEngineConfiguration configuration) {
        synchronized (reconfigurationLOCK) {
            this.configuration = configuration;
            this.reconfigured = true;
        }
    }

    @Override
    public void reconfigure(CaptureEngineConfiguration configuration) {
        configure(configuration);
    }

    public void addListener(CaptureEngineListener listener) {
        listeners.add(listener);

        // We're keeping locally a previous state so we must be sure to send at
        // least
        // once the previous capture state to the new listener.

        synchronized (reconfigurationLOCK) {
            this.reconfigured = true;
        }
    }

    public void start() {
        thread.start();
    }

    private void mainLoop() throws InterruptedException {
        Gray8Bits quantization = null;
        int tick = -1;

        long start = -1;

        int captureId = 0;
        int captureCount = 0;

        int skipped = 0;

        while (true) {
            boolean reset = false;

            synchronized (reconfigurationLOCK) {
                if (reconfigured) // assuming everything has changed (!)
                {
                    skipped = 0;

                    start = System.currentTimeMillis();
                    captureCount = 0;

                    quantization = configuration.getCaptureQuantization();
                    tick = configuration.getCaptureTick();

                    resetPreviousCapture();

                    // I'm using a flag to tag the capture as a RESET - it is
                    // then easier
                    // to handle the reset message until the assistant without
                    // having to
                    // change anything (e.g., merging mechanism in the
                    // compressor engine).
                    reset = true;

                    Log.info("Capture engine has been reconfigured [tile:" + captureId + "] " + configuration);

                    reconfigured = false;
                }
            }

            ++captureCount;
            ++captureId;

            @Nullable
            final byte[] pixels = captureFactory.captureGray(quantization);

            if (pixels == null) // testing purpose (!)
            {
                Log.info("CaptureFactory has finisted!");
                break;
            }

            fireOnRawCaptured(captureId, pixels); // debugging purpose (!)

            @Nullable
            final CaptureTile[] dirty = computeDirtyTiles(captureId, pixels, captureFactory.getDimension(), previousCapture);

            if (dirty != null) {
                final Capture capture = new Capture(captureId, reset, skipped, 0, captureFactory.getDimension(), TILE_DIMENSION, dirty);
                fireOnCaptured(capture); // might update the capture (i.e.,
                // merging with previous not sent
                // yet)
                updatePreviousCapture(capture);
            }

            final int delayedCaptureCount = syncOnTick(start, captureCount, captureId, tick);

            captureCount += delayedCaptureCount;
            captureId += delayedCaptureCount;

            skipped = delayedCaptureCount;
        }

        Log.info("The capture engine has been stopped!");
    }

    private static int syncOnTick(final long start, final int captureCount, final int captureId, final long tick) throws InterruptedException {
        int delayedCaptureCount = 0;

        while (true) {
            final long captureMaxEnd = start + (captureCount + delayedCaptureCount) * tick;
            final long capturePause = captureMaxEnd - System.currentTimeMillis();

            if (capturePause < 0) {
                ++delayedCaptureCount;
                Log.warn("Skipping capture (" + (captureId + delayedCaptureCount) + ") " + UnitUtilities.toElapsedTime(-capturePause));
                continue;
            }

            if (capturePause > 0) {
                Thread.sleep(capturePause);
            }

            break;
        }

        return delayedCaptureCount;
    }

    private void resetPreviousCapture() {
        Arrays.fill(previousCapture, Long.MIN_VALUE);
    }

    private void updatePreviousCapture(Capture capture) {
        final CaptureTile[] dirtyTiles = capture.getDirtyTiles();

        for (int idx = 0; idx < dirtyTiles.length; idx++) {
            final CaptureTile dirtyTile = dirtyTiles[idx];

            if (dirtyTile != null) {
                previousCapture[idx] = dirtyTile.getChecksum();
            }
        }
    }

    @Nullable
    private static CaptureTile[] computeDirtyTiles(int captureId, byte[] capture, Dimension captureDimension, long[] previousCapture) {
        CaptureTile[] dirty = null;

        int tileId = 0;

        for (int ty = 0; ty < captureDimension.height; ty += TILE_DIMENSION.height) {
            final int th = Math.min(captureDimension.height - ty, TILE_DIMENSION.height);

            for (int tx = 0; tx < captureDimension.width; tx += TILE_DIMENSION.width) {
                final int tw = Math.min(captureDimension.width - tx, TILE_DIMENSION.width);

                final int offset = ty * captureDimension.width + tx;

                final byte[] data = createTile(capture, captureDimension.width, offset, tw, th);

                final long cs = CaptureTile.computeChecksum(data, 0, data.length);

                if (cs != previousCapture[tileId]) {
                    if (dirty == null) {
                        dirty = new CaptureTile[previousCapture.length];
                    }
                    final Position position = new Position(tx, ty);
                    dirty[tileId] = new CaptureTile(captureId, tileId, cs, position, tw, th, data);
                }

                ++tileId;
            }
        }

        return dirty;
    }

    /**
     * Screen-rectangle buffer to tile-rectangle buffer.
     */
    private static byte[] createTile(byte[] capture, int width, int offset, int tw, int th) {
        final byte[] tile = new byte[tw * th];

        int srcPos = offset;
        int destPos = 0;

        while (destPos < tile.length) {
            System.arraycopy(capture, srcPos, tile, destPos, tw);

            srcPos += width;
            destPos += tw;
        }

        return tile;
    }

    private void fireOnCaptured(Capture capture) {
        for (final CaptureEngineListener xListener : listeners.getListeners()) {
            xListener.onCaptured(capture);
        }
    }

    private void fireOnRawCaptured(int id, byte[] grays) {
        for (final CaptureEngineListener xListener : listeners.getListeners()) {
            xListener.onRawCaptured(id, grays);
        }
    }

}
