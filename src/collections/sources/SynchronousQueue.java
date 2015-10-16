/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package collections.sources;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * A {@linkplain BlockingQueue blocking queue} in which each insert
 * operation must wait for a corresponding remove operation by another
 * thread, and vice versa.  A synchronous queue does not have any
 * internal capacity, not even a capacity of one.  You cannot
 * <tt>peek</tt> at a synchronous queue because an element is only
 * present when you try to remove it; you cannot insert an element
 * (using any method) unless another thread is trying to remove it;
 * you cannot iterate as there is nothing to iterate.  The
 * <em>head</em> of the queue is the element that the first queued
 * inserting thread is trying to add to the queue; if there is no such
 * queued thread then no element is available for removal and
 * <tt>poll()</tt> will return <tt>null</tt>.  For purposes of other
 * <tt>Collection</tt> methods (for example <tt>contains</tt>), a
 * <tt>SynchronousQueue</tt> acts as an empty collection.  This queue
 * does not permit <tt>null</tt> elements.
 *
 * <p>Synchronous queues are similar to rendezvous channels used in
 * CSP and Ada. They are well suited for handoff designs, in which an
 * object running in one thread must sync up with an object running
 * in another thread in order to hand it some information, event, or
 * task.
 *
 * <p> This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to <tt>true</tt> grants threads access in FIFO order.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <E> the type of elements held in this collection
 */
