# 原理剖析（第 005 篇）AQS工作原理分析
-

## 一、大致介绍

``` 
1、前面章节讲解了一下CAS，简单讲就是cmpxchg+lock的原子操作；
2、而在谈到并发操作里面，我们不得不谈到AQS，JDK的源码里面好多并发的类都是通过Sync的内部类继承AQS而实现出五花八门的功能；
3、本章节就和大家分享分析一下AQS的工作原理；　
```


## 二、简单认识AQS

### 2.1 何为AQS？

``` 
1、AQS是一个抽象类，类名为AbstractQueuedSynchronizer，抽象的都是一些公用的方法属性，其自身是没有实现任何同步接口的；

2、AQS定义了同步器中获取锁和释放锁，目的来让自定义同步器组件来使用或重写；

3、纵观AQS的子类，绝大多数都是一个叫Sync的静态内部类来继承AQS类，通过重写AQS中的一些方法来实现自定义同步器；

4、AQS定义了两种资源共享方式：EXCLUSIVE( 独占式：每次仅有一个Thread能执行 )、SHARED( 共享式：多个线程可同时执行 )；

5、AQS维护了一个FIFO的CLH链表队列，且该队列不支持基于优先级的同步策略；
```


### 2.2 AQS的state关键词

``` 
1、private volatile int state：维护了一个volatile的int类型的state字段，该字段是实现AQS的核心关键词； 

2、通过getState、setState、compareAndSetState方法类获取、设置更新state值；

3、该字段在不同的并发类中起着不同的纽带作用，下面会接着讲到state字段的一些应用场景；
```


### 2.3 Node的waitStatus关键词

``` 
1、正常默认的状态值为0；

2、对于释放操作的时候，前一个结点有唤醒后一个结点的任务；

3、当前结点的前置结点waitStatus > 0，则结点处于CANCELLED状态，应该需要踢出队列；

4、当前结点的前置结点waitStatus = 0，则需要将前置结点改为SIGNAL状态；
```


### 2.4 CLH队列

``` 
1、队列模型：
      +------+  prev +------+  prev +------+
      |      | <---- |      | <---- |      |  
 head | Node |  next | Node |  next | Node |  tail
      |      | ----> |      | ----> |      |  
      +------+       +------+       +------+

2、链表结构，在头尾结点中，需要特别指出的是头结点是一个空对象结点，无任何意义，即傀儡结点；
	  
3、每一个Node结点都维护了一个指向前驱的指针和指向后驱的指针，结点与结点之间相互关联构成链表；

4、入队在尾，出队在头，出队后需要激活该出队结点的后继结点，若后继结点为空或后继结点waitStatus>0，则从队尾向前遍历取waitStatus<0的触发阻塞唤醒；
```


### 2.5 state在AQS简单应用举例

``` 
1、CountDownLatch，简单大致意思为：A组线程等待另外B组线程，B组线程执行完了，A组线程才可以执行；
   state初始化假设为N，后续每countDown()一次，state会CAS减1。
   等到所有子线程都执行完后(即state=0)，会unpark()主调用线程，然后主调用线程就会从await()函数返回，继续后余动作。

2、ReentrantLock，简单大致意思为：独占式锁的类；
   state初始化为0，表示未锁定状态，然后每lock()时调用tryAcquire()使state加1，
   其他线程再tryAcquire()时就会失败，直到A线程unlock()到state=0（即释放锁）为止，其它线程才有机会获取该锁；

3、Semaphore，简单大致意思为：A、B、C、D线程同时争抢资源，目前卡槽大小为2，若A、B正在执行且未执行完，那么C、D线程在门外等着，一旦A、B有1个执行完了，那么C、D就会竞争看谁先执行；
   state初始值假设为N，后续每tryAcquire()一次，state会CAS减1，当state为0时其它线程处于等待状态，
   直到state>0且<N后，进程又可以获取到锁进行各自操作了；
```


