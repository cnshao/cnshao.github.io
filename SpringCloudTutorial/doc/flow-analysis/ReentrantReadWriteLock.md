# 原理剖析（第 009 篇）ReentrantReadWriteLock工作原理分析
-

## 一、大致介绍

``` 
1、在前面章节了解了AQS和Semaphore后，想必大家已经对获取独占锁、获取共享锁有了一定的了解了；
2、而JDK中有一个关于读锁写锁分离的工具类，读锁是共享锁，写锁是排他锁，也是基于AQS实现的；
3、那么本章节就和大家分享分析一下JDK1.8的ReentrantReadWriteLock的工作原理；　
```


## 二、简单认识ReentrantReadWriteLock

### 2.1 何为ReentrantReadWriteLock？

``` 
1、ReentrantReadWriteLock从英文字面上可理解为可重入的读写锁；

2、ReentrantReadWriteLock具备读锁与写锁，读锁是共享锁使用共享模式，写锁是排它锁使用独占模式；

3、ReentrantReadWriteLock具备公平与非公平策略，"读-写"互斥、"写-写"互斥；
```


### 2.2 ReentrantReadWriteLock的state关键词

``` 
1、ReentrantReadWriteLock的state关键字，有点像ThreadPoolExecutor工作线程数量值ctl的味道；

2、ReentrantReadWriteLock高16位为读锁的计数值，低16位为写锁的计数值；
```




### 2.3 常用重要的成员属性

``` 
1、private final ReentrantReadWriteLock.ReadLock readerLock;
   // 读锁对象

2、private final ReentrantReadWriteLock.WriteLock writerLock;
   // 写锁对象

3、final Sync sync;
   // 同步器

4、static final int SHARED_SHIFT   = 16; // 分界线偏移值，用来向左或向右偏移尾数，以此来获取读写锁计数值
   static final int SHARED_UNIT    = (1 << SHARED_SHIFT); // 读锁需要加1时递增的增量
   static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1; // 读锁、写锁的最大计数值数量
   static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1; // 写锁掩码，用于写锁计数值的低16位有效值

5、private transient ThreadLocalHoldCounter readHolds;
   // 保存当前线程重入读锁次数的容器，当读锁重入次数为0时则会被移除掉

6、private transient HoldCounter cachedHoldCounter;
   // 最近一个成功获取读锁的线程的计数对象
```




### 2.4 常用重要的方法
``` 
1、public ReentrantReadWriteLock()
   // 创建一个读写锁的对象，默认的策略是非公平策略

2、public ReentrantReadWriteLock(boolean fair)
   // 创建一个读写锁的对象，且是否公平方式由传入的fair布尔参数值决定

3、public ReentrantReadWriteLock.WriteLock writeLock()
   // 获取写锁对象
   
4、public ReentrantReadWriteLock.ReadLock  readLock()
   // 获取读锁对象
   
5、public final boolean isFair() 
   // 查看当前的读写锁对象用的策略方式是啥，是公平策略，还是非公平策略
   
6、protected Thread getOwner()
   // 获取持有独占锁的线程对象
   
7、public int getReadLockCount()
   // 获取持有读锁计数值
   
8、public boolean isWriteLocked()
   // 查看是否有线程持有写锁
   
9、public boolean isWriteLockedByCurrentThread()
   // 查看当前的线程是不是持有独占写锁的线程
   
10、public int getWriteHoldCount()
    // 获取当前线程在此写锁上保持的重入锁数量
   
11、public int getReadHoldCount()
    // 获取当前线程在此读锁上保持的重入锁数量
	
12、protected Collection<Thread> getQueuedWriterThreads()
    // 返回一个 collection，它包含可能正在等待获取写入锁的线程

13、protected Collection<Thread> getQueuedReaderThreads()
    // 返回一个 collection，它包含可能正在等待获取读取锁的线程

14、public final boolean hasQueuedThreads()
    // 查看是否有阻塞的线程队列

15、public final boolean hasQueuedThread(Thread thread)
    // 查询给定的线程是否正处于阻塞队列中

16、abstract boolean readerShouldBlock();
    // 抽象方法，由AQS的子类实现，读锁是否需要阻塞

17、abstract boolean writerShouldBlock();
    // 抽象方法，由AQS的子类实现，写锁是否需要阻塞

18、static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
    // 获取读锁的计数值

19、static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }
    // 获取写锁的计数值
```






