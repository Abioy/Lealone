/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package com.codefollower.h2.jdbcx;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import com.codefollower.h2.constant.SysProperties;
import com.codefollower.h2.engine.Constants;
import com.codefollower.h2.message.Trace;
import com.codefollower.h2.message.TraceSystem;

/**
 * This class is used to create new DataSource objects.
 * An application should not use this class directly.
 */
public class JdbcDataSourceFactory implements ObjectFactory {

    private static TraceSystem cachedTraceSystem;
    private Trace trace;

    static {
        com.codefollower.h2.Driver.load();
    }

    /**
     * The public constructor to create new factory objects.
     */
    public JdbcDataSourceFactory() {
        trace = getTraceSystem().getTrace("JDBCX");
    }

    /**
     * Creates a new object using the specified location or reference
     * information.
     *
     * @param obj the reference (this factory only supports objects of type
     *            javax.naming.Reference)
     * @param name unused
     * @param nameCtx unused
     * @param environment unused
     * @return the new JdbcDataSource, or null if the reference class name is
     *         not JdbcDataSource.
     */
    public synchronized Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
        if (trace.isDebugEnabled()) {
            trace.debug("getObjectInstance obj={0} name={1} nameCtx={2} environment={3}", obj, name, nameCtx, environment);
        }
        if (obj instanceof Reference) {
            Reference ref = (Reference) obj;
            if (ref.getClassName().equals(JdbcDataSource.class.getName())) {
                JdbcDataSource dataSource = new JdbcDataSource();
                dataSource.setURL((String) ref.get("url").getContent());
                dataSource.setUser((String) ref.get("user").getContent());
                dataSource.setPassword((String) ref.get("password").getContent());
                dataSource.setDescription((String) ref.get("description").getContent());
                String s = (String) ref.get("loginTimeout").getContent();
                dataSource.setLoginTimeout(Integer.parseInt(s));
                return dataSource;
            }
        }
        return null;
    }

    /**
     * INTERNAL
     */
    public static TraceSystem getTraceSystem() {
        synchronized (JdbcDataSourceFactory.class) {
            if (cachedTraceSystem == null) {
                cachedTraceSystem = new TraceSystem(
                        SysProperties.CLIENT_TRACE_DIRECTORY + "h2datasource" + Constants.SUFFIX_TRACE_FILE);
                cachedTraceSystem.setLevelFile(SysProperties.DATASOURCE_TRACE_LEVEL);
            }
            return cachedTraceSystem;
        }
    }

    Trace getTrace() {
        return trace;
    }

}