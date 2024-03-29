/*
 * SessionRecorderImpl.java
 *
 * Copyright (c) 2010, Ralf Biedert, DFKI. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 *
 */
package de.dfki.km.text20.browserplugin.services.sessionrecorder.impl.xstream;

import static net.jcores.jre.CoreKeeper.$;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import net.jcores.jre.interfaces.functions.F0;
import net.jcores.jre.options.Hash;
import net.xeoh.plugins.base.PluginManager;
import net.xeoh.plugins.informationbroker.InformationBroker;
import net.xeoh.plugins.informationbroker.util.InformationBrokerUtil;

import com.thoughtworks.xstream.XStream;

import de.dfki.km.text20.browserplugin.browser.browserplugin.brokeritems.configuration.SessionDirectoryItem;
import de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder;
import de.dfki.km.text20.browserplugin.services.sessionrecorder.options.CreateRecorderOption;
import de.dfki.km.text20.browserplugin.services.sessionrecorder.options.SpecialCommandOption;
import de.dfki.km.text20.browserplugin.services.sessionrecorder.options.createrecorder.OptionFakeReplay;
import de.dfki.km.text20.browserplugin.services.sessionrecorder.options.specialcommand.OptionFakeNextDate;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingDeviceInfo;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingEvent;
import de.dfki.km.text20.services.trackingdevices.eyes.EyeTrackingDeviceInfo;
import de.dfki.km.text20.services.trackingdevices.eyes.EyeTrackingEvent;

/**
 * Manages writing to sessions.
 * 
 * @author Ralf Biedert
 * @author Andreas Buhl
 */
public class SessionRecorderImpl implements SessionRecorder {

    /** The prefix to name this session */
    private static final String filenamePrefix = "session.";

    /** The suffix to name this session */
    private static final String filenameExtension = ".xstream";

    /** Remove and replace by diagnosis */
    final Logger logger = Logger.getLogger(this.getClass().getName());

    /** Needed to get some information from the rest of the plugins */
    final InformationBroker infoBroker;

    /** Timer to take screenshots */
    Timer screenshotTimer = new Timer();

    /** Stores the last known mouse position (useful for clicks) */
    Point lastMousePos = new Point();

    /** If set, we use a fake replay */
    OptionFakeReplay fakeReplay;

    /** Needed to record screenshots */
    Robot robot;

    /** Where our document is on our screen */
    volatile Rectangle documentRectangle;

    /**  */
    volatile SessionStreamer sessionStreamer;

    /** */
    XStream xstream;

