/*
 * EventBusMSG - Java Class for Android
 * Created by G.Capelli on 05/08/17.
 * This file is part of BasicAirData GPS Logger
 *
 * Copyright (C) 2011 BasicAirData
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package eu.basicairdata.graziano.gpslogger;

public class EventBusMSG {
    public static final short APP_RESUME = 1;
    public static final short APP_PAUSE = 2;
    public static final short NEW_TRACK = 3;
    public static final short UPDATE_FIX = 4;
    public static final short UPDATE_TRACK = 5;
    public static final short UPDATE_TRACKLIST = 6;
    public static final short UPDATE_SETTINGS = 7;
    public static final short REQUEST_ADD_PLACEMARK = 8;
    public static final short ADD_PLACEMARK = 9;
    public static final short APPLY_SETTINGS = 10;
    public static final short TOAST_TRACK_EXPORTED = 11;
    public static final short UPDATE_JOB_PROGRESS = 13;
    public static final short NOTIFY_TRACKS_DELETED = 14;
    public static final short UPDATE_ACTIONBAR = 15;
    public static final short REFRESH_TRACKLIST = 16;
    public static final short REFRESH_TRACKTYPE = 17;

    public static final short TRACKLIST_DESELECT = 24;
    public static final short TRACKLIST_SELECT = 25;
    public static final short INTENT_SEND = 26;
    public static final short TOAST_UNABLE_TO_WRITE_THE_FILE = 27;

    public static final short ACTION_BULK_DELETE_TRACKS = 40;
    public static final short ACTION_BULK_EXPORT_TRACKS = 41;
    public static final short ACTION_BULK_VIEW_TRACKS = 42;
    public static final short ACTION_BULK_SHARE_TRACKS = 43;
    public static final short TRACKLIST_RANGE_SELECTION = 44;
    public static final short ACTION_EDIT_TRACK = 45;
}