### 2.5 设计与实现伪代码
``` 
1、获取写锁：
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

	acquire{
		如果尝试获取独占锁失败的话( 尝试获取独占锁的各种方式由AQS的子类实现 )，
		那么就新增独占锁结点通过自旋操作加入到队列中，并且根据结点中的waitStatus来决定是否调用LockSupport.park进行休息
	}
	
	
2、释放写锁：
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
	
	release{
		如果尝试释放独占锁成功的话( 尝试释放独占锁的各种方式由AQS的子类实现 )，
		那么取出头结点并根据结点waitStatus来决定是否有义务唤醒其后继结点
	}

3、获取读锁：
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }
	
	acquireShared{
		如果尝试获取共享锁失败的话( 尝试获取共享锁的各种方式由AQS的子类实现 )，
		那么新增共享锁结点通过自旋操作加入到队尾中，并且根据结点中的waitStatus来决定是否调用LockSupport.park进行休息
	}

4、释放读锁：
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }
	
	releaseShared{
		如果尝试释放共享锁失败的话( 尝试释放共享锁的各种方式由AQS的子类实现 )，
		那么通过自旋操作唤完成阻塞线程的唤起操作
	}
```






## 三、源码分析ReentrantReadWriteLock

### 3.1、Sync同步器

``` 
1、AQS --> Sync ---> FairSync // 公平策略
				  |
				  |> NonfairSync // 非公平策略
				  
2、ReentrantReadWriteLock内的同步器都是通过Sync抽象接口来操作调用关系的，细看会发现基本上都是通过sync.xxx之类的这种调用方式的；
```






### 3.2、ReentrantReadWriteLock构造器

``` 
1、构造器源码：
	// 构造方法一：
    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * default (nonfair) ordering properties.
     */
    public ReentrantReadWriteLock() {
        this(false);
    }
	
	// 构造方法二：
    /**
     * Creates a new {@code ReentrantReadWriteLock} with
     * the given fairness policy.
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }	
	

2、xxxxxxxxxxxxxxxxxxx
```





### 3.3、tryAcquire(int)

``` 
1、源码：
	// ReentrantReadWriteLock 的静态内部类 Sync 的 tryAcquire 方法，写锁获取锁的核心方法
	protected final boolean tryAcquire(int acquires) {
		/*
		 * Walkthrough:
		 * 1. If read count nonzero or write count nonzero
		 *    and owner is a different thread, fail.
		 * 2. If count would saturate, fail. (This can only
		 *    happen if count is already nonzero.)
		 * 3. Otherwise, this thread is eligible for lock if
		 *    it is either a reentrant acquire or
		 *    queue policy allows it. If so, update state
		 *    and set owner.
		 */
		Thread current = Thread.currentThread(); // 获取当前的线程对象
		int c = getState(); // 获取最新的读写锁资源值
		int w = exclusiveCount(c); // 然后查看独占锁的占有线程数量
		if (c != 0) { // c不为零，说明有线程占有写锁
			// (Note: if c != 0 and w == 0 then shared count != 0)
			// 如果w=0，说明已经有线程占有了读锁，那么当前想获取写锁的话没必要了，直接返回false到队列中排队去
			// 如果w!=0，说明已经有线程占有了写锁，那么再看看当前线程是不是那个正在持有写锁的线程？
			// 如果当前线程不是持有写锁的那个线程，则返回false到队列中排队去；
			if (w == 0 || current != getExclusiveOwnerThread())
				return false;
			// 执行到此，说明同一个线程先是持有了写锁，然后还想继续占有写锁，那么就是重入的概念，随时欢迎重入锁
			if (w + exclusiveCount(acquires) > MAX_COUNT) // 当持有写锁的数量超过MAX_COUNT=65535时，则抛出异常，试想一下这个写锁如果达到了65535这个数量级的话，
				// 可能是递归导致的，可能是其他原因导致的，反正不管怎么着总不至于都溢出了吧；
				// 因此是有问题的，所以这里抛出了异常让调用方去查查到底是什么原因；
				throw new Error("Maximum lock count exceeded");
			// Reentrant acquire
			setState(c + acquires); // 对于写锁的再次重入，来一个收一个，赋值写锁状态值，然后返回true继续执行临界区的代码
			return true;
		}
		
		// 执行到此，c=0，也就是说目前还没有线程占用读锁和写锁
		if (writerShouldBlock() || // 抽象方法需要子类来实现，根据写锁是否需要阻塞的标志来判断，true则需要阻塞，false则不需要阻塞
		// writerShouldBlock()在公平策略中，当有阻塞队列时则返回true需要阻塞，无阻塞队列时返回false不需要阻塞；
		// writerShouldBlock()在非公平策略中，永远都返回false写锁不需要阻塞；
			!compareAndSetState(c, c + acquires)) 
			// 如果需要阻塞，则直接返回false到队列中排队去
			// 如果需要阻塞，则通过CAS尝试占用写锁资源，如果尝试占用写锁失败，说明由于并发c的值已经被改动了，所以还是乖乖到队列中排队去
			return false;
		setExclusiveOwnerThread(current); // 走到这里，说明写锁经过千辛万苦终于拿到写锁的执行权了，则可以继续执行临界区代码块了
		return true;
	}

2、通过写锁writeLock.lock()最终调用的是Sync的tryAcquire尝试获取锁方式，从而可以得出几个结论：
	• 已持有读锁的线程不能再持有写锁；
	• 已持有写锁的线程可以再持有写锁，这和ReentrantLock的重入锁概念是一致的；
	• 已持有读锁的线程，其他线程是不能持有写锁的；
	• 已持有写锁的线程，其他线程是不能持有写锁的；
	
3、至于返回false后面是如何进入阻塞队列的话，这里就不多讲了，因为前面已经讲过了，见( 原理剖析（第 005 篇）AQS工作原理分析 )；
```