### 2.6 常用重要的方法
``` 
1、protected boolean isHeldExclusively()
   // 需要被子类实现的方法，调用该方法的线程是否持有独占锁，一般用到了condition的时候才需要实现此方法

2、protected boolean tryAcquire(int arg)
   // 需要被子类实现的方法，独占方式尝试获取锁，获取锁成功后返回true，获取锁失败后返回false

3、protected boolean tryRelease(int arg)  
   // 需要被子类实现的方法，独占方式尝试释放锁，释放锁成功后返回true，释放锁失败后返回false
   
4、protected int tryAcquireShared(int arg)  
   // 需要被子类实现的方法，共享方式尝试获取锁，获取锁成功后返回正数1，获取锁失败后返回负数-1
   
5、protected boolean tryReleaseShared(int arg)   
   // 需要被子类实现的方法，共享方式尝试释放锁，释放锁成功后返回正数1，释放锁失败后返回负数-1
   
6、final boolean acquireQueued(final Node node, int arg)
   // 对于进入队尾的结点，检测自己可以休息了，如果可以修改则进入SIGNAL状态且进入park()阻塞状态

7、private Node addWaiter(Node mode)
   // 添加结点到链表队尾

8、private Node enq(final Node node)
   // 如果addWaiter尝试添加队尾失败，则再次调用enq此方法自旋将结点加入队尾

9、private static boolean shouldParkAfterFailedAcquire(Node pred, Node node)
   // 检测结点状态，如果可以休息的话则设置waitStatus=SIGNAL并调用LockSupport.park休息；

10、private void unparkSuccessor(Node node)   
   // 释放锁时，该方法需要负责唤醒后继节点
```


### 2.7 设计与实现伪代码
``` 
1、获取独占锁：
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && 
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

	acquire{
		如果尝试获取独占锁失败的话( 尝试获取独占锁的各种方式由AQS的子类实现 )，
		那么就新增独占锁结点通过自旋操作加入到队列中，并且根据结点中的waitStatus来决定是否调用LockSupport.park进行休息
	}
	
	
2、释放独占锁：
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

3、获取共享锁：
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }
	
	acquireShared{
		如果尝试获取共享锁失败的话( 尝试获取共享锁的各种方式由AQS的子类实现 )，
		那么新增共享锁结点通过自旋操作加入到队尾中，并且根据结点中的waitStatus来决定是否调用LockSupport.park进行休息
	}

4、释放共享锁：
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


## 三、举例ReentrantLock

### 3.1、ReentrantLock
``` 
1、在分析AQS源码前，我们需要依赖一个载体来说，毕竟AQS的一些方法都是空方法且抛异常的，所以单讲AQS不太生动形象；

2、因此我们决定采用ReentrantLock来讲解，其他都大致差不多，因为了解了一个，其他都可以依葫芦画瓢秒懂；
```


### 3.2、ReentrantLock生活细节化理解

``` 
比如我们天天在外面吃快餐，我就以吃快餐为例生活化阐述该ReentrantLock原理：

1、场景：餐厅只有一个排队的走廊，只有一个打饭菜的师傅；

2、开饭时间点，大家都争先恐后的去吃饭，因此排上了队，挨个挨个排队打饭菜，任何一个人只要排到了打饭师傅的前面，都可以打到饭菜；

3、但是有时候队很长，有些人之间的关系是家属关系，如果后来的人看到自己家属正在打饭菜，这个时候可以不用排队直接跑到前面打饭菜；

4、总之大家都挨个挨个排队打饭，有家属关系的直接跑到前面打饭菜；

5、到此打止，1、2、3、4可以认为是一种公平方式的独占锁，3可以理解为重入锁；

5、但是呢，还有那么些紧急赶时间的人，而且又跟排队的人没半点瓜葛，来餐厅时刚好看到师傅刚刚打完一个人的饭菜，于是插入去打饭菜敢时间；

6、如果敢时间人的来的时候发现师傅还在打饭菜，那么就只得乖乖的排队等候打饭菜咯；

