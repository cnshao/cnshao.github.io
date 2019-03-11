# 原理剖析（第 006 篇）Semaphore工作原理分析
-

## 一、大致介绍

``` 
1、在前面章节了解了CAS、AQS后，想必大家已经对这块知识有了深刻的了解了；
2、而JDK中有一个关于信号量的工具类，它也是基于AQS实现的，可以认为是synchronized的升级版(结尾处会讲解到)；
3、那么本章节就和大家分享分析一下JDK1.8的Semaphore的工作原理；　
```


## 二、简单认识Semaphore

### 2.1 何为Semaphore？

``` 
1、Semaphore顾名思义，叫信号量；

2、Semaphore可用来控制同时访问特定资源的线程数量，以此来达到协调线程工作；

3、Semaphore内部也有公平锁、非公平锁的静态内部类，就像ReentrantLock一样，Semaphore内部基本上是通过sync.xxx之类的这种调用方式的；

4、Semaphore内部维护了一个虚拟的资源池，如果许可为0则线程阻塞等待，直到许可大于0时又可以有机会获取许可了；
```


### 2.2 Semaphore的state关键词

``` 
1、其实Semaphore的实现也恰恰很好利用了其父类AQS的state变量值；

2、初始化一个数量值作为许可池的资源，假设为N，那么当任何线程获取到资源时，许可减1，直到许可为0时后续来的线程就需要等待；

3、Semaphore，简单大致意思为：A、B、C、D线程同时争抢资源，目前卡槽大小为2，若A、B正在执行且未执行完，那么C、D线程在门外等着，一旦A、B有1个执行完了，那么C、D就会竞争看谁先执行；
   state初始值假设为N，后续每tryAcquire()一次，state会CAS减1，当state为0时其它线程处于等待状态，
   直到state>0且<N后，进程又可以获取到锁进行各自操作了；
```


### 2.3 常用重要的方法
``` 
1、public Semaphore(int permits)
   // 创建一个给定许可数量的信号量对象，且默认以非公平锁方式获取资源

2、public Semaphore(int permits, boolean fair)
   // 创建一个给定许可数量的信号量对象，且是否公平方式由传入的fair布尔参数值决定

3、public void acquire() 
   // 从此信号量获取一个许可，当许可数量小于零时，则阻塞等待
   
4、public void acquire(int permits)  
   // 从此信号量获取permits个许可，当许可数量小于零时，则阻塞等待，但是当阻塞等待的线程被唤醒后发现被中断过的话则会抛InterruptedException异常
   
5、public void acquireUninterruptibly(int permits)   
   // 从此信号量获取permits个许可，当许可数量小于零时，则阻塞等待，但是当阻塞等待的线程被唤醒后发现被中断过的话不会抛InterruptedException异常
   
6、public void release()
   // 释放一个许可

7、public void acquire(int permits)
   // 释放permits个许可
```


### 2.4 设计与实现伪代码
``` 
1、获取共享锁：
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

	acquire{
		如果检测中断状态发现被中断过的话，那么则抛出InterruptedException异常
		如果尝试获取共享锁失败的话( 尝试获取共享锁的各种方式由AQS的子类实现 )，
		那么就新增共享锁结点通过自旋操作加入到队列中，并且根据结点中的waitStatus来决定是否调用LockSupport.park进行休息
	}
	
	
2、释放共享锁：
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }
	
	release{
		如果尝试释放共享锁失败的话( 尝试释放共享锁的各种方式由AQS的子类实现 )，
		那么通过自旋操作唤完成阻塞线程的唤起操作
	}
```


### 2.5、Semaphore生活细节化理解

