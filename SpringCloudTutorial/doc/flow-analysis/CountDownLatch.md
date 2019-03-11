# 原理剖析（第 007 篇）CountDownLatch工作原理分析
-

## 一、大致介绍

``` 
1、在前面章节了解了CAS、AQS后，想必大家已经对这块知识有了深刻的了解了；
2、而JDK中有一个关于计数同步器的工具类，它也是基于AQS实现的；
3、那么本章节就和大家分享分析一下JDK1.8的CountDownLatch的工作原理；　
```


## 二、简单认识CountDownLatch

### 2.1 何为CountDownLatch？

``` 
1、CountDownLatch从英文字面上理解，count计数做down的减法动作，而Latch又是门闩的意思；

2、CountDownLatch是一种同步帮助，允许一个或多个线程等待，直到在其他线程中执行的一组操作完成。；

3、CountDownLatch内部没有所谓的公平锁\非公平锁的静态内部类，只有一个Sync静态内部类，CountDownLatch内部基本上也是通过sync.xxx之类的这种调用方式的；

4、CountDownLatch内部维护了一个虚拟的资源池，如果许可数不为为0一直线程阻塞等待，直到许可数为0时才释放继续往下执行；
```


### 2.2 CountDownLatch的state关键词

``` 
1、其实CountDownLatch的实现也恰恰很好利用了其父类AQS的state变量值；

2、初始化一个数量值作为计数器的默认值，假设为N，那么当任何线程调用一次countDown则计数值减1，直到许可为0时才释放等待；

3、CountDownLatch，简单大致意思为：A组线程等待另外B组线程，B组线程执行完了，A组线程才可以执行；
```


### 2.3 常用重要的方法
``` 
1、public CountDownLatch(int count)
   // 创建一个给定许计数值的计数同步器对象

2、public void await()
   // 入队等待，直到计数器值为0则释放等待

3、public void countDown()
   // 释放许可，计数器值减1，若计数器值为0则触发释放无用结点
   
4、public long getCount() 
   // 获取目前最新的共享资源计数器值
```




### 2.4 设计与实现伪代码
``` 
1、获取共享锁：
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

	await{
		如果检测中断状态发现被中断过的话，那么则抛出InterruptedException异常
		如果尝试获取共享锁失败的话( 尝试获取共享锁的各种方式由AQS的子类实现 )，
		那么就新增共享锁结点通过自旋操作加入到队列中，然后通过调用LockSupport.park进入阻塞等待，直到计数器值为零才释放等待
	}
	
	
2、释放共享锁：
    public void countDown() {
        sync.releaseShared(1);
    }
	
	release{
		如果尝试释放共享锁失败的话( 尝试释放共享锁的各种方式由AQS的子类实现 )，
		那么通过自旋操作完成阻塞线程的唤起操作
	}
```





### 2.5、CountDownLatch生活细节化理解

``` 
比如百米赛跑，我就以赛跑为例生活化阐述该CountDownLatch原理：

1、场景：百米赛跑十人参赛，终点处有一个裁判计数；

2、开跑一声枪响，十个人争先恐后的向终点跑去，真的是振奋多秒，令人振奋；

3、当一个人到达终点，这个人就完成了他的赛跑事情了，就没事一边玩去了，那么裁判则减去一个人；

4、随着人员陆陆续续的都跑到了终点，最后裁判计数显示还有0个人未到达，意思就是人员都达到了；

5、然后裁判就拿着登记的成绩屁颠屁颠去输入电脑登记了；

8、到此打止，这一系列的动作认为是A组线程等待另外其他组线程的操作，直到计数器为零，那么A则再干其他事情；
```






## 三、源码分析CountDownLatch

### 3.1、CountDownLatch构造器

``` 
1、构造器源码：
    /**
     * Constructs a {@code CountDownLatch} initialized with the given count.
     *
     * @param count the number of times {@link #countDown} must be invoked
     *        before threads can pass through {@link #await}
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }
	
2、创建一个给定许计数值的计数同步器对象，计数器值必须大于零，count值最后赋值给了state这个共享资源值；
```






### 3.2、Sync同步器

