# 原理剖析（第 008 篇）CyclicBarrier工作原理分析
-

## 一、大致介绍

``` 
1、在前面章节了解了CountDownLatch/Semaphore后，想必大家已经对同步器有了一定的了解了；
2、而JDK中有一个关于线程之间相互等待的工具类，它是直接由独占锁ReentrantLock实现的，间接的也是基于AQS实现的；
3、那么本章节就和大家分享分析一下JDK1.8的CyclicBarrier的工作原理；　
```


## 二、简单认识CyclicBarrier

### 2.1 何为CyclicBarrier？

``` 
1、CyclicBarrier从英文字面上理解，循环栅栏，咋一看好像跟同步器没多大关系，而栅栏式一排排的阻拦着，好像也有点同步等待的意思；

2、CyclicBarrier是也一种同步帮助工具，允许多个线程相互等待，即多个线程到达同步点时被阻塞，直到最后一个线程到达同步点时栅栏才会被打开；

3、CyclicBarrier内部没有所谓的公平锁\非公平锁的静态内部类，只是利用了ReentrantLock(独占锁)、ConditionObject(条件对象)实现了线程之间相互等待的功能；
```


### 2.2 CyclicBarrier的state关键词

``` 
1、其实说CyclicBarrier这个类没有真正的state关键词，它只有parties线程总数量，count还没有进入阻塞的线程数量；

2、但是CyclicBarrier的实现是间接利用了ReentrantLock(独占锁)的父类AQS的state变量值；

3、CountDownLatch，A、B、C组线程同时执行，A先执行完的话就在那里等着，等所有A、B、C线程中执行最久的线程执行完了才开始执行各自的事件；
```


### 2.3 常用重要的方法
``` 
1、public CyclicBarrier(int parties)
   // 创建一个给定数值的栅栏总数，也就是支持参与线程的最多数值

2、public CyclicBarrier(int parties, Runnable barrierAction)
   // 创建一个给定数值的栅栏总数，也就是支持参与线程的最多数值，且当最后一个线程执行完时会回调barrierAction方法

3、private void nextGeneration()
   // 更新换代，改朝换代，触发唤醒所有在Lock对象上等待的线程，释放所有正在处于阻塞的线程
   
4、private void breakBarrier() 
   // 打破平衡，并设置打破平衡的标志，然后再唤醒所有被阻塞的线程，
   
5、public int await() 
   // 导致当前线程阻塞，直到其他线程调用trip.signal()或trip.signalAll()方法唤醒该线程
   
6、public int await(long timeout, TimeUnit unit)
   // 比await()多了两个参数，意思就是阻塞等待信号量的最大时长，等待的时间值为timeout，单位为unit；
   
7、private int dowait(boolean timed, long nanos) 
   // 阻塞等待的核心方法，如果不需要超时等待信号量的话则nanos参数是没用的，否则就有用
   
8、public boolean isBroken() 
   // 线程之间的等待，这样一个等待的平衡体系是否被打破
   
9、public void reset()
   // 重置为初始状态值，就像初始创建CyclicBarrier该实例对象一样，干干净净的初始状态值
   
10、public int getNumberWaiting()
    // 获取目前正在处于阻塞状态的线程数量值
   
11、public int getParties()
    // 获取线程数量，也就是栅栏数量总数值
```




### 2.4 设计与实现伪代码
``` 
1、等待被释放：
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

	await{
		阻塞等待的核心方法；
		内部会调用trip.await()方法进入Condition等待阻塞队列；
		一旦栅栏数量为零时则会逐个逐个将Condition等待的队列转移到CLH的等待阻塞队列；
		所有线程被唤醒后然后等待dowait方法内部lock.unlock()一个个释放线程等待；
		阻塞的最后一个线程还有机会执行构造方法传入的接口回调；
	}
```





### 2.5、CyclicBarrier生活细节化理解

``` 
比如百米赛跑，我就以赛跑为例生活化阐述该CyclicBarrier原理：

1、场景：百米赛跑十人参赛，终点处有一个裁判计数；

2、开跑一声枪响，十个人争先恐后的向终点跑去，真的是振奋多秒，令人振奋；

3、当一个人到达终点，这个人就完成了他的赛跑事情了，就没事一边玩去了，那么裁判则减去一个人；

4、随着人员陆陆续续的都跑到了终点，最后裁判计数显示还有0个人未到达，意思就是人员都达到了；

5、然后裁判就拿着登记的成绩屁颠屁颠去输入电脑登记了；

8、到此打止，这一系列的动作认为是A组线程等待另外其他组线程的操作，直到计数器为零，那么A则再干其他事情；
```






