# 原理剖析（第 010 篇）Netty之服务端启动工作原理分析(上)
-

## 一、大致介绍

``` 
1、Netty这个词，对于熟悉并发的童鞋一点都不陌生，它是一个异步事件驱动型的网络通信框架；
2、使用Netty不需要我们关注过多NIO的API操作，简简单单的使用即可，非常方便，开发门槛较低；
3、而且Netty也经历了各大著名框架的“摧残”，足以证明其性能高，稳定性高；
4、那么本章节就来和大家分享分析一下Netty的服务端启动流程，分析Netty的源码版本为：netty-netty-4.1.22.Final；
```


## 二、简单认识Netty

### 2.1 何为Netty？

``` 
1、是一个基于NIO的客户端、服务器端的网络通信框架；

2、是一个以提供异步的、事件驱动型的网络应用工具；

3、可以供我们快速开发高性能的、高可靠性的网络服务器与客户端；
```


### 2.2 为什么使用Netty？

``` 
1、开箱即用，简单操作，开发门槛低，API简单，只需关注业务实现即可，不用关心如何编写NIO；

2、自带多种协议栈且预置多种编解码功能，且定制化能力强；

3、综合性能高，已历经各大著名框架(RPC框架、消息中间件)等广泛验证，健壮性非常强大；

4、相对于JDK的NIO来说，netty在底层做了很多优化，将reactor线程的并发处理提到了极致；

5、社区相对较活跃，遇到问题可以随时提问沟通并修复；
```




### 2.3 大致阐述启动流程

``` 
1、创建两个线程管理组，一个是bossGroup，一个是workerGroup，每个Group下都有一个线程组children[i]来执行任务；

2、bossGroup专门用来揽客的，就是接收客户端的请求链接，而workerGroup专门用来干事的，bossGroup揽客完了就交给workerGroup去干活了；

3、通过bind轻松的一句代码绑定注册，其实里面一点都不简单，一堆堆的操作；

4、创建NioServerSocketChannel，并且将此注册到bossGroup的子线程中的多路复用器上；

5、最后一步就是将NioServerSocketChannel绑定到指定ip、port即可，由此完成服务端的整个启动过程；
```




### 2.4 Netty服务端启动Demo

``` 
/**
 * Netty服务端启动代码。
 *
 * @author hmilyylimh
 *
 * @version 0.0.1
 *
 * @date 2018/3/25
 *
 */
public class NettyServer {

    public static final int TCP_PORT = 20000;

    private final int port;

    public NettyServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = null;
        EventLoopGroup workerGroup = null;
        try {
            // Server 端引导类
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            // Boss 线程管理组
            bossGroup = new NioEventLoopGroup(1);

            // Worker 线程管理组
            workerGroup = new NioEventLoopGroup();

            // 将 Boss、Worker 设置到 ServerBootstrap 服务端引导类中
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 指定通道类型为NioServerSocketChannel，一种异步模式，OIO阻塞模式为OioServerSocketChannel
                    .localAddress("localhost", port)//设置InetSocketAddress让服务器监听某个端口已等待客户端连接。
                    .childHandler(new ChannelInitializer<Channel>() {//设置childHandler执行所有的连接请求
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new PacketHeadDecoder());
                            ch.pipeline().addLast(new PacketBodyDecoder());

                            ch.pipeline().addLast(new PacketHeadEncoder());
                            ch.pipeline().addLast(new PacketBodyEncoder());

                            ch.pipeline().addLast(new PacketHandler());
                        }
                    });
            // 最后绑定服务器等待直到绑定完成，调用sync()方法会阻塞直到服务器完成绑定,然后服务器等待通道关闭，因为使用sync()，所以关闭操作也会被阻塞。
            ChannelFuture channelFuture = serverBootstrap.bind().sync();
            System.out.println("Server started，port：" + channelFuture.channel().localAddress());
            channelFuture.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new NettyServer(TCP_PORT).start();
    }
}
```