7、到此打止，1、2、5、6可以认为是一种非公平方式的独占锁；
```



## 四、源码分析ReentrantLock

### 4.1、ReentrantLock构造器

``` 
1、构造器源码：
    /**
     * Creates an instance of {@code ReentrantLock}.
     * This is equivalent to using {@code ReentrantLock(false)}.
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * Creates an instance of {@code ReentrantLock} with the
     * given fairness policy.
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }
	
2、默认构造方法为非公平锁，带参构造方法还可通过传入变量还决定调用方是使用公平锁还是非公平锁；	
```



### 4.2、Sync同步器

``` 
1、AQS --> Sync ---> FairSync // 公平锁
				  |
				  |> NonfairSync // 非公平锁
				  
2、ReentrantLock内的同步器都是通过Sync抽象接口来操作调用关系的，细看会发现基本上都是通过sync.xxx之类的这种调用方式的；
```



### 4.3、lock()

``` 
1、源码：
    public void lock() {
        sync.lock();
    }
	
	// FairSync 公平锁调用方式
	final void lock() {
		acquire(1); // 尝试获取独占锁
	}	
	
	// NonfairSync 非公平锁调用方式
	final void lock() {
		if (compareAndSetState(0, 1)) // 首先判断state资源是否为0，如果恰巧为0则表明目前没有线程占用锁，则利用CAS占有锁
			setExclusiveOwnerThread(Thread.currentThread()); // 当独占锁之后则将设置exclusiveOwnerThread为当前线程
		else
			acquire(1); // 若CAS占用锁失败的话，则再尝试获取独占锁
	}
	
2、这里的区别就是非公平锁在调用lock时首先检测了是否通过CAS获取锁，发现锁一旦空着的话，则抢先一步占为己有，
   不管有没有阻塞队列，只要当前线程来的时候发现state资源没被占用那么当前线程就抢先一步试一下CAS，CAS失败了它才去排队；
```


### 4.4、acquire(int)

``` 
1、源码：
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && // 尝试获取锁资源，若获取到资源的话则线程直接返回，此方法由AQS的具体子类实现
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) // 否则获取资源失败的话，那么就进入等待队列
            selfInterrupt();
    }
	
2、该方法是独占模式下线程获取state共享资源的入口，如果获取到资源的话就返回，否则创建独占模式结点加入阻塞队列，直到获取到共享资源；

3、而且这里需要加上自我中断判断，主要是因为线程在等待过程中被中断的话，它是不响应的，那么就只有等到线程获取到资源后通过自我判断将这个判断后续补上；

4、独占模式的该方法，正常情况下只要没有获取到锁，该方法一直处于阻塞状态，获取到了则跳出该方法区；
```



### 4.5、tryAcquire(int)

``` 
1、公平锁tryAcquire源码：
	// FairSync 公平锁的 tryAcquire 方法
	protected final boolean tryAcquire(int acquires) {
		final Thread current = Thread.currentThread();
		int c = getState(); // 获取锁资源的最新内存值
		if (c == 0) { // 当state=0，说明锁资源目前还没有被任何线程被占用
			if (!hasQueuedPredecessors() && // 检查线程是否有阻塞队列
				compareAndSetState(0, acquires)) { // 如果没有阻塞队列，则通过CAS操作获取锁资源
				setExclusiveOwnerThread(current); // 没有阻塞队列，且CAS又成功获取锁资源，则设置独占线程对象为当前线程
				return true; // 返回标志，告诉上层该线程已经获取到了锁资源
			}
		}
		// 执行到此，锁资源值不为0，说明已经有线程正在占用这锁资源
		else if (current == getExclusiveOwnerThread()) { // 既然锁已经被占用，则看看占用锁的线程是不是当前线程
			int nextc = c + acquires; // 如果占用的锁的线程是当前线程的话，则为重入锁概念，状态值做加1操作
			// int类型值小于0，是因为该int类型的state状态值溢出了，溢出了的话那得说明这个锁有多难获取啊，可能出问题了
			if (nextc < 0)
				throw new Error("Maximum lock count exceeded");
			setState(nextc);
			return true; // 返回成功标志，告诉上层该线程已经获取到了锁资源
		}
		return false; // 返回失败标志，告诉上层该线程没有获取到锁资源
	}

2、非公平锁tryAcquire源码：
	// NonfairSync 非公平锁的 tryAcquire 方法
	protected final boolean tryAcquire(int acquires) {
		return nonfairTryAcquire(acquires); // 调用父类的非公平获取锁资源方法
	}	

	// NonfairSync 非公平锁父类 Sync 类的 nonfairTryAcquire 方法	
	final boolean nonfairTryAcquire(int acquires) {
		final Thread current = Thread.currentThread();
		int c = getState(); // 获取锁资源的最新内存值
		if (c == 0) { // 当state=0，说明锁资源目前还没有被任何线程被占用
			if (compareAndSetState(0, acquires)) { // 先不管三七二十一，先尝试通过CAS操作获取锁资源
				setExclusiveOwnerThread(current); // CAS一旦成功获取锁资源，则设置独占线程对象为当前线程
				return true;// 返回成功标志，告诉上层该线程已经获取到了锁资源
			}
		}
		// 执行到此，锁资源值不为0，说明已经有线程正在占用这锁资源
		else if (current == getExclusiveOwnerThread()) { // 既然锁已经被占用，则看看占用锁的线程是不是当前线程
			int nextc = c + acquires; // 如果占用的锁的线程是当前线程的话，则为重入锁概念，状态值做加1操作
			// int类型值小于0，是因为该int类型的state状态值溢出了，溢出了的话那得说明这个锁有多难获取啊，可能出问题了
			if (nextc < 0) // overflow
				throw new Error("Maximum lock count exceeded");
			setState(nextc); // 
			return true; // 返回成功标志，告诉上层该线程已经获取到了锁资源
		}
		return false; // 返回失败标志，告诉上层该线程没有获取到锁资源
	}	

3、tryAcquire方法是AQS的子类实现的，也就是ReentrantLock的两个静态内部类实现的，目的就是通过CAS尝试获取锁资源，
   获取锁资源成功则返回true，获取锁资源失败则返回false； 
```


### 4.6、addWaiter(Node)

``` 
1、源码：
    /**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     */
    private Node addWaiter(Node mode) {
		// 按照给定的mode模式创建新的结点，模式有两种：Node.EXCLUSIVE独占模式、Node.SHARED共享模式；
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail; // 将先队尾结点赋值给临时变量
        if (pred != null) { // 如果pred不为空，说明该队列已经有结点了
            node.prev = pred;
            if (compareAndSetTail(pred, node)) { // 通过CAS尝试将node结点设置为队尾结点
                pred.next = node;
                return node;
            }
        }
		// 执行到此，说明队尾没有元素，则进入自旋首先设置头结点，然后将此新建结点添加到队尾
        enq(node); // 进入自旋添加node结点
        return node;
    }
	