``` 
比如我们天天在外面吃快餐，我就以吃快餐为例生活化阐述该Semaphore原理：

1、场景：餐厅只有一个排队的走廊，只有十个打饭窗口；

2、开饭时间点，刚开始的时候，人数不多，屈指可数，窗口很多，打饭菜自然很快，但随着时间的推移人数会越来越多，会呈现阻塞拥挤状况，排起了慢慢长队；

3、人数越来越多，但窗口只有十个，后来的就只好按先来后到排队等待打饭菜咯，前面窗口空缺一个，排队最前的一个则上去打饭菜，秩序有条不紊；

4、总之大家都挨个挨个排队打饭，先来后到，相安无事的顺序打饭菜；

5、到此打止，1、2、3、4可以认为是一种公平方式的信号量共享锁；

6、但是呢，还有那么些紧急赶时间的人，来餐厅时刚好看到师傅刚刚打完一个人的饭菜，于是插入去打饭菜敢时间；

7、如果敢时间人的来的时候发现师傅还在打饭菜，那么就只得乖乖的排队等候打饭菜咯；

8、到此打止，1、2、6、7可以认为是一种非公平方式的信号量共享锁；
```




## 三、源码分析Semaphore

### 3.1、Semaphore构造器

``` 
1、构造器源码：
    /**
     * Creates a {@code Semaphore} with the given number of
     * permits and nonfair fairness setting.
     *
     * @param permits the initial number of permits available.
     *        This value may be negative, in which case releases
     *        must occur before any acquires will be granted.
     */
    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    /**
     * Creates a {@code Semaphore} with the given number of
     * permits and the given fairness setting.
     *
     * @param permits the initial number of permits available.
     *        This value may be negative, in which case releases
     *        must occur before any acquires will be granted.
     * @param fair {@code true} if this semaphore will guarantee
     *        first-in first-out granting of permits under contention,
     *        else {@code false}
     */
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
	
2、创建一个给定许可数量的信号量对象，默认使用非公平锁，当然也可通过fair布尔参数值决定是公平锁还是非公平锁；
```



### 3.2、Sync同步器

``` 
1、AQS --> Sync ---> FairSync // 公平锁
				  |
				  |> NonfairSync // 非公平锁
				  
2、Semaphore内的同步器都是通过Sync抽象接口来操作调用关系的，细看会发现基本上都是通过sync.xxx之类的这种调用方式的；
```



### 3.3、acquire()

``` 
1、源码：
    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available, or the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1); // 调用父类AQS中的获取共享锁资源的方法
    }
	
2、acquire是信号量获取共享资源的入口，尝试获取锁资源，获取到了则立马返回并跳出该方法，没有获取到则该方法阻塞等待；
   其主要也是调用sync的父类AQS的acquireSharedInterruptibly方法；
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
	
2、acquireSharedInterruptibly是共享模式下线程获取锁资源的基类方法，每当线程获取到一次共享资源，则共享资源数值就会做减法操作，直到共享资源值小于0时，则线程阻塞进入队列等待；

3、而且该线程支持中断，也正如方法名称所意，当该方法检测到中断后则立马会抛出中断异常，让调用该方法的地方立马感知线程中断情况；
```




### 3.5、tryAcquireShared(int)