``` 
1、AQS --> Sync
				  
2、CountDownLatch内的同步器都是通过Sync抽象接口来操作调用关系的，细看会发现基本上都是通过sync.xxx之类的这种调用方式的；
```





### 3.3、await()

``` 
1、源码：
    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * // 导致当前线程等待，直到计数器值减为零则释放等待，或者由于线程被中断也可导致释放等待；
     *
     * <p>If the current count is zero then this method returns immediately.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of two things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
	
2、await此方法被调用后，则一直会处于等待状态，其核心还是由于调用了LockSupport.park进入阻塞等待；
   当计数器值state=0时可以打破等待现状，当然还有线程被中断后也可以打破线程等待现状；
```





### 3.4、acquireSharedInterruptibly(int)

``` 
1、源码：
    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted()) // 调用之前先检测该线程中断标志位，检测该线程在之前是否被中断过
            throw new InterruptedException(); // 若被中断过的话，则抛出中断异常
        if (tryAcquireShared(arg) < 0) // 尝试获取共享资源锁，小于0则获取失败，此方法由AQS的具体子类实现
            doAcquireSharedInterruptibly(arg); // 将尝试获取锁资源的线程进行入队操作
    }
	
2、由于是实现同步计数器功能，所以tryAcquireShared首次调用必定小于0，则就顺利了进入了doAcquireSharedInterruptibly线程等待；
   至于首次调用为什么会小于0，请看子类的实现，子类的实现判断为 "(getState() == 0) ? 1 : -1" ;
```





### 3.5、tryAcquireShared(int)

``` 
1、源码：
	protected int tryAcquireShared(int acquires) {
		return (getState() == 0) ? 1 : -1; // 计数器值与零比较判断，小于零则获取锁失败，大于零则获取锁成功
	}
	
2、尝试获取共享锁资源，但是在计数器CountDownLatch这个功能中，小于零则需要入队，进入阻塞队列进行等待；大于零则唤醒等待队列，释放await方法的阻塞等待；
```





### 3.6、doAcquireSharedInterruptibly(int)

``` 
1、源码：
    /**
     * Acquires in shared interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
		// 按照给定的mode模式创建新的结点，模式有两种：Node.EXCLUSIVE独占模式、Node.SHARED共享模式；
        final Node node = addWaiter(Node.SHARED); // 创建共享模式的结点
        boolean failed = true;
        try {
            for (;;) { // 自旋的死循环操作方式
                final Node p = node.predecessor(); // 获取结点的前驱结点
                if (p == head) { // 若前驱结点为head的话，那么说明当前结点自然不用说了，仅次于老大之后的便是老二了咯
                    int r = tryAcquireShared(arg); // 而且老二也希望尝试去获取一下锁，万一头结点恰巧刚刚释放呢？希望还是要有的，万一实现了呢。。。
                    if (r >= 0) { // 若r>=0，说明已经成功的获取到了共享锁资源
                        setHeadAndPropagate(node, r); // 把当前node结点设置为头结点，并且调用doReleaseShared释放一下无用的结点
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
					
					// 但是在await方法首次被调用会流转到此，这个时候获取锁资源会失败，即r<0，所以会进入是否需要休眠的判断
					// 但是第一次进入休眠方法，因为被创建的结点waitStatus=0，所以会被修改一次为SIGNAL状态，再次循环一次
					// 而第二次循环进入shouldParkAfterFailedAcquire方法时，返回true就是需要休眠，则顺利调用park方式阻塞等待
                }
                if (shouldParkAfterFailedAcquire(p, node) && // 根据前驱结点看看是否需要休息一会儿
                    parkAndCheckInterrupt()) // 阻塞操作，正常情况下，获取不到共享锁，代码就在该方法停止了，直到被唤醒
					// 被唤醒后，发现parkAndCheckInterrupt()里面检测了被中断了的话，则补上中断异常，因此抛了个异常
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
	
2、doAcquireSharedInterruptibly在实现计数器原理的时候，主要的干的事情就是等待再等待，等到计数器值为零时才苏醒；
```





### 3.7、countDown()

