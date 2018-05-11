/*
 Copyright (c) 2018 by ScaleOut Software, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package com.scaleoutsoftware.soss.http.websession;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.io.Serializable;
import java.util.*;

/**
 * Standard implementation of the HttpSession with additional flags for updating 
 * and removing Java Sessions from the ScaleOut StateServer in-memory data grid.
 */
public class SossHttpSession implements HttpSession, Serializable {

    // Member variables
    private int _maxInactiveInterval;

    private long _creationTime;

    private long _accessTime;

    private boolean _isNew;

    private boolean _updated;

    private boolean _invalidated;

    private String _sessionId;

    private Map<String,Object> _sessionAttributes;

    private transient ServletContext _context;

    /**
     * Simple constructor - sets no variables. Should not be used.
     */
    private SossHttpSession() {

    }

    /**
     * Creates a new SossHttpSession object with the same characteristics as the Session parameter.
     * @param session
     */
    public SossHttpSession(HttpSession session) {
        if(session == null) {
            throw new IllegalArgumentException("Parameter session object was null.");
        }
        _sessionId = session.getId();
        _creationTime = session.getCreationTime();
        _accessTime = session.getLastAccessedTime();
        _maxInactiveInterval = session.getMaxInactiveInterval();
        _isNew = session.isNew();
        _updated = true;
        _invalidated = false;
        _sessionAttributes = new HashMap<String, Object>();

        Enumeration<String> names = session.getAttributeNames();

        String name;
        while (names.hasMoreElements()) {
            name = names.nextElement();
            _sessionAttributes.put(name, session.getAttribute(name));
        }
    }

    /**
     * Creates a new SossHttpSession using the SossHttpSession(HttpSession) constructor and sets the isNew flag to the
     * parameter value.
     * @param session
     * @param isNewFlag
     */
    SossHttpSession(HttpSession session, boolean isNewFlag){
        this(session);
        _isNew = isNewFlag;
    }

    /**
     * Returns the creation time.
     * @return The time the session object was created.
     */
    @Override
    public long getCreationTime() {
        return _creationTime;
    }

    /**
     * Returns the sessionId associated with this session.
     * @return the sessionId associated with this session.
     */
    @Override
    public String getId() {
        return _sessionId;
    }

    /**
     * Returns the last accessed time.
     * @return the last time this session was accessed.
     */
    @Override
    public long getLastAccessedTime() {
        return _accessTime;
    }

    /**
     * Returns the ServletContext associated with this Session.
     * @return the ServletContext associated with this Session
     */
    @Override
    public ServletContext getServletContext() {
        return _context;
    }

    /**
     * sets _MaxInactiveInterval to the parameter maxInactiveInterval
     * @param maxInactiveInterval
     */
    @Override
    public void setMaxInactiveInterval(int maxInactiveInterval) {
        _maxInactiveInterval = maxInactiveInterval;
    }

    /**
     * Returns the MaxInactiveInterval associated with this session.
     * @return the MaxInactiveInterval associated with this session
     */
    @Override
    public int getMaxInactiveInterval() {
        return _maxInactiveInterval;
    }

    /**
     * Returns an *empty* session context associated with this session.
     * @return an empty session context
     */
    @Override
    @Deprecated
    public HttpSessionContext getSessionContext() {
        return _sessionContext;
    }

    /**
     * Returns the Object associated with the parameter key that is stored in this session. Returns null if the value
     * is not associated with the session.
     * @param key
     * @return the attribute or null if it doesn't exist
     */
    @Override
    public Object getAttribute(String key) {
        return _sessionAttributes.get(key);
    }

    /**
     * Returns the Object associated with the parameter key that is stored in this session. Returns null if the value
     * is not associated with the session.
     * @param key
     * @return the value or null if it doesn't exist
     */
    @Override
    public Object getValue(String key) {
        return _sessionAttributes.get(key);
    }

    /**
     * Returns an Enumeration of String Objects representing the attributes associated with this session object.
     * @return the attribute names associated with this session
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(_sessionAttributes.keySet());
    }

    /**
     * Returns an array of Strings representing the attributes associated with this session object.
     * @return the value names associated with this session.
     */
    @Override
    public String[] getValueNames() {
        return _sessionAttributes.keySet().toArray(new String[_sessionAttributes.size()]);
    }

    /**
     * Stores the parameter Key/Value pair inside this session.
     * @param key
     * @param value
     */
    @Override
    public void setAttribute(String key, Object value) {
        _sessionAttributes.put(key,value);
        _updated = true;
    }

    /**
     * Stores the parameter KeyValue pair inside this session.
     * @param key
     * @param value
     */
    @Override
    public void putValue(String key, Object value) {
        this.setAttribute(key,value);
        _updated = true;
    }

    /**
     * Removes the KeyValue pair from this session.
     * @param key
     */
    @Override
    public void removeAttribute(String key) {
        _sessionAttributes.remove(key);
        _updated = true;
    }

    /**
     * Removes the KeyValue pair from this session.
     * @param key
     */
    @Override
    public void removeValue(String key) {
        this.removeAttribute(key);
        _updated = true;
    }

    /**
     * Invalidates this session. Clears the internal storage of session attributes.
     */
    @Override
    public void invalidate() {
        _sessionAttributes.clear();
        _updated = false;
        _invalidated = true;
    }

    /**
     * Check to see if the session is new.
     * @return true/false if the session is new.
     */
    @Override
    public boolean isNew() {
        return _isNew;
    }

    /**
     * Sets the ServletContext associated with this session.
     * @param servletContext
     */
    public void setServletContext(ServletContext servletContext) {
        _context = servletContext;
    }

    /**
     * Refreshes the updated flag.
     */
    public void refreshUpdate() {
        _updated = false;
    }

    /**
     * Check to see if the session was updated.
     * @return true/false if the session was updated
     */
    public boolean updated() {
        return _updated;
    }

    /**
     * Checks to see if the session was invalidated.
     * @return true/false if the session was invalidated
     */
    public boolean invalidated() {
        return _invalidated;
    }

    // Empty session context.
    private static final HttpSessionContext _sessionContext = new HttpSessionContext() {
        private final List<String> _emptyList = Collections.emptyList();
        @Override
        @Deprecated
        public HttpSession getSession(String id) {
            return null;
        }

        @Override
        @Deprecated
        public Enumeration<String> getIds() {
            return Collections.enumeration(_emptyList);
        }
    };

}
