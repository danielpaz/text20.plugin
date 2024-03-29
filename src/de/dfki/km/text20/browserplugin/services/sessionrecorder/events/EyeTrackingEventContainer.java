/*
 * EyeTrackingEventContainer.java
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
package de.dfki.km.text20.browserplugin.services.sessionrecorder.events;

import java.awt.Point;

import org.simpleframework.xml.Element;

import de.dfki.km.text20.services.trackingdevices.eyes.EyeTrackingEvent;
import de.dfki.km.text20.services.trackingdevices.eyes.EyeTrackingEventValidity;

/**
 *
 *
 * @author Ralf Biedert
 */
public class EyeTrackingEventContainer extends AbstractSessionEvent implements EyeTrackingEvent {

    /** */
    public static final long serialVersionUID = -4224591581456166382L;

    @Element(required = false)
    public long hardwareEventTime = 0;

    @Element(required = false)
    public long elapsedTime = 0;
    
    /** */
    @Element
    public Point combinedCenter;

    @Element(required = false)
    public float headPosition[] = new float[3];

    @Element(required = false)
    public float leftEyeDistance = 0;

    @Element(required = false)
    public float[] leftEyePosition = new float[3];

    @Element(required = false)
    public float pupilSizeLeft = 0;

    @Element(required = false)
    public float pupilSizeRight = 0;

    @Element(required = false)
    public float rightEyeDistance = 0;

    @Element(required = false)
    public float[] rightEyePosition = new float[3];

    @Element(required = false)
    public boolean validity = true;

    // If version = 0 this is an old event
    @Element(required = false)
    public int version = 0;


    public EyeTrackingEventContainer() {

    }

    /**
     * @param trackingEvent
     */
    public EyeTrackingEventContainer(final EyeTrackingEvent trackingEvent) {
        // We should not use the original's event time, as it is set on the remote host and the times may differ.
        this.hardwareEventTime = trackingEvent.getObservationTime();
        this.elapsedTime = trackingEvent.getElapsedTime();
        this.combinedCenter = trackingEvent.getGazeCenter();
        this.headPosition = trackingEvent.getHeadPosition();
        this.pupilSizeLeft = trackingEvent.getPupilSizeLeft();
        this.pupilSizeRight = trackingEvent.getPupilSizeRight();

        this.leftEyeDistance = trackingEvent.getLeftEyeDistance();
        this.rightEyeDistance = trackingEvent.getRightEyeDistance();
        this.leftEyePosition = trackingEvent.getLeftEyePosition();
        this.rightEyePosition = trackingEvent.getRightEyePosition();

        // Current version
        this.version = 2;
    }

    /* (non-Javadoc)
     * @see de.dfki.km.augmentedtext.services.trackingdevices.TrackingEvent#areValid(de.dfki.km.augmentedtext.services.trackingdevices.TrackingEventValidity[])
     */
    @Override
    public boolean areValid(final EyeTrackingEventValidity... validities) {
        // Base return type 
        boolean rval = true;
        
        // Check for all validities we got ...
        for (final EyeTrackingEventValidity v : validities) {
            
            // Check center position ...
            if (v == EyeTrackingEventValidity.CENTER_POSITION_VALID) {
                
                // First combine that with our given validity
                rval &= this.validity;

                // ... and override validity for special points ...
                if (this.combinedCenter.x < 0 || this.combinedCenter.y < 0) {
                    rval = false;
                }
            }
        }
        
        
        // Return final validity
        return rval;
    }

    @Override
    public long getObservationTime() {
        return this.hardwareEventTime == 0 ? this.originalEventTime : this.hardwareEventTime;
    }
    
    /* (non-Javadoc)
     * @see de.dfki.km.text20.services.trackingdevices.common.TrackingEvent#getElapsedTime()
     */
    @Override
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    public void setEventTime(long originalEventTime) {
        this.originalEventTime = originalEventTime;
    }

    @Override
    public Point getGazeCenter() {
        return (Point) this.combinedCenter.clone();
    }

    public void setGazeCenter(Point combinedCenter) {
        this.combinedCenter = combinedCenter;
    }

    @Override
    public float[] getHeadPosition() {
        return this.headPosition;
    }

    public void setHeadPosition(float[] headPosition) {
        this.headPosition = headPosition;
    }

    @Override
    public float getLeftEyeDistance() {
        return this.leftEyeDistance;
    }

    public void setLeftEyeDistance(float leftEyeDistance) {
        this.leftEyeDistance = leftEyeDistance;
    }

    @Override
    public float[] getLeftEyePosition() {
        return this.leftEyePosition;
    }

    public void setLeftEyePosition(float[] leftEyePosition) {
        this.leftEyePosition = leftEyePosition;
    }

    @Override
    public float getPupilSizeLeft() {
        return this.pupilSizeLeft;
    }

    public void setPupilSizeLeft(float pupilSizeLeft) {
        this.pupilSizeLeft = pupilSizeLeft;
    }

    @Override
    public float getPupilSizeRight() {
        return this.pupilSizeRight;
    }

    public void setPupilSizeRight(float pupilSizeRight) {
        this.pupilSizeRight = pupilSizeRight;
    }

    @Override
    public float getRightEyeDistance() {
        return this.rightEyeDistance;
    }

    public void setRightEyeDistance(float rightEyeDistance) {
        this.rightEyeDistance = rightEyeDistance;
    }

    @Override
    public float[] getRightEyePosition() {
        return this.rightEyePosition;
    }

    public void setRightEyePosition(float[] rightEyePosition) {
        this.rightEyePosition = rightEyePosition;
    }

    @Override
    public Point getLeftEyeGazePoint() {
        return null;
    }

    @Override
    public float[] getLeftEyeGazePosition() {
        return null;
    }

    @Override
    public Point getRightEyeGazePoint() {
        return null;
    }

    @Override
    public float[] getRightEyeGazePosition() {
        return null;
    }

    /**
     * @return the validity
     */
    public boolean isValidity() {
        return this.validity;
    }

    /**
     * @param validity the validity to set
     */
    public void setValidity(boolean validity) {
        this.validity = validity;
    }

    /**
     * @return the version
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(int version) {
        this.version = version;
    }
}