### 3.4、writerShouldBlock/readerShouldBlock

``` 
1、源码：
    /**
     * Fair version of Sync：公平策略版本的同步器；
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;
		
		/**
		 * 公平策略的写锁是否需要阻塞，阻塞的判断依据就是：当有阻塞队列时则返回true需要阻塞，无阻塞队列时返回false不需要阻塞；
		 */
        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }
		
		/**
		 * 公平策略的读锁是否需要阻塞，阻塞的判断依据就是：当有阻塞队列时则返回true需要阻塞，无阻塞队列时返回false不需要阻塞；
		 */
        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }
	
    /**
     * Nonfair version of Sync：非公平策略版本的同步器；
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
		
		/**
		 * 非公平策略的写锁是否需要阻塞，阻塞的判断依据就是：直接是默认返回false，永远都返回false写锁不需要阻塞；
		 */
        final boolean writerShouldBlock() {
            return false; // writers can always barge
        }
		
		/**
		 * 非公平策略的读锁是否需要阻塞，阻塞的判断依据就是：阻塞队列中的第一个结点是不是独占式结点，如果是则返回true表明读锁需要阻塞，否则返回false不需要阻塞；
		 */
        final boolean readerShouldBlock() {
            /* As a heuristic to avoid indefinite writer starvation,
             * block if the thread that momentarily appears to be head
             * of queue, if one exists, is a waiting writer.  This is
             * only a probabilistic effect since a new reader will not
             * block if there is a waiting writer behind other enabled
             * readers that have not yet drained from the queue.
             */
            return apparentlyFirstQueuedIsExclusive();
        }
    }
	
2、FairSync/NonfairSync主要重写了父类Sync的读锁、写锁是否需要阻塞，在公平策略与非公平策略中都有各自的实现；
```






### 3.5、tryRelease(int)

``` 
1、源码：
	// ReentrantReadWriteLock 的静态内部类 Sync 的 tryRelease 方法，写锁释放锁的核心方法
	protected final boolean tryRelease(int releases) {
		if (!isHeldExclusively()) // 如果当前线程没有获取独占式锁的话，也就是说当前线程没有持有写锁的话，那么就直接抛异常
			throw new IllegalMonitorStateException();
			// 之所以抛异常，是因为本来该方法就是持有写锁的线程来调用释放操作的，但是结果却发现当前线程自己没有吃有写锁，
			// 那岂不是尴尬，所以期间肯定出现了其他未知的问题，因此直接抛异常，告诉调用方，肯定有地方用错了还是啥啥啥的
		int nextc = getState() - releases; // 获取最新的锁资源值并且做减法操作，减去releases
		boolean free = exclusiveCount(nextc) == 0; // 如果得出的nextc=0，那么说明持有写锁的线程已经完全被释放了
		if (free) // 一般情况下，通过锁资源值做减法操作，一般都会得到结果零，则设置独占式线程对象exclusiveOwnerThread为空
			setExclusiveOwnerThread(null); // 
		setState(nextc); // 如果能执行到此，说明是重入锁，需要多重释放才能降低为零，反正如果没减至零最后都需要更新减后的结果值
		return free; // 返回true说明已经没有线程持有写锁了，返回false说明还有线程持有写锁
	}

2、该方法主要讲解了写锁如何进行释放资源，最后不管做减法的结果如何，都会更新减法之后的结果赋值到state锁资源值；
```





### 3.6、tryAcquireShared(int)