``` 
1、源码：
    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the new count is zero then all waiting threads are re-enabled for
     * thread scheduling purposes.
     *
     * <p>If the current count equals zero then nothing happens.
     */
    public void countDown() {
        sync.releaseShared(1); // 释放一个许可资源 
    }
	
2、释放许可资源，也就是计数器值不断的做减1操作，当计数器值为零的时候，该方法将会释放所有正在等待的线程队列；
   至于为什么还会释放所有，请看后续的releaseShared(int arg)讲解；
```





### 3.8、releaseShared(int)

``` 
1、源码：
    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) { // 尝试释放共享锁资源，此方法由AQS的具体子类实现
            doReleaseShared(); // 自旋操作，唤醒后继结点
            return true; // 返回true表明所有线程已释放
        }
        return false; // 返回false表明目前还没释放完全，只要计数器值不为零的话，那么都会返回false
    }
	
2、releaseShared方法首先就判断了tryReleaseShared(arg)的返回值，但是计数器值只要不为零，都会返回false，因此releaseShared该方法就立马返回false了；

3、所以当计数器值慢慢减至零时，则立马返回true，那么也就立马会调用doReleaseShared释放所有等待的线程队列；
```






### 3.9、tryReleaseShared(int)

``` 
1、源码：
	// CountDownLatch 的静态内部类 Sync 类的 tryReleaseShared 方法	
	protected boolean tryReleaseShared(int releases) {
		// Decrement count; signal when transition to zero
		for (;;) { // 自旋的死循环操作方式
			int c = getState(); // 获取最新的计数器值
			if (c == 0) // 若计数器值为零，说明已经通过CAS操作减至零了，所以在并发中读取到零时并不需要做什么操作，因此返回false
				return false;
			int nextc = c-1; // 计数器值减1操作
			if (compareAndSetState(c, nextc)) // 通过CAS比较，顺利情况下设置成功返回true
				return nextc == 0; // 当通过计算操作得到的nextc为零时通过CAS修改成功，那么表明所有事情都已经做完，需要释放所有等待的线程队列
				
			// 若CAS失败，想都不用想肯定是由于并发操作，导致CAS失败，那么唯一可做的就是下一次循环查看是否已经被其他线程处理了
		}
	}
	
2、CountDownLatch的静态内部类实现父类AQS的方法，用来处理如何释放锁，笼统的讲，若返回负数则需要进入阻塞队列，否则需要释放所有等待队列；
```






### 3.10、doReleaseShared()

``` 
1、源码：
    /**
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         */
        for (;;) { // 自旋的死循环操作方式
            Node h = head; // 每次都是取出队列的头结点
            if (h != null && h != tail) { // 若头结点不为空且也不是队尾结点
                int ws = h.waitStatus; // 那么则获取头结点的waitStatus状态值
                if (ws == Node.SIGNAL) { // 若头结点是SIGNAL状态则意味着头结点的后继结点需要被唤醒了
					// 通过CAS尝试设置头结点的状态为空状态，失败的话，则继续循环，因为并发有可能其它地方也在进行释放操作
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    unparkSuccessor(h); // 唤醒头结点的后继结点
                }
				// 如头结点为空状态，则把其改为PROPAGATE状态，失败的则可能是因为并发而被改动过，则再次循环处理
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
			// 若头结点没有发生什么变化，则说明上述设置已经完成，大功告成，功成身退
			// 若发生了变化，可能是操作过程中头结点有了新增或者啥的，那么则必须进行重试，以保证唤醒动作可以延续传递
            if (h == head)                   // loop if head changed
                break;
        }
    }
	
2、主要目的是释放线程中所有等待的队列，当计数器值为零时，此方法马上会被调用，通过自旋方式轮询干掉所有等待的队列；
```







## 四、总结
``` 
1、有了分析AQS的基础后，再来分析CountDownLatch便快了很多；

2、在这里我简要总结一下CountDownLatch的流程的一些特性：
	• 管理一个大于零的计数器值；
	• 每countDown一次则state就减1一次，直到许可证数量等于0则释放队列中所有的等待线程；
	• 也可以通过countDown/await组合一起使用，来实现CyclicBarrier的功能；
``` 



## 五、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!