## 三、源码分析CyclicBarrier

### 3.1、CyclicBarrier构造器

``` 
1、构造器源码：
	// 构造方法一：
    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }
	
	// 构造方法二：
    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }	
	
2、创建一个给定数值的栅栏总数，也就是支持参与线程的最多数值，但是构造方法二还可以通过传入接口回调，当最后一个阻塞的线程被释放后，
   它将有机会执行这个被传入的回调接口barrierAction；
```






### 3.2、await()

``` 
1、源码：
    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @return the arrival index of the current thread, where index
     *         {@code getParties() - 1} indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was
     *         broken when {@code await} was called, or the barrier
     *         action (if present) failed due to an exception
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L); // 阻塞的核心方法，重心再次，通过ReentrantLock和Condition组合完成阻塞等待
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

2、阻塞等待的核心方法，内部会调用trip.await()方法进入Condition等待阻塞队列，一旦栅栏数量为零时则会逐个逐个将Condition等待的队列转移到CLH的等待阻塞队列；

3、所有线程被唤醒后然后等待dowait方法内部lock.unlock()一个个释放线程等待，阻塞的最后一个线程还有机会执行构造方法传入的接口回调；
```








### 3.3、dowait(boolean, long)

``` 
1、源码：
    /**
     * Main barrier code, covering the various policies.
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock; // 获取独占锁
        lock.lock(); // 通过lock其父类AQS的CLH队列阻塞在此，但是为啥又会继续往下进入临界区执行try方法，其原因就是trip.await()这句代码
        try {
            final Generation g = generation;

            if (g.broken) // 若平衡被一旦打破，则其他所有的线程都会抛出异常，因为即使这里没遇到抛异常，下面还会有 if (g.broken) 判断
                throw new BrokenBarrierException();

            if (Thread.interrupted()) { // 检测线程是否在其他地方被中断过，若任何一个线程被中断过
                breakBarrier(); // 那么则打破平衡，并设置打破平衡的标志，还原初始状态值，然后再唤醒所有被阻塞的线程，
                throw new InterruptedException();
            }

            int index = --count; // 执行一个则减1操作，正常情况下count表示还有多少个未进入临界区，即还在lock阻塞队列中
            if (index == 0) {  // tripped 当count值降为0后，则表明所有线程都执行完了，那么就可以happy的一起改朝换代去做其他事情了
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand; // 构造方法传入的接口回调对象
                    if (command != null) // 当接口不为空时，最后一个执行的线程有机会消费该回调方法
                        command.run();
                    ranAction = true;
                    nextGeneration(); // 改朝换代，该执行的都已经执行完了，还原为初始状态值，以便下次可以重复再次使用
                    return 0;
                } finally {
                    if (!ranAction) // 若最后一个线程眼看着要完事了，若出现了任何异常的话，也照样打破整体平衡，要么一起生要么一起亡
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            for (;;) { // 自旋的死循环操作方式
                try {
                    if (!timed) // 若不需要使用超时等待信号量的话，那么下面就直接调用trip.await()进入阻塞等待
                        trip.await(); // 正常情况下，代码执行到此就不动了，该方法内部已经调用了park方法导致线程阻塞等待
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos); // 在指定时间内等待信号量
                } catch (InterruptedException ie) { // 若在阻塞等待期间由于被中断了
                    if (g == generation && ! g.broken) { // 如果还没改朝换代，并且平衡标志位还为false的话，则继续打破平衡并且抛出中断异常
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken) // 这里也有 if (g.broken) 判断，若平衡被一旦打破，则其他所有的线程都会抛出异常
                    throw new BrokenBarrierException();

                if (g != generation) // 若已经被改朝换代了，那么则直接返回index值
                    return index;

                if (timed && nanos <= 0L) { // 若设置了超时标志，并且不管是传入的nanos值也好还是通过等待后返回的nanos也好，只要小于或等于零都会打破平衡
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock(); // 释放lock锁
        }
    }

2、dowait方法是CyclicBarrier实现阻塞等待的核心方法，当await方法被调用时阻塞等待被Condition的一个队列维护着；
   然而线程从await跳出来时，正常情况下一般都是由于发送了信号量，阻塞被解除，那么Condition的等待队列将会被转移至AQS的等待队列；
   然后一个逐渐锁释放，最后CyclicBarrier也处于了初始值状态，供下次调用使用；
   因此CyclicBarrier每用完一套整个流程，又会回到初始状态值，又可以被其他地方当做新创建的对象一样来使用，所以才成为循环栅栏；
```








