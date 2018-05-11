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

import com.scaleoutsoftware.soss.client.*;

import javax.servlet.*;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * ScaleOut StateServer implementation of the Filter class to store Java sessions 
 * in the ScaleOut StateServer in-memory data grid.
 */
public class SossHttpFilter implements Filter {

    public static final String SOSS_CACHE_NAME = "SossAppName";

    public static final String SOSS_USE_SESSION_LOCKING = "SossSessionLocking";

    private ServletContext _servletContext;

    private NamedCache _cache;

    private MessageDigest _md;

    private ReadOptions _readOptions;

    private UpdateOptions _updateOptions;

    private boolean _createPolicySet;

    /**
     * Implements the Init method for this Filter. Initializes this filter and constructs the NamedCache and
     * ReadOptions/UpdateOptions by pulling the "SossAppName" and "SossSessionLocking" parameters from the FilterConfig.
     * <p>
     * During initialization, the <code>ServletContext</code> associated with the <code>FilterConfig</code> is used
     * to retrieve the context parameters that are required for initializing the <code>SossHttpFilter</code> from
     * the WEB-INF/web.xml configuration parameters.
     * <p/>
     * <p>
     * The only required parameter is:
     * <context-param>
     * <param-name>SossAppName</param-name>
     * <param-value>YourAppName</param-value>
     * </context-param>
	 * Additionally, the optional parameters are: 
	 * <context-param>
     * <param-name>SossSessionLocking</param-name>
     * <param-value>true</param-value>
     * </context-param>	 
     * <p/>
     *
     * @param filterConfig
     * @throws javax.servlet.ServletException
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            _servletContext = filterConfig.getServletContext();
            String cacheName = _servletContext.getInitParameter(SOSS_CACHE_NAME);
            if (cacheName == "") {
                throw new ServletException("SossAppName is empty, it can be specified in the applications WEB-INF/web.xml.");
            } else {
                _cache = CacheFactory.getCache(cacheName);
                _createPolicySet = false;
            }

            String locking = _servletContext.getInitParameter(SOSS_USE_SESSION_LOCKING);
            if (locking.equals("false")) {
                _readOptions = new ReadOptions(ReadLockingMode.NoLockOnRead);
                _updateOptions = new UpdateOptions(UpdateLockingMode.None);
            } else { // default is use locking.
                _readOptions = new ReadOptions(ReadLockingMode.LockOnRead);
                _updateOptions = new UpdateOptions(UpdateLockingMode.UnlockAfterUpdate);
            }

            _md = MessageDigest.getInstance("SHA-256");
        } catch (NamedCacheException nce) {
            throw new ServletException(nce);
        } catch (NoSuchAlgorithmException nsae) {
            throw new ServletException(nsae);
        }
    }

    /**
     * Performs the doFilter for the ServletRequest and ServletResponse via the FilterChain following the
     * Chain-of-responsibility pattern.
     * <p>
     *     If the ServletRequest is a HttpServletRequest, it is handled by the <code>processRequest</code> function
     *     otherwise the parameter <code>FilterChain</code> will perform doFilter on the <code>ServletRequest</code>
     *     and <code>ServletResponse</code>.
     * </p>
     * @param servletRequest
     * @param servletResponse
     * @param filterChain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            processRequest(request, servletResponse, filterChain);
        } else {
            try {
                filterChain.doFilter(servletRequest, servletResponse);
            } catch (IOException ioe) {
                throw new IOException(ioe);
            } catch (ServletException se) {
                throw new ServletException(se);
            }
        }
    }

    /**
     * Implements the destroy method for this filter.
     * Simply returns.
     */
    @Override
    public void destroy() {
        return;
    }

    /**
     * ProcessRequest is the logic behind intercepting a HttpServletRequest, retrieving/creating the session,
     * stashing the session in a custom request that wraps the HttpServletRequest, processing the request using the
     * FilterChain and then updating/removing the session from the SOSS in-memory data grid (IMDG).
     *
     * @param request
     * @param response
     * @param chain
     * @throws IOException
     * @throws ServletException
     */
    private void processRequest(HttpServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // create or retrieve the session from the data grid.
        SossHttpSession cachedSession;
        String sessionId = request.getRequestedSessionId();
        byte[] cacheId;

        if (sessionId != null) {
            try {
                cacheId = getCacheId(sessionId);
                cachedSession = (SossHttpSession) _cache.retrieve(cacheId, _readOptions);
                if (cachedSession != null) {
                    if (cachedSession.isNew()) {
                        cachedSession = new SossHttpSession(cachedSession, false);
                    }
                } else {
                    cachedSession = processRequestHelper(request);
                }
            } catch (NamedCacheException nce) {
                throw new ServletException(nce);
            }
        } else {
            cachedSession = processRequestHelper(request);
        }

        sessionId = cachedSession.getId();
        cachedSession.setServletContext(_servletContext);
        cachedSession.refreshUpdate();

        // store our session in the request, process the request and then update the session if updates occurred.
        try {
            request = new RequestWrapper(request, cachedSession);
            chain.doFilter(request, response);

            HttpSession session = request.getSession(false);

            if (session != null) {
                if (session instanceof SossHttpSession) {
                    SossHttpSession wrappedSession = (SossHttpSession) session;
                    if (wrappedSession.updated()) {
                        _cache.update(getCacheId(sessionId), wrappedSession, _updateOptions);
                    } else {
                        _cache.releaseLock(getCacheId(sessionId));
                    }

                    if (wrappedSession.invalidated()) {
                        byte[] key = getCacheId(sessionId);
                        _cache.acquireLock(key);
                        _cache.remove(key);
                    }
                }
            }
        } catch (IOException ioe) {
            throw new IOException(ioe);
        } catch (ServletException se) {
            throw new ServletException(se);
        } catch (NamedCacheException nce) {
            throw new ServletException(nce);
        }
    }

    /**
     * processRequestHelper creates a new SossHttpSession, byte[] key and puts the newly created Session into the
     * ScaleOut StateServer in-memory data grid.
     *
     * @param request
     * @return
     */
    private SossHttpSession processRequestHelper(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        SossHttpSession storeSession = new SossHttpSession(session, true);
        if(!_createPolicySet) {
            CreatePolicy createPolicy = new CreatePolicy();
            createPolicy.setTimeout(TimeSpan.fromMilliseconds(storeSession.getMaxInactiveInterval()));
            _cache.setDefaultCreatePolicy(createPolicy);
            _createPolicySet = true;
        }

        try {
            byte[] cacheId = getCacheId(storeSession.getId());
            _cache.put(cacheId, storeSession);
        } catch (NamedCacheException e) {
            throw new RuntimeException(e);
        }
        return storeSession;
    }

    /**
     * Returns a new SHA-256 byte[] that is created from the sessionId after calling String.getBytes().
     *
     * @param sessionId
     * @return
     */
    public byte[] getCacheId(String sessionId) {
        return _md.digest(sessionId.getBytes());
    }

    /**
     * RequestWrapper extends the HttpServletRequestWrapper to simply stash the SossHttpSession object inside the
     * request.
     *
     */
    private static class RequestWrapper extends HttpServletRequestWrapper {
        private final SossHttpSession _session;

        private RequestWrapper(HttpServletRequest request, SossHttpSession session) {
            super(request);
            _session = session;
        }

        @Override
        public HttpSession getSession() {
            return _session;
        }

        @Override
        public HttpSession getSession(boolean create) {
            return _session;
        }
    }
}