``` 
1、源码：
	// ReentrantReadWriteLock 的静态内部类 Sync 的 tryAcquireShared 方法，读锁获取锁的核心方法
	protected final int tryAcquireShared(int unused) {
		/*
		 * Walkthrough:
		 * 1. If write lock held by another thread, fail.
		 * 2. Otherwise, this thread is eligible for
		 *    lock wrt state, so ask if it should block
		 *    because of queue policy. If not, try
		 *    to grant by CASing state and updating count.
		 *    Note that step does not check for reentrant
		 *    acquires, which is postponed to full version
		 *    to avoid having to check hold count in
		 *    the more typical non-reentrant case.
		 * 3. If step 2 fails either because thread
		 *    apparently not eligible or CAS fails or count
		 *    saturated, chain to version with full retry loop.
		 */
		Thread current = Thread.currentThread(); // 获取当前线程对象
		int c = getState(); // 获取内存中最新的锁资源值
		if (exclusiveCount(c) != 0 && // 如果有线程持有写锁
			getExclusiveOwnerThread() != current) // 并且持有写锁的线程不是当前线程
			return -1; // 那么则返回-1表明获取读锁失败，应该乖乖进入CLH阻塞队列
		int r = sharedCount(c); // 获取读锁共享计数
		
		// readerShouldBlock()在公平策略中，当有阻塞队列时则返回true需要阻塞，无阻塞队列时返回false不需要阻塞；
		// readerShouldBlock()在非公平策略中，阻塞队列中的第一个结点是不是独占式结点，如果是则返回true表明读锁需要阻塞，否则返回false不需要阻塞；
		if (!readerShouldBlock() && // 如果读锁不需要阻塞处理
			r < MAX_COUNT && // 如果读锁计数值没有超过最大限制值
			compareAndSetState(c, c + SHARED_UNIT)) { // 并且通过CAS尝试获取读锁资源
			// 能执行到if里面来，说明当前线程已经成功的突破了重重包围，准备看看如何接下来的处理；
			if (r == 0) { // 如果成功获取到读锁资源前，发现之前还没有任何线程持有读锁
				firstReader = current; // 则给firstReader对象赋值为第一个获取读锁的线程对象
				firstReaderHoldCount = 1; // 并且firstReaderHoldCount第一个线程持有读锁次数初始化次数为1
			} else if (firstReader == current) { // 能执行这个判断，说明读锁计数值肯定不为零，当首次获取读锁的线程正好是当前线程的话
				firstReaderHoldCount++; // 那么firstReaderHoldCount又加1，这里又可以认为重入锁的概念，但是这里重入的是读锁
			} else { // 如果执行到这里，说明当前持有读锁的线程不是当前线程
				HoldCounter rh = cachedHoldCounter; // 获取最近的一个成功获取读锁的线程的计数对象
				if (rh == null || rh.tid != getThreadId(current)) // 如果rh为空或者rh的线程id不是当前线程的话，
					cachedHoldCounter = rh = readHolds.get(); // 那么则将readHolds的计数对象取出来赋值给cachedHoldCounter
					// 意思就是说，readHolds里面有最近一次获取读锁的线程的一些简单的计数信息
				else if (rh.count == 0) // 当最近一个的那个计数对象count=0，则说明HoldCounter还刚刚被创立出来
					readHolds.set(rh); // 那么将rh这个对象直接赋值到readHolds中去
				rh.count++; // 并且次数累加一次
			}
			return 1; // 返回1说明当前已经成功的获取到了读锁，并且也成功的修改了state这么一个和锁资源密切相关的字段
		}
		
		// 执行到此，有3种情况：
		// 1、当读锁需要阻塞处理的话，则会执行到此；
		// 2、当读锁不需要阻塞处理，但是读锁的计数值超过了最大限制值MAX_COUNT=65535，那么也会执行到此；
		// 3、当读锁不需要阻塞处理，读锁计数值也没有超过最大限制值，但是通过CAS尝试占有读锁资源时失败了，也会执行到此
		// 总之一句话，没有顺利获取到读锁资源的线程，都会执行到这里来；
		return fullTryAcquireShared(current); // 获取读锁失败，则回炉重造通过自旋方式重试
	}

2、通过readLock.lock()最终调用的是Sync的tryAcquireShared尝试获取锁方式，从而可以得出几个结论：
	• 已持有写锁的线程，其他线程是不能持有读锁的；
	• 已持有写锁的线程可以再持有读锁，这里我们称之为锁降级；
	• 已持有读锁的线程可以再持有读锁，这和ReentrantLock的重入锁概念是一致的；
	• 已持有读锁的线程，其他线程也可以持有读锁的；

3、至于回炉重造的重试机制和tryAcquireShared操作方式以及代码非常类似，这里就不再详讲了；
```