``` 
1、公平锁tryAcquireShared源码：
	// FairSync 公平锁的 tryAcquireShared 方法
	protected int tryAcquireShared(int acquires) {
		for (;;) { // 自旋的死循环操作方式
			if (hasQueuedPredecessors()) // 检查线程是否有阻塞队列
				return -1; // 如果有阻塞队列，说明共享资源的许可数量已经用完，返回-1乖乖进行入队操作
			int available = getState(); // 获取锁资源的最新内存值
			int remaining = available - acquires; // 计算得到剩下的许可数量
			if (remaining < 0 || // 若剩下的许可数量小于0，说明已经共享资源了，返回负数然后乖乖进入入队操作
				compareAndSetState(available, remaining)) // 若共享资源大于或等于0，防止并发则通过CAS操作占据最后一个共享资源
				return remaining; // 不管得到remaining后进入了何种逻辑，操作了之后再将remaining返回，上层会根据remaining的值进行判断是否需要入队操作
		}
	}

2、非公平锁tryAcquireShared源码：	
	// NonfairSync 非公平锁的 tryAcquireShared 方法
	protected int tryAcquireShared(int acquires) {
		return nonfairTryAcquireShared(acquires); // 
	}

	// NonfairSync 非公平锁父类 Sync 类的 nonfairTryAcquireShared 方法	
	final int nonfairTryAcquireShared(int acquires) {
		for (;;) { // 自旋的死循环操作方式
			int available = getState(); // 获取锁资源的最新内存值
			int remaining = available - acquires; // 计算得到剩下的许可数量
			if (remaining < 0 || // 若剩下的许可数量小于0，说明已经共享资源了，返回负数然后乖乖进入入队操作
				compareAndSetState(available, remaining)) // 若共享资源大于或等于0，防止并发则通过CAS操作占据最后一个共享资源
				return remaining; // 不管得到remaining后进入了何种逻辑，操作了之后再将remaining返回，上层会根据remaining的值进行判断是否需要入队操作
		}
	}		
	
3、tryAcquireShared法是AQS的子类实现的，也就是Semaphore的两个静态内部类实现的，目的就是通过CAS尝试获取共享锁资源，
   获取共享锁资源成功大于或等于0的自然数，获取共享锁资源失败则返回负数；
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
	
2、doAcquireSharedInterruptibly也是采用一个自旋的死循环操作方式，直到正常返回或者被唤醒抛出中断异常为止；
```




### 3.7、release()

``` 
1、源码：
    /**
     * Releases a permit, returning it to the semaphore.
     *
     * <p>Releases a permit, increasing the number of available permits by
     * one.  If any threads are trying to acquire a permit, then one is
     * selected and given the permit that was just released.  That thread
     * is (re)enabled for thread scheduling purposes.
     *
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link #acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    public void release() {
        sync.releaseShared(1); // 释放一个许可资源 
    }
	
2、该方法是调用其父类AQS的一个释放共享资源的基类方法；
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
            return true;
        }
        return false;
    }
	
2、releaseShared主要是进行共享锁资源释放，如果释放成功则唤醒队列等待的结点，如果失败则返回false，由上层调用方决定如何处理；
```




### 3.9、tryReleaseShared(int)

``` 
1、源码：
	// NonfairSync 和 FairSync 的父类 Sync 类的 tryReleaseShared 方法	
	protected final boolean tryReleaseShared(int releases) {
		for (;;) { // 自旋的死循环操作方式
			int current = getState(); // 获取最新的共享锁资源值
			int next = current + releases; // 对许可数量进行加法操作
			// int类型值小于0，是因为该int类型的state状态值溢出了，溢出了的话那得说明这个锁有多难释放啊，可能出问题了
			if (next < current) // overflow
				throw new Error("Maximum permit count exceeded");
			if (compareAndSetState(current, next)) // 
				return true; // 返回成功标志，告诉上层该线程已经释放了共享锁资源
		}
	}
	
2、tryReleaseShared主要通过CAS操作对state锁资源进行加法操作，腾出多余的共享锁资源供其它线程竞争；
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
	
2、doReleaseShared主要是释放共享许可，但是最重要的目的还是保证唤醒后继结点的传递，来让这些线程释放他们所持有的信号量；
```




## 四、总结
``` 
1、在分析了AQS之后，再来分析Semaphore是不是变得比较简单了；

2、在这里我简要总结一下Semaphore的流程的一些特性：
	• 管理一系列许可证，即state共享资源值；
	• 每acquire一次则state就减1一次，直到许可证数量小于0则阻塞等待；
	• 释放许可的时候要保证唤醒后继结点，以此来保证线程释放他们所持有的信号量；
	• 是Synchronized的升级版，因为Synchronized是只有一个许可，而Semaphore就像开了挂一样，可以有多个许可；
``` 



## 五、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!