## 三、常用的类结构
![](http://img.blog.csdn.net/20180325190340333?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvWUxJTUhfSE1JTFk=/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)





## 四、源码分析Netty服务端启动

### 4.1、创建bossGroup对象

``` 
1、源码：
	// NettyServer.java, Boss 线程管理组, 上面NettyServer.java中的示例代码
	bossGroup = new NioEventLoopGroup(1);

	// NioEventLoopGroup.java
    /**
     * Create a new instance using the specified number of threads, {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
    }	
	
	// NioEventLoopGroup.java
    public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }	
	
	// NioEventLoopGroup.java
    public NioEventLoopGroup(
            int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }	
	
	// NioEventLoopGroup.java
    public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }	
	
	// MultithreadEventLoopGroup.java
    /**
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
        // DEFAULT_EVENT_LOOP_THREADS 默认为CPU核数的2倍
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
    }	
	
	// MultithreadEventExecutorGroup.java
    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }	
	
	// MultithreadEventExecutorGroup.java
    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) { // 小于或等于零都会直接抛异常，由此可见，要想使用netty，还得必须至少得有1个线程跑起来才能使用
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) { // 如果调用方不想自己定制线程池的话，那么则用netty自己默认的线程池
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        children = new EventExecutor[nThreads]; // 构建孩子结点数组，也就是构建NioEventLoopGroup持有的线程数组

        for (int i = 0; i < nThreads; i ++) { // 循环线程数，依次创建实例化线程封装的对象NioEventLoop
            boolean success = false;
            try {
                children[i] = newChild(executor, args); // 最终调用到了NioEventLoopGroup类中的newChild方法
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        // 实例化选择线程器，也就是说我们要想执行任务，对于nThreads个线程，我们得靠一个规则来如何选取哪个具体线程来执行任务；
        // 那么chooser就是来干这个事情的，它主要是帮我们选出需要执行任务的线程封装对象NioEventLoop
        chooser = chooserFactory.newChooser(children);

        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };

        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }	
	
2、主要讲述了NioEventLoopGroup对象的实例化过程，这仅仅只是讲了一半，因为还有一半是实例化children[i]子线程组；

3、每个NioEventLoopGroup都配备了一个默认的线程池executor对象，而且同时也配备了一个选择线程器chooser对象；

4、每个NioEventLoopGroup都一个子线程组children[i]，根据上层传入的参数来决定子线程数量，默认数量为CPU核数的2倍；
```




### 4.2、实例化线程管理组的孩子结点children[i]

``` 
1、源码：
	// MultithreadEventExecutorGroup.java, 最终调用到了NioEventLoopGroup类中的newChild方法
	children[i] = newChild(executor, args);

	// NioEventLoopGroup.java
    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new NioEventLoop(this, executor, (SelectorProvider) args[0],
            ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2]);
    }

	// NioEventLoop.java
    NioEventLoop(NioEventLoopGroup parent, Executor executor, SelectorProvider selectorProvider,
                 SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler) {
        // 调用父类的构造方法
        // DEFAULT_MAX_PENDING_TASKS 任务队列初始化容量值，默认值为：Integer.MAX_VALUE
        // 若不想使用默认值的话，那么就得自己配置 io.netty.eventLoop.maxPendingTasks 属性值为自己想要的值
        super(parent, executor, false, DEFAULT_MAX_PENDING_TASKS, rejectedExecutionHandler);

        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        if (strategy == null) {
            throw new NullPointerException("selectStrategy");
        }

        // 这个对象在NioEventLoopGroup的构造函数中通过SelectorProvider.provider()获得，然后一路传参到此类
        provider = selectorProvider;

        // 通过调用JDK底层类库，为每个NioEventLoop配备一个多路复用器
        final SelectorTuple selectorTuple = openSelector();
        selector = selectorTuple.selector;
        unwrappedSelector = selectorTuple.unwrappedSelector;
        selectStrategy = strategy;
    }

	// SingleThreadEventLoop.java
    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor,
                                    boolean addTaskWakesUp, int maxPendingTasks,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        // 调用父类的构造方法
        super(parent, executor, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);

        // 构造任务队列，最终会调用NioEventLoop的newTaskQueue(int maxPendingTasks)方法
        tailTasks = newTaskQueue(maxPendingTasks);
    }

	// SingleThreadEventExecutor.java
    /**
     * Create a new instance
     *
     * @param parent            the {@link EventExecutorGroup} which is the parent of this instance and belongs to it
     * @param executor          the {@link Executor} which will be used for executing
     * @param addTaskWakesUp    {@code true} if and only if invocation of {@link #addTask(Runnable)} will wake up the
     *                          executor thread
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                        boolean addTaskWakesUp, int maxPendingTasks,
                                        RejectedExecutionHandler rejectedHandler) {
        // 调用父类的构造方法
        super(parent);
        this.addTaskWakesUp = addTaskWakesUp; // 添加任务时是否需要唤醒多路复用器的阻塞状态
        this.maxPendingTasks = Math.max(16, maxPendingTasks);
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
        taskQueue = newTaskQueue(this.maxPendingTasks);
        rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
    }

	// AbstractScheduledEventExecutor.java
    protected AbstractScheduledEventExecutor(EventExecutorGroup parent) {
        // 调用父类的构造方法
        super(parent);
    }

	// AbstractEventExecutor.java
    protected AbstractEventExecutor(EventExecutorGroup parent) {
        this.parent = parent;
    }



2、该流程主要实例化线程管理组的孩子结点children[i]，孩子结点的类型为NioEventLoop类型；

3、仔细一看，netty的开发者对命名也很讲究，线程管理组的类名为NioEventLoopGroup，线程管理组的子线程类名为NioEventLoop，
   有没有发现有什么不一样的地方？其实就是差了个Group几个字母，线程管理组自然以Group结尾，不是组的就自然没有Group字母；
   
4、每个NioEventLoop都持有组的线程池executor对象，方便添加task到任务队列中；

5、每个NioEventLoop都有一个selector多路复用器，而那些Channel就是注册到这个玩意上面的；

6、每个NioEventLoop都有一个任务队列，而且这个队列的初始化容器大小为1024；
```





### 4.3、如何构建任务队列

``` 
1、源码：
	// SingleThreadEventLoop.java, 构造任务队列，最终会调用NioEventLoop的newTaskQueue(int maxPendingTasks)方法
	tailTasks = newTaskQueue(maxPendingTasks);

	// NioEventLoop.java
    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        // This event loop never calls takeTask()
        // 由于默认是没有配置io.netty.eventLoop.maxPendingTasks属性值的，所以maxPendingTasks默认值为Integer.MAX_VALUE；
        // 那么最后配备的任务队列的大小也就自然使用无参构造队列方法
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                                                    : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

	// PlatformDependent.java
    /**
     * Create a new {@link Queue} which is safe to use for multiple producers (different threads) and a single
     * consumer (one thread!).
     * @return A MPSC queue which may be unbounded.
     */
    public static <T> Queue<T> newMpscQueue() {
        return Mpsc.newMpscQueue();
    }

	// Mpsc.java
	static <T> Queue<T> newMpscQueue() {
		// 默认值 MPSC_CHUNK_SIZE =  1024;
		return USE_MPSC_CHUNKED_ARRAY_QUEUE ? new MpscUnboundedArrayQueue<T>(MPSC_CHUNK_SIZE)
											: new MpscUnboundedAtomicArrayQueue<T>(MPSC_CHUNK_SIZE);
	}

2、这里主要看看NioEventLoop是如何构建任务队列的，而且还构建了一个给定初始化容量值大小的队列；
```





### 4.4、如何获得多路复用器

``` 
1、源码：
	// NioEventLoop.java, 通过调用JDK底层类库，为每个NioEventLoop配备一个多路复用器
	final SelectorTuple selectorTuple = openSelector();
	selector = selectorTuple.selector;
	unwrappedSelector = selectorTuple.unwrappedSelector;
	selectStrategy = strategy;

	// NioEventLoop.java
    private SelectorTuple openSelector() {
        final Selector unwrappedSelector;
        try {
            // 通过 provider 调用底层获取一个多路复用器对象
            unwrappedSelector = provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }

        // DISABLE_KEYSET_OPTIMIZATION: 是否优化选择器key集合，默认为不优化
        if (DISABLE_KEYSET_OPTIMIZATION) {
            return new SelectorTuple(unwrappedSelector);
        }

        // 执行到此，说明需要优化选择器集合，首先创建一个选择器集合
        final SelectedSelectionKeySet selectedKeySet = new SelectedSelectionKeySet();

        // 然后通过反射找到SelectorImpl对象
        Object maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    // 通过反射获取SelectorImpl实现类对象
                    return Class.forName(
                            "sun.nio.ch.SelectorImpl",
                            false,
                            PlatformDependent.getSystemClassLoader());
                } catch (Throwable cause) {
                    return cause;
                }
            }
        });

        if (!(maybeSelectorImplClass instanceof Class) ||
                // ensure the current selector implementation is what we can instrument.
                !((Class<?>) maybeSelectorImplClass).isAssignableFrom(unwrappedSelector.getClass())) {
            if (maybeSelectorImplClass instanceof Throwable) {
                Throwable t = (Throwable) maybeSelectorImplClass;
                logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, t);
            }
            return new SelectorTuple(unwrappedSelector);
        }

        final Class<?> selectorImplClass = (Class<?>) maybeSelectorImplClass;

        // 以下run方法的主要目的就是将我们自己创建的selectedKeySet选择器集合通过反射替换底层自带的选择器集合
        Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    Field selectedKeysField = selectorImplClass.getDeclaredField("selectedKeys");
                    Field publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys");

                    Throwable cause = ReflectionUtil.trySetAccessible(selectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }
                    cause = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }

                    selectedKeysField.set(unwrappedSelector, selectedKeySet);
                    publicSelectedKeysField.set(unwrappedSelector, selectedKeySet);
                    return null;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                }
            }
        });

        if (maybeException instanceof Exception) {
            selectedKeys = null;
            Exception e = (Exception) maybeException;
            logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, e);
            return new SelectorTuple(unwrappedSelector);
        }

        // 反射执行完后，则将创建的selectedKeySet赋值为当成员变量
        selectedKeys = selectedKeySet;
        logger.trace("instrumented a special java.util.Set into: {}", unwrappedSelector);
        return new SelectorTuple(unwrappedSelector,
                                 new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet));
    }

2、其实说获得多路复用器，倒不如说多路复用器从何而来，是通过provider调用provider.openSelector()方法而获得的；

3、而这个provider所产生的地方其内部是一个静态变量，细心的童鞋会发现SelectorProvider.provider()这个里面还真有一个静态provider；

4、而这里给用户做了一个选择是否需要优化选择器，如果需要优化则用自己创建的选择器通过反射塞到底层的多路复用器对象中；
```





### 4.5、线程选择器

``` 
1、源码：
	// MultithreadEventExecutorGroup.java
	// 实例化选择线程器，也就是说我们要想执行任务，对于nThreads个线程，我们得靠一个规则来如何选取哪个具体线程来执行任务；
	// 那么chooser就是来干这个事情的，它主要是帮我们选出需要执行任务的线程封装对象NioEventLoop
	chooser = chooserFactory.newChooser(children);	

	// DefaultEventExecutorChooserFactory.java
    @SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            return new GenericEventExecutorChooser(executors);
        }
    }

	// PowerOfTwoEventExecutorChooser.java
    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

	// GenericEventExecutorChooser.java
    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }

2、记得在前面说过，在实例化线程组Group的时候，会实例化一个线程选择器，而这个选择器的实现方式也正是由通过线程数量来决定的；

3、PowerOfTwoEventExecutorChooser与GenericEventExecutorChooser的主要区别就是，当线程个数为2的n次方的话，那么则用PowerOfTwoEventExecutorChooser实例化的选择器；
   
4、因为EventExecutorChooser的next()方法，一个是与操作，一个是求余操作，而与操作的效率稍微高些，所以在选择线程这个细小的差别，netty的开发人员也真实一丝不苟的处理；
```





### 4.6、未完待续...

``` 
由于篇幅过长难以发布，所以接下来的请看【原理剖析（第 011 篇）Netty之服务端启动工作原理分析(下)】
```
详见 [原理剖析（第 011 篇）Netty之服务端启动工作原理分析(下)](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/flow-analysis/NettyStart_02.md)






## 五、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!