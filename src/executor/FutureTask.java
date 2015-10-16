/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package executor;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.*;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the <tt>get</tt>
 * method will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled.
 *
 * <p>A <tt>FutureTask</tt> can be used to wrap a {@link Callable} or
 * {@link java.lang.Runnable} object.  Because <tt>FutureTask</tt>
 * implements <tt>Runnable</tt>, a <tt>FutureTask</tt> can be
 * submitted to an {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides
 * <tt>protected</tt> functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's <tt>get</tt> method
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /** Synchronization control for FutureTask */
    private final Sync sync;

    /**
     * Creates a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Callable</tt>.
     *
     * @param  callable the callable task
     * @throws NullPointerException if callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        sync = new Sync(callable);
    }

    /**
     * Creates a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param  runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * <tt>Future&lt;?&gt; f = new FutureTask&lt;Object&gt;(runnable, null)</tt>
     * @throws NullPointerException if runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        sync = new Sync(Executors.callable(runnable, result));
    }

    public boolean isCancelled() {
        return sync.innerIsCancelled();
    }

    public boolean isDone() {
        return sync.innerIsDone();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return sync.innerCancel(mayInterruptIfRunning);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        return sync.innerGet();
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return sync.innerGet(unit.toNanos(timeout));
    }

    /**
     * Protected method invoked when this task transitions to state
     * <tt>isDone</tt> (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * Sets the result of this Future to the given value unless
     * this future has already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon successful completion of the computation.
     * @param v the value
     */
    protected void set(V v) {
        sync.innerSet(v);
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause, unless this Future has
     * already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon failure of the computation.
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        sync.innerSetException(t);
    }

    // The following (duplicated) doc comment can be removed once
    //
    // 6270645: Javadoc comments should be inherited from most derived
    //          superinterface or superclass
    // is fixed.
    /**
     * Sets this Future to the result of its computation
     * unless it has been cancelled.
     */
    public void run() {
        sync.innerRun();
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this Future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     * @return true if successfully run and reset
     */
    //执行结果而不设置返回结果，重置了任务的状态，用于执行多于一次的任务
    protected boolean runAndReset() {
        return sync.innerRunAndReset();
    }

    /**
     * Synchronization control for FutureTask. Note that this must be
     * a non-static inner class in order to invoke the protected
     * <tt>done</tt> method. For clarity, all inner class support
     * methods are same as outer, prefixed with "inner".
     *
     * Uses AQS sync state to represent run status
     */
    private final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -7828117401763700385L;

        /** 代表任务正在运行的状态值 */
        private static final int RUNNING   = 1;
        /** 代表任务已经运行的状态值 */
        private static final int RAN       = 2;
        /** 代表任务已经取消的状态值 */
        private static final int CANCELLED = 4;

        /** 潜在的计算 */
        private final Callable<V> callable;
        /** get()方法返回的结果*/
        private V result;
        /** get()方法抛出的异常 */
        private Throwable exception;

        /**
         * The thread running task. When nulled after set/cancel, this
         * indicates that the results are accessible.  Must be
         * volatile, to ensure visibility upon completion.
         */
        //运行任务的线程
        private volatile Thread runner;

        Sync(Callable<V> callable) {
            this.callable = callable;
        }
        //判断已经运行或取消的状态
        private boolean ranOrCancelled(int state) {
            return (state & (RAN | CANCELLED)) != 0;
        }

        /**
         * Implements AQS base acquire to succeed if ran or cancelled
         */
        //任务完成返回1，否则进入同步队列等待
        protected int tryAcquireShared(int ignore) {
            return innerIsDone()? 1 : -1;
        }

        /**
         * Implements AQS base release to always signal after setting
         * final done status by nulling runner thread.
         */
        //释放共享的同步状态
        protected boolean tryReleaseShared(int ignore) {
            runner = null;
            return true;
        }
        //判断是否已经取消
        boolean innerIsCancelled() {
            return getState() == CANCELLED;
        }
        //判断是否已经完成
        boolean innerIsDone() {
            return ranOrCancelled(getState()) && runner == null;
        }

        V innerGet() throws InterruptedException, ExecutionException {
            //支持可中断的同步状态获取
            acquireSharedInterruptibly(0);
            //任务取消抛出CancellationException
            if (getState() == CANCELLED)
                throw new CancellationException();
            //存在别的异常则抛出ExecutionException
            if (exception != null)
                throw new ExecutionException(exception);
            //如果能够到达这里，说明计算已经完成，innerSet方法已经设置了结果
            return result;
        }

        V innerGet(long nanosTimeout) throws InterruptedException, ExecutionException, TimeoutException {
            //延期地获取同步状态，如果超时，抛出TimeoutException
            if (!tryAcquireSharedNanos(0, nanosTimeout))
                throw new TimeoutException();
           //任务取消抛出CancellationException
            if (getState() == CANCELLED)
                throw new CancellationException();
          //存在别的异常则抛出ExecutionException
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }
        //设置任务结果
        void innerSet(V v) {
            //自旋直至任务完成或取消
    	    for (;;) {
        		int s = getState();
        		if (s == RAN)
        		    return;
                if (s == CANCELLED) {
        		    // aggressively release to set runner to null,
        		    // in case we are racing with a cancel request
        		    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
                //CAS设置任务状态为完成
        		if (compareAndSetState(s, RAN)) {
        		        //任务完成，设置结果，并释放同步状态
                        result = v;
                        releaseShared(0);
                        done();
        		    return;
                }
            }
        }

        void innerSetException(Throwable t) {
    	    for (;;) {
        		int s = getState();
        		if (s == RAN)
        		    return;
                if (s == CANCELLED) {
    		    // aggressively release to set runner to null,
    		    // in case we are racing with a cancel request
    		    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
                if (compareAndSetState(s, RAN)) {
                    exception = t;
                    result = null;
                    releaseShared(0);
                    done();
                    return;
                }
    	    }
        }
        //取消任务
        boolean innerCancel(boolean mayInterruptIfRunning) {
            //自旋
    	    for (;;) {
    	        //获取任务状态
        		int s = getState();
        		//如果任务完成或已经取消，则返回false,取消失败。
        		if (ranOrCancelled(s))
        		    return false;
        		//CAs设置任务的状态为取消。
        		if (compareAndSetState(s, CANCELLED))
        		    break;
    	    }
    	    //如果需要中断任务的线程
            if (mayInterruptIfRunning) {
                Thread r = runner;
                if (r != null)
                    r.interrupt();
            }
            //释放同步状态
            releaseShared(0);
            done();
            return true;
        }
        //运行任务
        void innerRun() {
            //CAS设置任务状态为运行中，失败则返回
            if (!compareAndSetState(0, RUNNING))
                return;
            try {
                //设置运行任务的线程
                runner = Thread.currentThread();
                //再次检查任务状态为RUNNING时，调用callable.call()执行运算后返回结果
                if (getState() == RUNNING) // recheck after setting thread
                    innerSet(callable.call());
                else
                    //如果不是运行状态，则释放同步状态
                    releaseShared(0); // cancel
            } catch (Throwable ex) {
                //设置异常
                innerSetException(ex);
            }
        }

        boolean innerRunAndReset() {
            //CAS设置任务状态为运行中，失败则返回
            if (!compareAndSetState(0, RUNNING))
                return false;
            try {
                runner = Thread.currentThread();
                //不设置结果
                if (getState() == RUNNING)
                    callable.call(); // don't set result
                runner = null;
                //把任务状态重置 为0
                return compareAndSetState(RUNNING, 0);
            } catch (Throwable ex) {
                innerSetException(ex);
                return false;
            }
        }
    }
}