public class SynchronousQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * This class implements extensions of the dual stack and dual
     * queue algorithms described in "Nonblocking Concurrent Objects
     * with Condition Synchronization", by W. N. Scherer III and
     * M. L. Scott.  18th Annual Conf. on Distributed Computing,
     * Oct. 2004 (see also
     * http://www.cs.rochester.edu/u/scott/synchronization/pseudocode/duals.html).
     * The (Lifo) stack is used for non-fair mode, and the (Fifo)
     * queue for fair mode. The performance of the two is generally
     * similar. Fifo usually supports higher throughput under
     * contention but Lifo maintains higher thread locality in common
     * applications.
     *
     * A dual queue (and similarly stack) is one that at any given
     * time either holds "data" -- items provided by put operations,
     * or "requests" -- slots representing take operations, or is
     * empty. A call to "fulfill" (i.e., a call requesting an item
     * from a queue holding data or vice versa) dequeues a
     * complementary node.  The most interesting feature of these
     * queues is that any operation can figure out which mode the
     * queue is in, and act accordingly without needing locks.
     *
     * Both the queue and stack extend abstract class Transferer
     * defining the single method transfer that does a put or a
     * take. These are unified into a single method because in dual
     * data structures, the put and take operations are symmetrical,
     * so nearly all code can be combined. The resulting transfer
     * methods are on the long side, but are easier to follow than
     * they would be if broken up into nearly-duplicated parts.
     *
     * The queue and stack data structures share many conceptual
     * similarities but very few concrete details. For simplicity,
     * they are kept distinct so that they can later evolve
     * separately.
     *
     * The algorithms here differ from the versions in the above paper
     * in extending them for use in synchronous queues, as well as
     * dealing with cancellation. The main differences include:
     *
     *  1. The original algorithms used bit-marked pointers, but
     *     the ones here use mode bits in nodes, leading to a number
     *     of further adaptations.
     *  2. SynchronousQueues must block threads waiting to become
     *     fulfilled.
     *  3. Support for cancellation via timeout and interrupts,
     *     including cleaning out cancelled nodes/threads
     *     from lists to avoid garbage retention and memory depletion.
     *
     * Blocking is mainly accomplished using LockSupport park/unpark,
     * except that nodes that appear to be the next ones to become
     * fulfilled first spin a bit (on multiprocessors only). On very
     * busy synchronous queues, spinning can dramatically improve
     * throughput. And on less busy ones, the amount of spinning is
     * small enough not to be noticeable.
     *
     * Cleaning is done in different ways in queues vs stacks.  For
     * queues, we can almost always remove a node immediately in O(1)
     * time (modulo retries for consistency checks) when it is
     * cancelled. But if it may be pinned as the current tail, it must
     * wait until some subsequent cancellation. For stacks, we need a
     * potentially O(n) traversal to be sure that we can remove the
     * node, but this can run concurrently with other threads
     * accessing the stack.
     *
     * While garbage collection takes care of most node reclamation
     * issues that otherwise complicate nonblocking algorithms, care
     * is taken to "forget" references to data, other nodes, and
     * threads that might be held on to long-term by blocked
     * threads. In cases where setting to null would otherwise
     * conflict with main algorithms, this is done by changing a
     * node's link to now point to the node itself. This doesn't arise
     * much for Stack nodes (because blocked threads do not hang on to
     * old head pointers), but references in Queue nodes must be
     * aggressively forgotten to avoid reachability of everything any
     * node has ever referred to since arrival.
     */

    /**
     * Shared internal API for dual stacks and queues.
     */
    static abstract class Transferer {
        /**
         * Performs a put or take.
         *
         * @param e if non-null, the item to be handed to a consumer;
         *          if null, requests that transfer return an item
         *          offered by producer.
         * @param timed if this operation should timeout
         * @param nanos the timeout, in nanoseconds
         * @return if non-null, the item provided or received; if null,
         *         the operation failed due to timeout or interrupt --
         *         the caller can distinguish which of these occurred
         *         by checking Thread.interrupted.
         */
        abstract Object transfer(Object e, boolean timed, long nanos);
    }

    /** The number of CPUs, for spin control */
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     */
    static final int maxTimedSpins = (NCPUS < 2)? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    static final int maxUntimedSpins = maxTimedSpins * 16;

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /** Dual stack */
    static final class TransferStack extends Transferer {
        /*
         * This extends Scherer-Scott dual stack algorithm, differing,
         * among other ways, by using "covering" nodes rather than
         * bit-marked pointers: Fulfilling operations push on marker
         * nodes (with FULFILLING bit set in mode) to reserve a spot
         * to match a waiting node.
         */

        /* Modes for SNodes, ORed together in node fields */
        /** Node represents an unfulfilled consumer */
        static final int REQUEST    = 0;
        /** Node represents an unfulfilled producer */
        static final int DATA       = 1;
        /** Node is fulfilling another unfulfilled DATA or REQUEST */
        static final int FULFILLING = 2;

        /** Return true if m has fulfilling bit set */
        //m为0或1时，返回false
        static boolean isFulfilling(int m) { return (m & FULFILLING) != 0; }

        /** Node class for TransferStacks. */
        static final class SNode {
            volatile SNode next;        // next node in stack
            volatile SNode match;       // the node matched to this
            volatile Thread waiter;     // to control park/unpark
            Object item;                // data; or null for REQUESTs
            int mode;
            // Note: item and mode fields don't need to be volatile
            // since they are always written before, and read after,
            // other volatile/atomic operations.

            SNode(Object item) {
                this.item = item;
            }

            static final AtomicReferenceFieldUpdater<SNode, SNode>
                nextUpdater = AtomicReferenceFieldUpdater.newUpdater
                (SNode.class, SNode.class, "next");

            boolean casNext(SNode cmp, SNode val) {
                return (cmp == next &&
                        nextUpdater.compareAndSet(this, cmp, val));
            }

            static final AtomicReferenceFieldUpdater<SNode, SNode>
                matchUpdater = AtomicReferenceFieldUpdater.newUpdater
                (SNode.class, SNode.class, "match");

            /**
             * Tries to match node s to this node, if so, waking up thread.
             * Fulfillers call tryMatch to identify their waiters.
             * Waiters block until they have been matched.
             *
             * @param s the node to match
             * @return true if successfully matched to s
             */
            boolean tryMatch(SNode s) {
                System.out.println(Thread.currentThread().getId() + "update this="+this+";match="+this.match);
                if (match == null &&
                    matchUpdater.compareAndSet(this, null, s)) {
                    System.out.println(Thread.currentThread().getId() + "update this="+this+";match="+s);
                    Thread w = waiter;
                    if (w != null) {    // waiters need at most one unpark
                        waiter = null;
                        //唤醒对方等待的线程
                        LockSupport.unpark(w);
                    }
                    return true;
                }
                return match == s;
            }

            /**
             * Tries to cancel a wait by matching node to itself.
             */
            void tryCancel() {
                matchUpdater.compareAndSet(this, null, this);
            }

            boolean isCancelled() {
                return match == this;
            }
        }

        /** The head (top) of the stack */
        volatile SNode head;

        static final AtomicReferenceFieldUpdater<TransferStack, SNode>
            headUpdater = AtomicReferenceFieldUpdater.newUpdater
            (TransferStack.class,  SNode.class, "head");

        boolean casHead(SNode h, SNode nh) {
            return h == head && headUpdater.compareAndSet(this, h, nh);
        }

        /**
         * Creates or resets fields of a node. Called only from transfer
         * where the node to push on stack is lazily created and
         * reused when possible to help reduce intervals between reads
         * and CASes of head and to avoid surges of garbage when CASes
         * to push nodes fail due to contention.
         */
        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null) s = new SNode(e);
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         * Puts or takes an item.
         */
        Object transfer(Object e, boolean timed, long nanos) {
            /*
             * Basic algorithm is to loop trying one of three actions:
             *
             * 1. If apparently empty or already containing nodes of same
             *    mode, try to push node on stack and wait for a match,
             *    returning it, or null if cancelled.
             *如果栈为空或者已经包含相同模式的节点，把节点推到栈上等待匹配
             * 2. If apparently containing node of complementary mode,
             *    try to push a fulfilling node on to stack, match
             *    with corresponding waiting node, pop both from
             *    stack, and return matched item. The matching or
             *    unlinking might not actually be necessary because of
             *    other threads performing action 3:
             *
             * 3. If top of stack already holds another fulfilling node,
             *    help it out by doing its match and/or pop
             *    operations, and then continue. The code for helping
             *    is essentially the same as for fulfilling, except
             *    that it doesn't return the item.
             */

            SNode s = null; // constructed/reused as needed
            //e为null时，表示操作想从栈中获取数据，比如take方法
            //e不为空时，表示携带数据入栈，如put方法
            int mode = (e == null)? REQUEST : DATA;
            System.out.println(Thread.currentThread().getId() + " transfer ,mode="+mode);
            //自旋
            for (;;) {
                SNode h = head;
                System.out.println(Thread.currentThread().getId() + " transfer ,head="+head+";h="+h);
                //头节点为空或匹配操作模式
                if (h == null || h.mode == mode) {  // empty or same-mode
                    //等待超时版本，如put/take 的超时方法
                    if (timed && nanos <= 0) {      // can't wait
                        //如果头节点已经取消，换下一个节点
                        if (h != null && h.isCancelled())
                            casHead(h, h.next);     // pop cancelled node
                        else
                            return null;
                    } else if (casHead(h, s = snode(s, e, h, mode))) {//构造s节点指向头节点
                        //等待对方线程满足匹配
                        System.out.println(Thread.currentThread().getId() + " push to stack, awaitFulfill,s="+s+";s.match="+s.match);
                        SNode m = awaitFulfill(s, timed, nanos);
                        System.out.println(Thread.currentThread().getId() + " isFulfill,match node="+m);
                        if (m == s) {               // wait was cancelled
                            clean(s);
                            return null;
                        }
                        //等待中返回后
                        if ((h = head) != null && h.next == s)
                            casHead(h, s.next);     // help s's fulfiller
                        //如果为take操作，返回匹配的节点中的元素，put操作返回当前节点
                        System.out.println(Thread.currentThread().getId() + " return ,m.item="+m.item+";s.itme="+s.item);
                        return mode == REQUEST? m.item : s.item;
                    }
                } else if (!isFulfilling(h.mode)) { // try to fulfill，栈顶存在节点，如果为mode=0或1时
                    //头节点取消，把下一个节点替换头节点
                    if (h.isCancelled())            // already cancelled
                        casHead(h, h.next);         // pop and retry
                    else if (casHead(h, s=snode(s, e, h, FULFILLING|mode))) {
                        System.out.println(Thread.currentThread().getId() + " try to fulfill ,h="+h+";s="+s);
                        for (;;) { // loop until matched or waiters disappear
                            SNode m = s.next;       // m is s's match
                            System.out.println(Thread.currentThread().getId() + " loop to  match ,h="+h+";m="+s.next);
                            if (m == null) {        // all waiters are gone
                                casHead(s, null);   // pop fulfill node
                                s = null;           // use new node next time
                                break;              // restart main loop
                            }
                            SNode mn = m.next;
                            System.out.println(Thread.currentThread().getId() + " tryMatch,mn="+mn);
                            //更新m,即对方线程的node的match节点为当前线程的节点s
                            if (m.tryMatch(s)) {
                                //两个节点都出栈
                                casHead(s, mn);     // pop both s and m
                                //如果是take操作，返回对方线程node中的元素，否则返回当前线程栈中的元素
                                System.out.println(Thread.currentThread().getId() + " return  ,m.item="+m.item+";s.item="+s.item+";s.match="+s.match);
                                return (mode == REQUEST)? m.item : s.item;
                            } else                  // lost match
                                s.casNext(m, mn);   // help unlink
                        }
                    }
                } else {                            // help a fulfiller
                    SNode m = h.next;               // m is h's match
                    if (m == null)                  // waiter is gone
                        casHead(h, null);           // pop fulfilling node
                    else {
                        SNode mn = m.next;
                        if (m.tryMatch(h))          // help match
                            casHead(h, mn);         // pop both h and m
                        else                        // lost match
                            h.casNext(m, mn);       // help unlink
                    }
                }
            }
        }

        /**
         * Spins/blocks until node s is matched by a fulfill operation.
         *
         * @param s the waiting node
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched node, or s if cancelled
         */
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            /*
             * When a node/thread is about to block, it sets its waiter
             * field and then rechecks state at least one more time
             * before actually parking, thus covering race vs
             * fulfiller noticing that waiter is non-null so should be
             * woken.
             *
             * When invoked by nodes that appear at the point of call
             * to be at the head of the stack, calls to park are
             * preceded by spins to avoid blocking when producers and
             * consumers are arriving very close in time.  This can
             * happen enough to bother only on multiprocessors.
             *
             * The order of checks for returning out of main loop
             * reflects fact that interrupts have precedence over
             * normal returns, which have precedence over
             * timeouts. (So, on timeout, one last check for match is
             * done before giving up.) Except that calls from untimed
             * SynchronousQueue.{poll/offer} don't check interrupts
             * and don't wait at all, so are trapped in transfer
             * method rather than calling awaitFulfill.
             */
            long lastTime = (timed)? System.nanoTime() : 0;
            Thread w = Thread.currentThread();
            SNode h = head;
            int spins = (shouldSpin(s)?
                         (timed? maxTimedSpins : maxUntimedSpins) : 0);
            for (;;) {
                if (w.isInterrupted())
                    s.tryCancel();
                //获取匹配的节点
                SNode m = s.match;
                if (m != null)
                    return m;
                //超时逻辑
                if (timed) {
                    long now = System.nanoTime();
                    nanos -= now - lastTime;
                    lastTime = now;
                    if (nanos <= 0) {
                        s.tryCancel();
                        continue;
                    }
                }
                //需要循环的时间片
                if (spins > 0)
                    spins = shouldSpin(s)? (spins-1) : 0;
                else if (s.waiter == null)
                    //设置s节点中的等待线程
                    s.waiter = w; // establish waiter so can park next iter
                else if (!timed)
                    //时间用完还未匹配，阻塞当前节点线程
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                    //带超时时间的阻塞
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Returns true if node s is at head or there is an active
         * fulfiller.
         */
        boolean shouldSpin(SNode s) {
            SNode h = head;
            return (h == s || h == null || isFulfilling(h.mode));
        }

        /**
         * Unlinks s from the stack.
         */
        void clean(SNode s) {
            s.item = null;   // forget item
            s.waiter = null; // forget thread

            /*
             * At worst we may need to traverse entire stack to unlink
             * s. If there are multiple concurrent calls to clean, we
             * might not see s if another thread has already removed
             * it. But we can stop when we see any node known to
             * follow s. We use s.next unless it too is cancelled, in
             * which case we try the node one past. We don't check any
             * further because we don't want to doubly traverse just to
             * find sentinel.
             */

            SNode past = s.next;
            if (past != null && past.isCancelled())
                past = past.next;

            // Absorb cancelled nodes at head
            SNode p;
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);

            // Unsplice embedded nodes
            while (p != null && p != past) {
                SNode n = p.next;
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }
    }

    /** Dual Queue */
    static final class TransferQueue extends Transferer {
        /*
         * This extends Scherer-Scott dual queue algorithm, differing,
         * among other ways, by using modes within nodes rather than
         * marked pointers. The algorithm is a little simpler than
         * that for stacks because fulfillers do not need explicit
         * nodes, and matching is done by CAS'ing QNode.item field
         * from non-null to null (for put) or vice versa (for take).
         */

        /** Node class for TransferQueue. */
        static final class QNode {
            volatile QNode next;          // next node in queue
            volatile Object item;         // CAS'ed to or from null
            volatile Thread waiter;       // to control park/unpark
            final boolean isData;

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            static final AtomicReferenceFieldUpdater<QNode, QNode>
                nextUpdater = AtomicReferenceFieldUpdater.newUpdater
                (QNode.class, QNode.class, "next");

            boolean casNext(QNode cmp, QNode val) {
                return (next == cmp &&
                        nextUpdater.compareAndSet(this, cmp, val));
            }

            static final AtomicReferenceFieldUpdater<QNode, Object>
                itemUpdater = AtomicReferenceFieldUpdater.newUpdater
                (QNode.class, Object.class, "item");

            boolean casItem(Object cmp, Object val) {
                return (item == cmp &&
                        itemUpdater.compareAndSet(this, cmp, val));
            }

            /**
             * Tries to cancel by CAS'ing ref to this as item.
             */
            void tryCancel(Object cmp) {
                itemUpdater.compareAndSet(this, cmp, this);
            }

            boolean isCancelled() {
                return item == this;
            }

            /**
             * Returns true if this node is known to be off the queue
             * because its next pointer has been forgotten due to
             * an advanceHead operation.
             */
            boolean isOffList() {
                return next == this;
            }
        }

        /** Head of queue */
        transient volatile QNode head;
        /** Tail of queue */
        transient volatile QNode tail;
        /**
         * Reference to a cancelled node that might not yet have been
         * unlinked from queue because it was the last inserted node
         * when it cancelled.
         */
        transient volatile QNode cleanMe;

        TransferQueue() {
            QNode h = new QNode(null, false); // initialize to dummy node.
            head = h;
            tail = h;
        }

        static final AtomicReferenceFieldUpdater<TransferQueue, QNode>
            headUpdater = AtomicReferenceFieldUpdater.newUpdater
            (TransferQueue.class,  QNode.class, "head");

        /**
         * Tries to cas nh as new head; if successful, unlink
         * old head's next node to avoid garbage retention.
         */
        void advanceHead(QNode h, QNode nh) {
            if (h == head && headUpdater.compareAndSet(this, h, nh))
                h.next = h; // forget old next
        }

        static final AtomicReferenceFieldUpdater<TransferQueue, QNode>
            tailUpdater = AtomicReferenceFieldUpdater.newUpdater
            (TransferQueue.class, QNode.class, "tail");

        /**
         * Tries to cas nt as new tail.
         */
        void advanceTail(QNode t, QNode nt) {
            if (tail == t)
                tailUpdater.compareAndSet(this, t, nt);
        }

        static final AtomicReferenceFieldUpdater<TransferQueue, QNode>
            cleanMeUpdater = AtomicReferenceFieldUpdater.newUpdater
            (TransferQueue.class, QNode.class, "cleanMe");

        /**
         * Tries to CAS cleanMe slot.
         */
        boolean casCleanMe(QNode cmp, QNode val) {
            return (cleanMe == cmp &&
                    cleanMeUpdater.compareAndSet(this, cmp, val));
        }

        /**
         * Puts or takes an item.
         */
        Object transfer(Object e, boolean timed, long nanos) {
            /* Basic algorithm is to loop trying to take either of
             * two actions:
             *
             * 1. If queue apparently empty or holding same-mode nodes,
             *    try to add node to queue of waiters, wait to be
             *    fulfilled (or cancelled) and return matching item.
             *
             * 2. If queue apparently contains waiting items, and this
             *    call is of complementary mode, try to fulfill by CAS'ing
             *    item field of waiting node and dequeuing it, and then
             *    returning matching item.
             *
             * In each case, along the way, check for and try to help
             * advance head and tail on behalf of other stalled/slow
             * threads.
             *
             * The loop starts off with a null check guarding against
             * seeing uninitialized head or tail values. This never
             * happens in current SynchronousQueue, but could if
             * callers held non-volatile/final ref to the
             * transferer. The check is here anyway because it places
             * null checks at top of loop, which is usually faster
             * than having them implicitly interspersed.
             */

            QNode s = null; // constructed/reused as needed
            //e!=null,表示添加元素，如put
            //e =null,表示移除元素，如take
            boolean isData = (e != null);

            for (;;) {
                QNode t = tail;
                QNode h = head;
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin
                //空队列或队列的尾节点的操作模式与当前线程操作相同 
                System.out.println(Thread.currentThread().getId() + " judge queue ,h==t："+(h==t)+";t.isData="+t.isData+";isData="+isData);
                if (h == t || t.isData == isData) { // empty or same-mode
                    QNode tn = t.next;
                    //tail节点已经被别的线程修改
                    if (t != tail)                  // inconsistent read
                        continue;
                    //t有next节点，说明它已经不是尾节点，把tn作为尾节点
                    if (tn != null) {               // lagging tail
                        advanceTail(t, tn);
                        continue;
                    }
                    //超时版本的方法并且已经超时，返回null
                    if (timed && nanos <= 0)        // can't wait
                        return null;
                    //创建一个队列节点，如果两个线程都创建成功，在casNext时，只能一个成功
                    System.out.println(Thread.currentThread().getId() + " create s node ,s="+s);
                    if (s == null)
                        s = new QNode(e, isData);
                    //把s节点连接到队列尾节点的next上
                    System.out.println(Thread.currentThread().getId() + " t.casNext link in s node ,s="+s);
                    //如果同时两个线程都到达，只有一个成功，失败者跳出此逻辑到达else分支
                    if (!t.casNext(null, s))        // failed to link in
                        continue;
                    //把tail节点指向s节点
                    advanceTail(t, s);              // swing tail and wait
                    System.out.println(Thread.currentThread().getId() + " advanceTail s to t, ,h="+h+";t="+t+";h="+h);
                    //等待对方匹配线程进行交换
                    Object x = awaitFulfill(s, e, timed, nanos);
                    if (x == s) {                   // wait was cancelled
                        clean(t, s);
                        return null;
                    }

                    if (!s.isOffList()) {           // not already unlinked
                        advanceHead(t, s);          // unlink if head
                        if (x != null)              // and forget fields
                            s.item = s;
                        s.waiter = null;
                    }
                    System.out.println(Thread.currentThread().getId() + "return, ,x="+x+";e="+e);
                    return (x != null)? x : e;
                } else {                            // complementary-mode
                    //当前头节点的下一节点,即对方线程创建的节点
                    QNode m = h.next;               // node to fulfill
                    //非一致性读
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read
                    System.out.println(Thread.currentThread().getId() + " h.next, h="+h+";m="+m);
                    Object x = m.item;
                    if (isData == (x != null) ||    // m already fulfilled
                        x == m ||                   // m cancelled
                        !m.casItem(x, e)) {         // lost CAS
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }
                    //头节点h指向下一节点
                    advanceHead(h, m);              // successfully fulfilled
                    System.out.println(Thread.currentThread().getId() + " advanceHead, h="+h+";m="+m);
                    //唤醒对方等待的线程
                    LockSupport.unpark(m.waiter);
                    System.out.println(Thread.currentThread().getId() + " reuturn , x="+x+";e="+e);
                    return (x != null)? x : e;
                }
            }
        }

        /**
         * Spins/blocks until node s is fulfilled.
         *
         * @param s the waiting node
         * @param e the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         */
        Object awaitFulfill(QNode s, Object e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            //与TransferStack.awaitFulfill的实现类似
            System.out.println(Thread.currentThread().getId() + " awaitFulfill, ,s="+s+";e="+e);
            long lastTime = (timed)? System.nanoTime() : 0;
            Thread w = Thread.currentThread();
            int spins = ((head.next == s) ?
                         (timed? maxTimedSpins : maxUntimedSpins) : 0);
            for (;;) {
                //线程被中断，取消
                if (w.isInterrupted())
                    s.tryCancel(e);
                //获取节点中的元素
                Object x = s.item;
                System.out.println(Thread.currentThread().getId() + " awaitFulfill, ,x="+x+";e="+e);
                //如果当前节点的元素和原来的元素不一样，说明已经被对方线程交换
                //返回对方的元素
                if (x != e)
                    return x;
                //超时版本
                if (timed) {
                    long now = System.nanoTime();
                    nanos -= now - lastTime;
                    lastTime = now;
                    if (nanos <= 0) {
                        s.tryCancel(e);
                        continue;
                    }
                }
                //自旋次数
                if (spins > 0)
                    --spins;
                else if (s.waiter == null)
                    //自旋次数小于等于0，设置等待的线程
                    s.waiter = w;
                else if (!timed)
                    //阻塞当前线程
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                   //有超时的阻塞当前线程
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Gets rid of cancelled node s with original predecessor pred.
         */
        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread
            /*
             * At any given time, exactly one node on list cannot be
             * deleted -- the last inserted node. To accommodate this,
             * if we cannot delete s, we save its predecessor as
             * "cleanMe", deleting the previously saved version
             * first. At least one of node s or the node previously
             * saved can always be deleted, so this always terminates.
             */
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head
                if (hn != null && hn.isCancelled()) {
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail
                if (t == h)
                    return;
        		QNode tn = t.next;
        		if (t != tail)
                    continue;
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }
                QNode dp = cleanMe;
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;
                    if (d == null ||               // d is gone or
                        d == dp ||                 // d is off list or
                        !d.isCancelled() ||        // d not cancelled or
                        (d != t &&                 // d not tail and
                         (dn = d.next) != null &&  //   has successor
                         dn != d &&                //   that is on list
                         dp.casNext(d, dn)))       // d unspliced
                        casCleanMe(dp, null);
                    if (dp == pred)
                        return;      // s is already saved node
                } else if (casCleanMe(null, pred))
                    return;          // Postpone cleaning s
            }
        }
    }

    /**
     * The transferer. Set only in constructor, but cannot be declared
     * as final without further complicating serialization.  Since
     * this is accessed only at most once per public method, there
     * isn't a noticeable performance penalty for using volatile
     * instead of final here.
     */
    private transient volatile Transferer transferer;

    /**
     * Creates a <tt>SynchronousQueue</tt> with nonfair access policy.
     */
    public SynchronousQueue() {
        this(false);
    }

    /**
     * Creates a <tt>SynchronousQueue</tt> with the specified fairness policy.
     *
     * @param fair if true, waiting threads contend in FIFO order for
     *        access; otherwise the order is unspecified.
     */
    public SynchronousQueue(boolean fair) {
        transferer = (fair)? new TransferQueue() : new TransferStack();
    }

    /**
     * Adds the specified element to this queue, waiting if necessary for
     * another thread to receive it.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E o) throws InterruptedException {
        if (o == null) throw new NullPointerException();
        if (transferer.transfer(o, false, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * up to the specified wait time for another thread to receive it.
     *
     * @return <tt>true</tt> if successful, or <tt>false</tt> if the
     *         specified waiting time elapses before a consumer appears.
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E o, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (o == null) throw new NullPointerException();
        if (transferer.transfer(o, true, unit.toNanos(timeout)) != null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, if another thread is
     * waiting to receive it.
     *
     * @param e the element to add
     * @return <tt>true</tt> if the element was added to this queue, else
     *         <tt>false</tt>
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * for another thread to insert it.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    public E take() throws InterruptedException {
        Object e = transferer.transfer(null, false, 0);
        if (e != null)
            return (E)e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, waiting
     * if necessary up to the specified wait time, for another thread
     * to insert it.
     *
     * @return the head of this queue, or <tt>null</tt> if the
     *         specified waiting time elapses before an element is present.
     * @throws InterruptedException {@inheritDoc}
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        Object e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return (E)e;
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     *
     * @return the head of this queue, or <tt>null</tt> if no
     *         element is available.
     */
    public E poll() {
        return (E)transferer.transfer(null, true, 0);
    }

    /**
     * Always returns <tt>true</tt>.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @return <tt>true</tt>
     */
    public boolean isEmpty() {
        return true;
    }

    /**
     * Always returns zero.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @return zero.
     */
    public int size() {
        return 0;
    }

    /**
     * Always returns zero.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @return zero.
     */
    public int remainingCapacity() {
        return 0;
    }

    /**
     * Does nothing.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     */
    public void clear() {
    }

    /**
     * Always returns <tt>false</tt>.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @param o the element
     * @return <tt>false</tt>
     */
    public boolean contains(Object o) {
        return false;
    }

    /**
     * Always returns <tt>false</tt>.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @param o the element to remove
     * @return <tt>false</tt>
     */
    public boolean remove(Object o) {
        return false;
    }

    /**
     * Returns <tt>false</tt> unless the given collection is empty.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @param c the collection
     * @return <tt>false</tt> unless given collection is empty
     */
    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    /**
     * Always returns <tt>false</tt>.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @param c the collection
     * @return <tt>false</tt>
     */
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns <tt>false</tt>.
     * A <tt>SynchronousQueue</tt> has no internal capacity.
     *
     * @param c the collection
     * @return <tt>false</tt>
     */
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns <tt>null</tt>.
     * A <tt>SynchronousQueue</tt> does not return elements
     * unless actively waited on.
     *
     * @return <tt>null</tt>
     */
    public E peek() {
        return null;
    }

    static class EmptyIterator<E> implements Iterator<E> {
        public boolean hasNext() {
            return false;
        }
        public E next() {
            throw new NoSuchElementException();
        }
        public void remove() {
            throw new IllegalStateException();
        }
    }

    /**
     * Returns an empty iterator in which <tt>hasNext</tt> always returns
     * <tt>false</tt>.
     *
     * @return an empty iterator
     */
    public Iterator<E> iterator() {
        return new EmptyIterator<E>();
    }

    /**
     * Returns a zero-length array.
     * @return a zero-length array
     */
    public Object[] toArray() {
        return new Object[0];
    }

    /**
     * Sets the zeroeth element of the specified array to <tt>null</tt>
     * (if the array has non-zero length) and returns it.
     *
     * @param a the array
     * @return the specified array
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        E e;
        while ( (e = poll()) != null) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        E e;
        while (n < maxElements && (e = poll()) != null) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /*
     * To cope with serialization strategy in the 1.5 version of
     * SynchronousQueue, we declare some unused classes and fields
     * that exist solely to enable serializability across versions.
     * These fields are never used, so are initialized only if this
     * object is ever serialized or deserialized.
     */

    static class WaitQueue implements java.io.Serializable { }
    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }
    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }
    private ReentrantLock qlock;
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        }
        else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    private void readObject(final java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue)
            transferer = new TransferQueue();
        else
            transferer = new TransferStack();
    }

}