    /** Working directory to create sessions in */
    String sessionDir = "/tmp/session";

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #getPreference(java.lang.String, java.lang.String)
     */
    @Override
    public void getPreference(final String key, final String deflt) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.getPreference(key, deflt);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #markLog(java.lang.String)
     */
    @Override
    public void markLog(final String tag) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.markLog(tag);

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #mouseClicked(int, int)
     */
    @Override
    public void mouseClicked(final int type, final int button) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.mouseClickEvent(type, button);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #takeScreenshot()
     */
    @Override
    public void takeScreenshot() {
        if (SessionRecorderImpl.this.sessionStreamer == null) return;
        // FIXME: Why do we have the delay when taking screenshots?
        takeScreenshotDelayed(100);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #callFunction(java.lang.String)
     */
    @Override
    public void callFunction(final String function) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.callFunction(function);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #executeJSFunction(java.lang.String, java.lang.Object[])
     */
    @Override
    public void executeJSFunction(final String function, final Object... args) {
        // FIXME: Better record the failed calls and store them separately
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.executeJSFunction(function, $(args).string().array(String.class));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#newTrackingEvent(de.dfki.km.text20.services
     * .trackingdevices.eyes.EyeTrackingEvent)
     */
    @Override
    public void eyeTrackingEvent(final EyeTrackingEvent event) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.trackingEvent(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#registerListener(java.lang.String,
     * java.lang.String)
     */
    @Override
    public void registerListener(final String type, final String listener) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.registerListener(type, listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#removeListener(java.lang.String)
     */
    @Override
    public void removeListener(final String listener) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.removeListener(listener);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #setParameter(java.lang.String, java.lang.String)
     */
    @Override
    public void setParameter(final String key, final String value) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.putProperty(key, value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #setPreference(java.lang.String, java.lang.String)
     */
    @Override
    public void setPreference(final String key, final String value) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.setPreference(key, value);

    }

    /**
     * Halts the execution of all timers.
     */
    public void shutdown() {
        stop();
        if (this.screenshotTimer != null) {
            this.screenshotTimer.cancel();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.eventrecorder.SessionStreamer#start
     * ()
     */
    @Override
    public void start() {

        // Obtain the session dir (should have been set by now)
        // Return the session dir
        this.sessionDir = new InformationBrokerUtil(this.infoBroker).get(SessionDirectoryItem.class, $.sys.tempfile().getAbsolutePath() + ".dir");

        // Create sessiondir
        new File(this.sessionDir).mkdirs();

        // Create streamer
        final Dimension screenSize = this.fakeReplay != null ? this.fakeReplay.getScreenSize() : Toolkit.getDefaultToolkit().getScreenSize();
        final Date fakeDate = this.fakeReplay != null ? new Date(this.fakeReplay.getStartDate()) : null;

        this.sessionStreamer = new SessionStreamer(screenSize, createFileName(fakeDate), fakeDate);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.eventrecorder.SessionStreamer#stop
     * ()
     */
    @Override
    public void stop() {
        if(this.sessionStreamer == null) return;
        
        // Very nasty hack to wait for the streamer to save everything. TODO: Improve this 
        try {
            Thread.sleep(500);
            this.sessionStreamer.close();
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
        
        // In case we receive a stop event, check if we had a fake session, in that case, zip our ouput
        if (this.fakeReplay != null) {
            $(this.sessionDir).file().zip(this.fakeReplay.getFile());
        }

        this.sessionStreamer = null;
    }

    /**
     * Stores the device info of the given eye tracking device.
     * 
     * @param deviceInfo
     */
    @Override
    public void storeEyeDeviceInfo(final EyeTrackingDeviceInfo deviceInfo) {
        final String[] keys = deviceInfo.getKeys();
        for (final String key : keys) {
            final String value = deviceInfo.getInfo(key);
            setParameter("#deviceinfo." + key, value);
        }
    }

    /**
     * Creates a new session recorder.
     * 
     * @param pm
     * @param options
     */
    public SessionRecorderImpl(final PluginManager pm, CreateRecorderOption... options) {
        // Get our information broker
        this.infoBroker = pm.getPlugin(InformationBroker.class);

        // Create the streamer to write XML into a file
        try {
            this.xstream = new XStream();
        } catch (Exception e) {
            this.logger.warning("Error creating xstream! No logging available!.");
            e.printStackTrace();
            this.xstream = null;
        }

        // Process options
        this.fakeReplay = $(options).cast(OptionFakeReplay.class).compact().get(0);

        init();
    }

    /** Called to set up serializer and stuff */
    void init() {

        try {
            this.robot = new Robot();
        } catch (final AWTException e) {
            e.printStackTrace();
        }

        try {
            SessionStreamer.setAlias(this.xstream);
            SessionStreamer.registerConverters(this.xstream);
        } catch (Exception e) {
            this.logger.warning("Error setting alias. No session recorder available!");
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#updateElementMetaInformation(java.lang
     * .String, java.lang.String, java.lang.String)
     */
    @Override
    public void updateElementMetaInformation(final String id, final String key,
                                             final String value) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.updateMetaInformation(id, key, value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#updateElementFlag(java.lang.String,
     * java.lang.String, boolean)
     */
    @Override
    public void updateElementFlag(final String id, final String flag, final boolean value) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.updateElementFlag(id, flag, value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#updateElementGeometry(java.lang.String,
     * java.lang.String, java.lang.String, java.awt.Rectangle)
     */
    @Override
    public void updateElementGeometry(final String id, final String type,
                                      final String content, final Rectangle r) {
        if (this.sessionStreamer == null) return;
        
        // In case this is an image, we also try to store the image
        if("image".equals(type)) {
            // Check if we already had this file
            final File target = new File(this.sessionDir + "/" + "image." + $(content).string().bytes().hash(Hash.MD5).get(0) + "." + $(content).split("\\.").get(-1));
            if(!target.exists()) {
                // If we hadn't, then run in the background 
                $.sys.oneTime(new F0() {
                    @Override
                    public void f() {
                        // Download the image and copy it to a file in the session directory
                        $(content).uri().download().copy(target.getAbsolutePath());
                    }
                }, 1);
            }
        }
        
        // And store the element info
        this.sessionStreamer.updateElementGeometry(id, type, content, r);
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#updateGeometry(java.awt.Rectangle)
     */
    @Override
    public void updateGeometry(final Rectangle rectangle) {
        if (this.sessionStreamer == null) return;
        this.documentRectangle = rectangle;
        this.sessionStreamer.updateGeometry(rectangle);
        takeScreenshotDelayed();
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#updateMousePosition(int, int)
     */
    @Override
    public void updateMousePosition(final int x, final int y) {

        if (this.lastMousePos.x != x || this.lastMousePos.y != y) {
            this.lastMousePos.x = x;
            this.lastMousePos.y = y;

            if (this.sessionStreamer != null) {
                this.sessionStreamer.mouseMovement(x, y);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.browserplugin.services.sessionrecorder.SessionRecorder
     * #updateViewport(java.awt.Point)
     */
    @Override
    public void updateViewport(final Point viewportStart) {
        if (this.sessionStreamer == null) return;
        this.sessionStreamer.updateDocumentViewport(viewportStart);
        takeScreenshotDelayed();
    }

    /**
     * Takes a screenshot after the given delay.
     * 
     * @param delay
     */
    private void takeScreenshotDelayed(final int delay) {
        if (this.sessionStreamer == null || this.fakeReplay != null) return;

        // Cancel all pending tasks
        try {
            this.screenshotTimer.cancel();
        } catch (final Throwable e) {
            // Why does the language has to be so crappy ?
        }

        this.screenshotTimer = new Timer();
        this.screenshotTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                doTakeScreenshot();

                // Don't invoke us again.
                SessionRecorderImpl.this.screenshotTimer.cancel();
            }

        }, delay);
    }

    /**
     * Really takes a screenshots.
     */
    void doTakeScreenshot() {
        if (this.sessionStreamer == null || this.fakeReplay != null) return;

        final String file = "screenshot." + System.currentTimeMillis() + ".png";
        final String fullpath = SessionRecorderImpl.this.sessionDir + "/" + file;

        // We need this priviledged stuff for applets...
        AccessController.doPrivileged(new PrivilegedAction<BufferedImage>() {

            @Override
            public BufferedImage run() {
                try {
                    // Try to save the image.
                    final BufferedImage createScreenCapture = SessionRecorderImpl.this.robot.createScreenCapture(SessionRecorderImpl.this.documentRectangle);

                    ImageIO.write(createScreenCapture, "png", new File(fullpath));

                    SessionRecorderImpl.this.sessionStreamer.newImage(file);

                    return createScreenCapture;
                } catch (final NullPointerException e) {
                    SessionRecorderImpl.this.logger.finer("NullPointerException when creating a screenshot.");
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

    /**
     * Takes a screenshot soon.
     */
    private void takeScreenshotDelayed() {
        if (this.sessionStreamer == null || this.fakeReplay != null) return;
        takeScreenshotDelayed(500);
    }

    /**
     * Creates a filename for the session file
     * @param fakeDate 
     * 
     * @return generated file path:
     * [session_directory]/[filePrefix]_[startTimeInMilliSeconds].[filenameExtension]
     */
    private String createFileName(Date fakeDate) {
        return this.sessionDir + "/" + filenamePrefix + (fakeDate != null ? fakeDate.getTime() : System.currentTimeMillis()) + filenameExtension;
    }

    /**
     * Returns the used session directory.
     * 
     * @return .
     */
    public String getSessionDir() {
        return this.sessionDir;
    }

    /**
     * @param sessionDir
     */
    public void setSessionDir(String sessionDir) {
        this.sessionDir = sessionDir;
    }

    /*
     * (non-Javadoc)
     * 
     * @see de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#specialCommand(de.dfki.km.text20.
     * browserplugin.services.sessionrecorder.options.SpecialCommandOption[])
     */
    @Override
    public void specialCommand(SpecialCommandOption... options) {
        final OptionFakeNextDate fakeNextDate = $(options).cast(OptionFakeNextDate.class).compact().get(0);
        if (fakeNextDate != null) {
            this.sessionStreamer.nextDate(new Date(fakeNextDate.getDate()));
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#newBrainTrackingEvent(de.dfki.km.text20
     * .services.trackingdevices.brain.BrainTrackingEvent)
     */
    @Override
    public void brainTrackingEvent(BrainTrackingEvent event) {
        if (this.sessionStreamer == null) return;

        this.sessionStreamer.brainTrackingEvent(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.text20.browserplugin.services.sessionrecorder.SessionRecorder#storeBrainDeviceInfo(de.dfki.km.text20
     * .services.trackingdevices.brain.BrainTrackingDeviceInfo)
     */
    @Override
    public void storeBrainDeviceInfo(BrainTrackingDeviceInfo deviceInfo) {
        final String[] keys = deviceInfo.getKeys();
        for (final String key : keys) {
            final String value = deviceInfo.getInfo(key);
            setParameter("#braindeviceinfo." + key, value);
        }
    }
}