### 3.7、tryReleaseShared(int)

``` 
1、源码：
	// ReentrantReadWriteLock 的静态内部类 Sync 的 tryReleaseShared 方法，读锁释放锁的核心方法
	protected final boolean tryReleaseShared(int unused) {
		Thread current = Thread.currentThread(); // 获取当前线程对象
		if (firstReader == current) { // 如果首次获取读锁的线程为当前线程的话
			// assert firstReaderHoldCount > 0;
			if (firstReaderHoldCount == 1) // 如果此刻firstReaderHoldCount次数正好为1的话，说明该线程的读锁没有重入
				firstReader = null; // 则直接将首次获取读锁的线程置为空即可
			else
				firstReaderHoldCount--; // 若firstReaderHoldCount不为1，则肯定是读锁重入了，则需要自减1操作；
		} else {
			// 执行到此，说明当前要释放读锁的线程不是那个首次获取到读锁的线程
			HoldCounter rh = cachedHoldCounter;
			if (rh == null || rh.tid != getThreadId(current)) // 获取最近的那个线程对象，如果不是当前线程的话
				rh = readHolds.get(); // 那么则通过readHolds获取最近的那个计数对象
			int count = rh.count; // 取出count值
			if (count <= 1) { // 若小于等于1，那么自减1就没了，所以减都没减了，直接移除掉，简单干脆
				readHolds.remove(); // 直接移除
				if (count <= 0) // 
					throw unmatchedUnlockException();
			}
			--rh.count; // 如果大于1的话，则还有的减，那么就自减1操作
		}
		for (;;) { // 自旋的死循环操作方式
			int c = getState(); // 获取最新的锁资源值
			int nextc = c - SHARED_UNIT; // 通过计算高位减1处理
			if (compareAndSetState(c, nextc)) // 通过尝试CAS正好设置成功的话，那么则返回nextc与0的比较
				// Releasing the read lock has no effect on readers,
				// but it may allow waiting writers to proceed if
				// both read and write locks are now free.
				return nextc == 0; // 不管如何，只要CAS成功，则表明读锁次数值已经被降低释放了一次了
				
			// 执行到此，说明由于并发的原因导致CAS失败，所以需要继续循环再次操作释放读锁次数操作
		}
	}

2、该方法主要讲解了读锁如何进行释放资源，如果首次释放失败的话，则会通过自旋的方式继续尝试释放资源，直到成功为止；
```







## 四、总结
``` 
1、这里有许多其它更底层的没有分析，因为都是AQS内部的基类方法了，而这些基类方法都在之前介绍过了，如果大家不记得的就去翻前面的篇章( 原理剖析（第 005 篇）AQS工作原理分析 )；

2、经过上面的一系列分析之后，在这里我再来总结一下ReentrantReadWriteLock的流程的一些特性；
	// ReentrantReadWriteLock.WriteLock.lock()特性：
	• 已持有读锁的线程不能再持有写锁；
	• 已持有写锁的线程可以再持有写锁，这和ReentrantLock的重入锁概念是一致的；
	• 已持有读锁的线程，其他线程是不能持有写锁的；
	• 已持有写锁的线程，其他线程是不能持有写锁的；

	// ReentrantReadWriteLock.ReadLock.lock()特性：
	• 已持有写锁的线程，其他线程是不能持有读锁的；
	• 已持有写锁的线程可以再持有读锁，这里我们称之为锁降级；
	• 已持有读锁的线程可以再持有读锁，这和ReentrantLock的重入锁概念是一致的；
	• 已持有读锁的线程，其他线程也可以持有读锁的；
	
3、然而将上面的WriteLock\ReadLock特性进行合并为：
	• 已持有写锁的线程可以再持有写锁，这和ReentrantLock的重入锁概念是一致的；
	• 已持有写锁的线程，其他线程是不能持有读锁、写锁的；
	• 已持有写锁的线程可以再持有读锁，这里我们称之为锁降级；
	
	• 已持有读锁的线程可以再持有读锁，这和ReentrantLock的重入锁概念是一致的；
	• 已持有读锁的线程，其他线程也可以持有读锁的；	
	• 已持有读锁的线程，其他线程(同时也包括已持有读锁的线程)是不能持有写锁的；

4、排除可重入的特性，再精炼合并特性为：
	• 写锁会排斥读锁、写锁，但是读锁会阻塞写锁；
	• 写锁可以降级为读锁，但读锁不能升级为写锁；	
``` 



## 五、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!