### 3.4、breakBarrier()

``` 
1、源码：
    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     */
    private void breakBarrier() {
        generation.broken = true; // 设置打破平衡的标志
        count = parties; // 重新还原count为初始值
        trip.signalAll(); // 发送信号量，唤醒所有Condition中的等待队列
    }

2、打破平衡，并设置打破平衡的标志，然后再唤醒所有被阻塞的线程；
```








### 3.5、nextGeneration()

``` 
1、源码：
    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    private void nextGeneration() {
        // signal completion of last generation
        trip.signalAll();
        // set up next generation
        count = parties;
        generation = new Generation();
    }

2、唤醒所有在Condition中等待的队列，然后还原初始状态值，并且重新换掉generation的引用，改朝换代，为下一轮操作做准备；
```








### 3.6、AQS的await()

``` 
1、源码：
	// CyclicBarrier 的成员属性 trip( Condition类型 ) 对象的方法
	/**
	 * Implements interruptible condition wait.
	 * <ol>
	 * <li> If current thread is interrupted, throw InterruptedException.
	 * <li> Save lock state returned by {@link #getState}.
	 * <li> Invoke {@link #release} with saved state as argument,
	 *      throwing IllegalMonitorStateException if it fails.
	 * <li> Block until signalled or interrupted.
	 * <li> Reacquire by invoking specialized version of
	 *      {@link #acquire} with saved state as argument.
	 * <li> If interrupted while blocked in step 4, throw InterruptedException.
	 * </ol>
	 */
	public final void await() throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		Node node = addConditionWaiter(); // 将当前线程包装一下，然后添加到Condition自己维护的链表队列中
		int savedState = fullyRelease(node); // 释放当前线程占有的锁，如果不释放的话，那么在第二次调用lock.lock()的地方；
		// 如果第一个没执行完的话，那么则会一直阻塞等待，那么也就无法完成栅栏的功能了
		int interruptMode = 0;
		while (!isOnSyncQueue(node)) { // 是否在AQS的队列中
			LockSupport.park(this); // 如果不在AQS队列中的话，则阻塞等待，这里才是最最最核心阻塞的地方
			if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
				break;
		}
		
		// 如果在AQS队列中的话，那么则考虑重入锁，重新竞争锁，重新休息
		if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
			interruptMode = REINTERRUPT;
		if (node.nextWaiter != null) // clean up if cancelled
			unlinkCancelledWaiters();
		if (interruptMode != 0)
			reportInterruptAfterWait(interruptMode);
	}

2、其实该AQS的await方法，才是本章节重点中的重点，因为该方法涉及到为什么用了独占锁lock.lock之后，dowait方法里面通过调用了trip.await()进行阻塞的话，
   第二个、第三个线程怎么还会通过lock.lock调用之后还能进入临界区呢？这岂不是和独占锁的概念相矛盾么？
   
3、而AQS的方法则给大家解开了迷惑， 它会调用fullyRelease(node)释放当前线程占有的锁，所以lock.lock才不至于一直被阻塞在那里；

3、并且Condition也维护了自己的一个链表，凡是通过调用trip.await()方法的线程，都会首先进入Condition的队列，然后释放独占锁，想办法调用park方法锁住当前线程；
   然后在被信号量通知的时候，又会将Condition队列的结点转移到AQS的同步队列中，然后等待调用unlock逐个释放锁；
```







## 四、总结
``` 
1、有了分析CountDownLatch、Semaphore的基础后，再来分析CyclicBarrier显然有了扎实的功底，分析起来顺手多了；

2、在这里我简要总结一下CyclicBarrier的流程的一些特性：
	• 用途让一组线程互相等待，直到都到达公共屏障点才开始各自继续做各自的工作；
	• 可重复利用，每正常走完一次流程，或者异常结束流程，那么接下来一轮还是可以继续利用CyclicBarrier实现线程等待功能；
	• 共存亡，只要有一个线程有异常发生中断，那么其它线程都会被唤醒继续工作，然后接着就是抛异常处理；
``` 



## 五、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!