/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.cluster.concurrent;

import static org.lealone.cluster.tracing.Tracing.isTracing;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.lealone.cluster.tracing.TraceState;
import org.lealone.cluster.tracing.Tracing;
import org.lealone.cluster.utils.JVMStabilityInspector;
import org.lealone.cluster.utils.concurrent.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTracingAwareExecutorService implements TracingAwareExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTracingAwareExecutorService.class);

    protected abstract void addTask(FutureTask<?> futureTask);

    protected abstract void onCompletion();

    /** Task Submission / Creation / Objects **/

    @Override
    public <T> FutureTask<T> submit(Callable<T> task) {
        return submit(newTaskFor(task));
    }

    @Override
    public FutureTask<?> submit(Runnable task) {
        return submit(newTaskFor(task, null));
    }

    @Override
    public <T> FutureTask<T> submit(Runnable task, T result) {
        return submit(newTaskFor(task, result));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    protected <T> FutureTask<T> newTaskFor(Runnable runnable, T result) {
        return newTaskFor(runnable, result, Tracing.instance.get());
    }

    @SuppressWarnings("unchecked")
    protected <T> FutureTask<T> newTaskFor(Runnable runnable, T result, TraceState traceState) {
        if (traceState != null) {
            if (runnable instanceof TraceSessionFutureTask)
                return (TraceSessionFutureTask<T>) runnable;
            return new TraceSessionFutureTask<T>(runnable, result, traceState);
        }
        if (runnable instanceof FutureTask)
            return (FutureTask<T>) runnable;
        return new FutureTask<>(runnable, result);
    }

    @SuppressWarnings("unchecked")
    protected <T> FutureTask<T> newTaskFor(Callable<T> callable) {
        if (isTracing()) {
            if (callable instanceof TraceSessionFutureTask)
                return (TraceSessionFutureTask<T>) callable;
            return new TraceSessionFutureTask<T>(callable, Tracing.instance.get());
        }
        if (callable instanceof FutureTask)
            return (FutureTask<T>) callable;
        return new FutureTask<>(callable);
    }

    private class TraceSessionFutureTask<T> extends FutureTask<T> {
        private final TraceState state;

        public TraceSessionFutureTask(Callable<T> callable, TraceState state) {
            super(callable);
            this.state = state;
        }

        public TraceSessionFutureTask(Runnable runnable, T result, TraceState state) {
            super(runnable, result);
            this.state = state;
        }

        @Override
        public void run() {
            TraceState oldState = Tracing.instance.get();
            Tracing.instance.set(state);
            try {
                super.run();
            } finally {
                Tracing.instance.set(oldState);
            }
        }
    }

    @SuppressWarnings("unchecked")
    class FutureTask<T> extends SimpleCondition implements Future<T>, Runnable {
        private boolean failure;
        private Object result = this;
        private final Callable<T> callable;

        public FutureTask(Callable<T> callable) {
            this.callable = callable;
        }

        public FutureTask(Runnable runnable, T result) {
            this(Executors.callable(runnable, result));
        }

        @Override
        public void run() {
            try {
                result = callable.call();
            } catch (Throwable t) {
                JVMStabilityInspector.inspectThrowable(t);
                logger.warn("Uncaught exception on thread {}: {}", Thread.currentThread(), t);
                result = t;
                failure = true;
            } finally {
                signalAll();
                onCompletion();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return isSignaled();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            await();
            Object result = this.result;
            if (failure)
                throw new ExecutionException((Throwable) result);
            return (T) result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            await(timeout, unit);
            Object result = this.result;
            if (failure)
                throw new ExecutionException((Throwable) result);
            return (T) result;
        }
    }

    private <T> FutureTask<T> submit(FutureTask<T> task) {
        addTask(task);
        return task;
    }

    @Override
    public void execute(Runnable command) {
        addTask(newTaskFor(command, null));
    }

    @Override
    public void execute(Runnable command, TraceState state) {
        addTask(newTaskFor(command, null, state));
    }
}