2、	addWaiter通过传入不同的模式来创建新的结点尝试加入到队列尾部，如果由于并发导致添加结点到队尾失败的话那么就进入自旋将结点加入队尾；
```


### 4.7、enq(Node)

``` 
1、源码：
    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        for (;;) { // 自旋的死循环操作方式
            Node t = tail;
			// 因为是自旋方式，首次链表队列tail肯定为空，但是后续链表有数据后就不会为空了
            if (t == null) { // Must initialize
                if (compareAndSetHead(new Node())) // 队列为空时，则创建一个空对象结点作为头结点，无意思，可认为傀儡结点
                    tail = head; // 空队列的话，头尾都指向同一个对象
            } else {
				// 进入 else 方法里面，说明链表队列已经有结点了
                node.prev = t;
				// 因为存在并发操作，通过CAS尝试将新加入的node结点设置为队尾结点
                if (compareAndSetTail(t, node)) { 
					// 如果node设置队尾结点成功，则将之前的旧的对象尾结点t的后继结点指向node,node的前驱结点也设置为t
                    t.next = node;
                    return t;
                }
            }
			
			// 如果执行到这里，说明上述两个CAS操作任何一个失败的话，该方法是不会放弃的，因为是自旋操作，再次循环继续入队
        }
    }

2、enq通过自旋这种死循环的操作方式，来确保结点正确的添加到队列尾部，通过CAS操作如果头部为空则添加傀儡空结点，然后在循环添加队尾结点；
```


### 4.8、compareAndSetHead/compareAndSetTail

``` 
1、源码：
    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }
	
    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }	

