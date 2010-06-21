/*
 * TrackingDeviceImpl.java
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
package de.dfki.km.text20.services.trackingdevices.brain.impl.braintrackingserver;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import net.xeoh.plugins.base.PluginConfiguration;
import net.xeoh.plugins.base.annotations.Capabilities;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import net.xeoh.plugins.base.annotations.injections.InjectPlugin;
import net.xeoh.plugins.informationbroker.InformationBroker;
import net.xeoh.plugins.remote.RemoteAPILipe;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingDevice;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingDeviceInfo;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingDeviceProvider;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingEvent;
import de.dfki.km.text20.services.trackingdevices.brain.BrainTrackingListener;
import de.dfki.km.text20.trackingserver.brain.remote.TrackingClientCallback;
import de.dfki.km.text20.trackingserver.brain.remote.TrackingDeviceInformation;
import de.dfki.km.text20.trackingserver.brain.remote.TrackingEvent;
import de.dfki.km.text20.trackingserver.brain.remote.TrackingServerRegistry;

/**
 * 
 * @author rb
 * 
 */
@PluginImplementation
public class BrainTrackingServerDeviceProviderImpl implements BrainTrackingDeviceProvider {

    private class ServerTrackingDevice implements BrainTrackingDevice,
            TrackingClientCallback {

        /** */
        TrackingDeviceInformation deviceInformation;

        /** Indicates if the client is properly connected */
        final boolean isProperlyConnected;

        /** Manages acces to the listener. */
        final Lock listenerLock = new ReentrantLock();

        /** List of listeners we inform. */
        final List<BrainTrackingListener> trackingListener = new ArrayList<BrainTrackingListener>();

        /** */
        final TrackingServerRegistry registry;

        /**
         * @param string
         * @throws URISyntaxException
         * 
         */
        public ServerTrackingDevice(final String string) throws URISyntaxException {
            // Get remote proxy of the server
            this.registry = BrainTrackingServerDeviceProviderImpl.this.remoteAPI.getRemoteProxy(new URI(string), TrackingServerRegistry.class);

            BrainTrackingServerDeviceProviderImpl.this.logger.info("Connected registry says " + this.registry);

            if (this.registry == null) {
                this.isProperlyConnected = false;
                return;
            }

            this.isProperlyConnected = true;

            BrainTrackingServerDeviceProviderImpl.this.logger.fine("Obtaining device information. In case LipeRMI is still broken this may lock up ...");
            this.deviceInformation = this.registry.getTrackingDeviceInformation();
            BrainTrackingServerDeviceProviderImpl.this.logger.fine("Device information obtained.");

            this.registry.addTrackingListener(this);
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * de.dfki.km.augmentedtext.services.trackingdevices.TrackingDevice#
         * addTrackingListener
         * (de.dfki.km.augmentedtext.services.trackingdevices.TrackingListener)
         */
        public void addTrackingListener(final BrainTrackingListener listener) {
            if (!this.isProperlyConnected) { return; }
            this.listenerLock.lock();
            try {
                this.trackingListener.add(listener);
            } finally {
                this.listenerLock.unlock();
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * de.dfki.km.augmentedtext.services.trackingdevices.TrackingDevice#
         * getDeviceInfo()
         */
        public BrainTrackingDeviceInfo getDeviceInfo() {
            return new BrainTrackingDeviceInfo() {

                public String getInfo(final String key) {
                    if (ServerTrackingDevice.this.deviceInformation == null) return null;

                    if (key.equals("DEVICE_NAME"))
                        return ServerTrackingDevice.this.deviceInformation.deviceName;
                    if (key.equals("DEVICE_MANUFACTURER"))
                        return ServerTrackingDevice.this.deviceInformation.trackingDeviceManufacturer;
                    if (key.equals("HARDWARE_ID"))
                        return ServerTrackingDevice.this.deviceInformation.hardwareID;

                    return null;
                }

                public String[] getKeys() {
                    return new String[] { "DEVICE_NAME", "HARDWARE_ID", "DEVICE_MANUFACTURER" };
                }

                @SuppressWarnings("unused")
                public int getTrackingEventRate() {
                    // TODO Auto-generated method stub
                    return 0;
                }
            };
        }

        /**
         * @return the isProperlyConnected
         */
        public boolean isProperlyConnected() {
            return this.isProperlyConnected;
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * de.dfki.km.augmentedtext.trackingserver.remote.TrackingClientCallback
         * #newTrackingEvent(de.dfki.km.augmentedtext.trackingserver.remote.
         * TrackingEvent)
         */
        public void newTrackingEvent(final TrackingEvent e) {
            // Sometimes null events might occur. Filter them.
            if (e == null) return;

            final BrainTrackingEvent event = new BrainTrackingEvent() {

                /* (non-Javadoc)
                 * @see de.dfki.km.text20.services.braintrackingdevices.BrainTrackingEvent#getEventTime()
                 */
                @Override
                public long getEventTime() {
                    return e.date;
                }

                /* (non-Javadoc)
                 * @see de.dfki.km.text20.services.braintrackingdevices.BrainTrackingEvent#getChannels()
                 */
                @Override
                public Collection<String> getChannels() {
                    return e.channels.keySet();
                }

                /* (non-Javadoc)
                 * @see de.dfki.km.text20.services.braintrackingdevices.BrainTrackingEvent#getValue(java.lang.String)
                 */
                @SuppressWarnings("boxing")
                @Override
                public double getValue(String channel) {
                    return e.channels.get(channel);
                }
            };

            // Lock listeners ...
            ServerTrackingDevice.this.listenerLock.lock();

            // TODO: Decouple the follwing calls from this method, otherwise we
            // might block our caller ...

            try {
                // And dispatch it to the listener
                for (int i = 0; i < ServerTrackingDevice.this.trackingListener.size(); i++) {
                    final BrainTrackingListener l = ServerTrackingDevice.this.trackingListener.get(i);
                    l.newTrackingEvent(event);
                }
            } finally {
                ServerTrackingDevice.this.listenerLock.unlock();
            }

        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * de.dfki.km.augmentedtext.services.trackingdevices.TrackingDevice#
         * closeDevice()
         */
        public void closeDevice() {
            // TODO Auto-generated method stub
        }
    }

    /** */
    @InjectPlugin
    public PluginConfiguration configuration;

    /** */
    @InjectPlugin
    public InformationBroker infobroker;

    /** */
    @InjectPlugin
    public RemoteAPILipe remoteAPI;

    /** */
    final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Return what we can do...
     * 
     * @return .
     */
    @Capabilities
    public String[] getCapabilities() {
        return new String[] { "braintrackingdevice:trackingserver" };
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * de.dfki.km.augmentedtext.services.trackingdevices.TrackingDeviceProvider
     * #openDevice(java.lang.String)
     */
    public BrainTrackingDevice openDevice(final String url) {
        try {
            ServerTrackingDevice serverTrackingDevice = new ServerTrackingDevice(url);
            if (serverTrackingDevice.isProperlyConnected()) return serverTrackingDevice;

            return null;

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

}