2、CAS操作，设置头结点、尾结点；
```


### 4.9、acquireQueued(Node, int)

``` 
1、源码：
    /**
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) { // 自旋的死循环操作方式
                final Node p = node.predecessor(); // 如果新建结点的前驱结点是头结点
				// 如果前驱结点为头结点，那么该结点则是老二，仅次于老大，也希望尝试去获取一下锁，万一头结点恰巧刚刚释放呢？希望还是要有的，万一实现了呢。。。
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
					// 拿到锁资源后，则该node结点升级做头结点，且设置后继结点指针为空，便于GC回收
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) && // 根据前驱结点看看是否需要休息一会儿
                    parkAndCheckInterrupt()) // 阻塞操作，正常情况下，获取不到锁，代码就在该方法停止了，直到被唤醒
                    interrupted = true;
					
				// 如果执行到这里，说明尝试休息失败了，因为是自旋操作，所以还会再次循环继续操作判断
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

2、acquireQueued也是采用一个自旋的死循环操作方式，只有头结点才能尝试获取锁资源，其余的结点挨个挨个在那里等待修改，等待被唤醒，等待机会成为头结点；
   而新添加的node结点也自然逃不过如此命运，先看看是否头结点，然后再看看是否能休息；
```


### 4.10、shouldParkAfterFailedAcquire(Node, Node)

``` 
1、源码：
    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus; // 获取前驱结点的状态值
        if (ws == Node.SIGNAL) // 若前驱结点的状态为SIGNAL状态的话，那么该结点就不要想事了，直接返回true准备休息
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
			// 若前驱结点的状态为CANCELLED状态的话，那么就一直向前遍历，直到找到一个不为CANCELLED状态的结点
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
			 // 剩下的结点状态，则设置其为SIGNAL状态，然后返回false标志等外层循环再次判断
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

2、shouldParkAfterFailedAcquire主要是检测前驱结点状态，前驱结点为SIGNAL的话，则新结点可以安安心心休息了；
   如果前驱结点大于零，说明前驱结点处于CANCELLED状态，那么则以入参pred前驱为起点，一直往前找，直到找到最近一个正常等待状态的结点；
   如果前驱结点小于零，那么就将前驱结点设置为SIGNAL状态，然后返回false依赖acquireQueued的自旋再次判断是否需要进行休息；
```


### 4.11、parkAndCheckInterrupt()

``` 
1、源码：
    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this); // 阻塞等待
        return Thread.interrupted(); // 被唤醒后查看是否有被中断过否？
    }

2、parkAndCheckInterrupt首先调用park让线程进入等待状态，然后当park阻塞被唤醒后，再次检测是否曾经被中断过；
   而被唤醒有两种情况，一个是利用unpark唤醒，一个是利用interrupt唤醒；
```


### 4.12、unlock()

``` 
1、源码：
    public void unlock() {
        sync.release(1); // 
    }

2、unlock释放锁资源，一般都是在finally中被调用，防止当临界区因为任何异常时怕锁不被释放；
   而释放锁不像获取锁lock的实现多色多样，没有所谓公平或不公平，就是规规矩矩的释放资源而已；
```


### 4.13、release(int)

``` 
1、源码：
    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) { // 尝试释放锁资源，此方法由AQS的具体子类实现
            Node h = head;
            if (h != null && h.waitStatus != 0) // 从头结点开始，唤醒后继结点
                unparkSuccessor(h); // 踢出CANCELLED状态结点，然后唤醒后继结点
            return true;
        }
        return false;
    }

2、release尝试释放锁，并且有义务移除CANCELLED状态的结点，还有义务唤醒后继结点继续运行获取锁资源；
```


### 4.14、tryRelease(int)

``` 
1、源码：
	// NonfairSync 和 FairSync 的父类 Sync 类的 tryRelease 方法	
	protected final boolean tryRelease(int releases) {
		int c = getState() - releases; // 获取锁资源值并做减1操作
		if (Thread.currentThread() != getExclusiveOwnerThread()) // 查看当前线程是否和持有锁的线程是不是同一个线程
			// 正常情况下，需要释放的线程肯定是持有锁的线程，否则不就乱套了，肯定哪里出问题了，所以抛出异常
			throw new IllegalMonitorStateException(); 
		boolean free = false;
		if (c == 0) { // 若此时锁资源值做减法操作后正好是0，则所有锁资源已经释放干净，因此持有锁的变量也置为空
			free = true;
			setExclusiveOwnerThread(null);
		}
		setState(c); // 若此时做减法操作还没有归零，那么这种情况就是那种重入锁，需要重重释放后才行
		return free;
	}

2、tryRelease主要通过CAS操作对state锁资源进行减1操作；
```


### 4.15、unparkSuccessor(Node)

``` 
1、源码：
    /**
     * Wakes up node's successor, if one exists.
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
		// 该node一般都是传入head进来，也就是说，需要释放头结点，也就是当前结点需要释放锁操作，顺便唤醒后继结点
        int ws = node.waitStatus;
        if (ws < 0) // 若结点状态值小于0，则归零处理，通过CAS归零，允许失败，但是不管怎么着，仍然要往下走去唤醒后继结点
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */
        Node s = node.next; // 取出后继结点，这个时候一般都是Head后面的一个结点，所以一般都是老二
        if (s == null || s.waitStatus > 0) { // 若后继结点为空或者后继结点已经处于CANCELLED状态的话
            s = null;
			// 那么从队尾向前遍历，直到找到一个小于等于0的结点
			// 这里为什么要从队尾向前寻找？
			// * 因为在这个队列中，任何一个结点都有可能被中断，只是有可能，并不代表绝对的，但有一点是确定的，
			// * 被中断的结点会将结点的状态设置为CANCELLED状态，标识这个结点在将来的某个时刻会被踢出；
			// * 踢出队列的规则很简单，就是该结点的前驱结点不会指向它，而是会指向它的后面的一个非CANCELLED状态的结点；
			// * 而这个将被踢出的结点，它的next指针将会指向它自己；
			// * 所以设想一下，如果我们从head往后找，一旦发现这么一个处于CANCELLED状态的结点，那么for循环岂不是就是死循环了；
			// * 但是所有的这些结点当中，它们的prev前驱结点还是没有被谁动过，所以从tail结点向前遍历最稳妥

            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            LockSupport.unpark(s.thread); // 唤醒线程
    }

2、unparkSuccessor主要是踢出CANCELLED状态结点，然后唤醒后继结点；
   但是这个唤醒的后继结点为空的话，那么则从队尾一直向前循环查找小于等于零状态的结点并调用unpark唤醒；
```





## 五、总结
``` 
1、分析了这么多，感觉是不是有一种豁然开朗的感觉，原来大家传的神乎其神的AQS是不是没有想象中那么难以理解；

2、在这里我简要总结一下AQS的流程的一些特性：
	• 关键获取锁、释放锁操作由AQS子类实现：acquire-release、acquireShared-releaseShared；
	• 维护了一个FIFO链表结构的队列，通过自旋方式将新结点添加到队尾；
	• 添加结点时会从前驱结点向前遍历，跳过那些处于CANCELLED状态的结点；
	• 释放结点时会从队尾向前遍历，踢出CANCELLED状态的结点，然后唤醒后继结点；

3、其实当了解了AQS后，这里以ReentrantLock为载体分析了一下，那么再去分析CountDownLatch、Semaphore、ReentrantReadWriteLock等那些集成AQS而实现不同功能的模块就会顺利很多；
``` 



## 六、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!