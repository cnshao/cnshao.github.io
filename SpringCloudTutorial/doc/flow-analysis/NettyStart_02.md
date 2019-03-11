# 原理剖析（第 011 篇）Netty之服务端启动工作原理分析(下)
-

## 一、大致介绍

``` 
1、由于篇幅过长难以发布，所以本章节接着上一节来的，上一章节为【原理剖析（第 010 篇）Netty之服务端启动工作原理分析(上)】；
2、那么本章节就继续分析Netty的服务端启动，分析Netty的源码版本为：netty-netty-4.1.22.Final；
```


## 二、三、四章节请看上一章节

详见 [原理剖析（第 010 篇）Netty之服务端启动工作原理分析(上)](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/flow-analysis/NettyStart_01.md)




## 四、源码分析Netty服务端启动
上一章节，我们主要分析了一下线程管理组对象是如何被实例化的，并且还了解到了每个线程管理组都有一个子线程数组来处理任务；
那么接下来我们就直接从4.6开始分析了：




### 4.6、为serverBootstrap添加配置参数

``` 
1、源码：
	// NettyServer.java
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

2、主要为后序的通信设置了一些配置参数而已，指定构建的Channel为NioServerSocketChannel，说明需要启动的是服务端Netty；
   而后面的服务端Channel实例化，就是需要通过这个参数反射实例化得到；

3、同时还设置childHandler，这个childHandler也是有顺序的，服务端读数据时执行的顺序是PacketHeadDecoder、PacketBodyDecoder、PacketHandler；
   而服务端写数据时执行的顺序是PacketHandler、PacketBodyEncoder、PacketHeadEncoder；
   所以在书写方式大家千万别写错了，按照本示例代码的方式书写即可；
```





### 4.7、serverBootstrap调用bind绑定注册

``` 
1、源码：
	// NettyServer.java
	// 最后绑定服务器等待直到绑定完成，调用sync()方法会阻塞直到服务器完成绑定,然后服务器等待通道关闭，因为使用sync()，所以关闭操作也会被阻塞。
	ChannelFuture channelFuture = serverBootstrap.bind().sync();

2、这里其实没什么好看的，接下来我们就主要看看这个bind()方法主要干了些啥，就这么简简单单一句代码就把服务端给启动起来了，有点神气了；
```





### 4.8、bind()操作

``` 
1、源码：
	// AbstractBootstrap.java
    /**
     * Create a new {@link Channel} and bind it.
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress); // 创建一个Channel，并且绑定它
    }

	// AbstractBootstrap.java
    private ChannelFuture doBind(final SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister(); // 初始化和注册

        // 执行到此，服务端大概完成了以下几件事情：
        // 1、实例化NioServerSocketChannel，并为Channel配备了pipeline、config、unsafe对象；
        // 2、将多个handler添加至pipeline双向链表中，并且等待Channel注册成功后需要给每个handler触发添加或者移除事件；
        // 3、将NioServerSocketChannel注册到NioEventLoop的多路复用器上；

        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        // 既然NioServerSocketChannel的Channel绑定到了多路复用器上，那么接下来就是绑定地址，绑完地址就可以正式进行通信了
        if (regFuture.isDone()) {
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();

                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }

2、大致一看，原来doBind方法主要干了两件事情，initAndRegister与doBind0；

3、initAndRegister主要做的事情就是初始化服务端Channel，并且将服务端Channel注册到bossGroup子线程的多路复用器上；

4、doBind0则主要完成服务端启动的最后一步，绑定地址，绑定完后就可以正式进行通信了；
```





### 4.9、initAndRegister()初始化和注册

``` 
1、源码：
	// AbstractBootstrap.java
    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            // 反射调用clazz.getConstructor().newInstance()实例化类
            // 同时也实例化了Channel，如果是服务端的话则为NioServerSocketChannel实例化对象
            // 在实例化NioServerSocketChannel的构造方法中，也为每个Channel创建了一个管道属性对象DefaultChannelPipeline=pipeline对象
            // 在实例化NioServerSocketChannel的构造方法中，也为每个Channel创建了一个配置属性对象NioServerSocketChannelConfig=config对象
            // 在实例化NioServerSocketChannel的构造方法中，也为每个Channel创建了一个unsafe属性对象NioMessageUnsafe=unsafe对象
            channel = channelFactory.newChannel(); // 调用ReflectiveChannelFactory的newChannel方法

            // 初始化刚刚被实例化的channel
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly();
                // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        // config().group()=bossGroup或parentGroup，然后利用parentGroup去注册NioServerSocketChannel=channel
        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        return regFuture;
    }

2、逐行分析后会发现，首先通过反射实例化服务端channel对象，然后将服务端channel初始化一下；

3、然后调用bossGroup的注册方法，将服务端channel作为参数传入；

4、至此，方法名也表明该段代码的意图，实例化并初始化服务端Channel，然后注册到bossGroup子线程的多路复用器上；
```





### 4.10、init服务端Channel

``` 
1、源码：
	// ServerBootstrap.java
    @Override
    void init(Channel channel) throws Exception {
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                @SuppressWarnings("unchecked")
                AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
                channel.attr(key).set(e.getValue());
            }
        }

		// 服务端ServerSocketChannel的管道对象，Channel实例化的时候就被创建出来了
        ChannelPipeline p = channel.pipeline();

        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Entry<ChannelOption<?>, Object>[] currentChildOptions;
        final Entry<AttributeKey<?>, Object>[] currentChildAttrs;
        synchronized (childOptions) {
            currentChildOptions = childOptions.entrySet().toArray(newOptionArray(childOptions.size()));
        }
        synchronized (childAttrs) {
            currentChildAttrs = childAttrs.entrySet().toArray(newAttrArray(childAttrs.size()));
        }

        ChannelInitializer<Channel> tempHandler = new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }

                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("initAndRegister.init.initChannel-->ch.eventLoop().execute");
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        };

        // 这里我将addLast的参数剥离出来了，方便查看阅读
        p.addLast(tempHandler);
    }

	// DefaultChannelPipeline.java
    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }	
	
	// DefaultChannelPipeline.java
    @Override
    public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        if (handlers == null) {
            throw new NullPointerException("handlers");
        }

        for (ChannelHandler h: handlers) {
            if (h == null) {
                break;
            }
            addLast(executor, null, h);
        }

        return this;
    }	
	
	// DefaultChannelPipeline.java
    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        // 这里加了synchronized关键字，因此说addLast的新增动作都是线程安全的
        // 然后再细看一下其它的方法，只要涉及到的handler的增删改动作的方法，那些方法的代码块都是经过synchronized修饰了，保证操作过程中线程安全
        synchronized (this) {
            // 检查handler的一些基本信息，若不是被Sharable注解过的话，而且已经被添加到其他pipeline时则会抛出异常
            checkMultiplicity(handler);

            // 通过一系列参数的封装，最后封装成DefaultChannelHandlerContext对象
            newCtx = newContext(group, filterName(name, handler), handler);

            // 将newCtx添加到倒数第二的位置，即tail的前面一个位置
            // 这里的pipeline中的handler的构成方式是一个双向链表式的结构
            addLast0(newCtx);

            // If the registered is false it means that the channel was not registered on an eventloop yet.
            // In this case we add the context to the pipeline and add a task that will call
            // ChannelHandler.handlerAdded(...) once the channel is registered.
            // 该addLast方法可能会被其它各个地方调用，但是又为了保证handler的线程安全，则采用了synchronized来保证addLast的线程安全
            // 在Channel未注册到多路复用器之前，registered肯定为false，那么则把需要添加的handler封装成AbstractChannelHandlerContext对象，
            // 然后调用setAddPending方法，pengding意味着在将来的某个时刻调用，那到底在什么时刻被调用呢？
            // 英文解释中提到一旦Channel注册成功了的话则会被调用，所以Channel后续注册完毕，再调用ChannelHandler.handlerAdded
            if (!registered) {
                newCtx.setAddPending();

                // 将newCtx追加到PendingHandlerCallback单向链表的队尾，以便将来回调时用到
                callHandlerCallbackLater(newCtx, true);
                return this;
            }

            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                newCtx.setAddPending();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callHandlerAdded0(newCtx);
                    }
                });
                return this;
            }
        }

        // 如果能顺利执行到这里来的话，则表明Channel已经注册到了NioEventLoop的多路复用器上面了
        // 然后接下来的就是触发调用newCtx的ChannelHandler.handlerAdded方法
        callHandlerAdded0(newCtx);
        return this;
    }	
	
	// DefaultChannelPipeline.java
    private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev; // 将目前双向链表tail的前驱结点找出来命名为prev
        newCtx.prev = prev; // 将新的结点的前驱结点指向prev
        newCtx.next = tail; // 将新的结点的后驱结点指向tail
        prev.next = newCtx; // 将prev的后驱结点指向新的结点
        tail.prev = newCtx; // 将tail的前驱结点指向新的结点

        // 就这样，将新的结点通过一系列的指针指向，顺利的将新结点插到了tail的前面，
        // 也就是链表中倒数第2个结点的位置，原链表中倒数第2个结点变成倒数第3个结点
    }	
	
	// DefaultChannelPipeline.java
    private void callHandlerCallbackLater(AbstractChannelHandlerContext ctx, boolean added) {
        assert !registered;

        // 根据added布尔值封装成PendingHandlerAddedTask、PendingHandlerRemovedTask对象
        PendingHandlerCallback task = added ? new PendingHandlerAddedTask(ctx) : new PendingHandlerRemovedTask(ctx);
        PendingHandlerCallback pending = pendingHandlerCallbackHead;
        if (pending == null) { // 首次添加时则直接赋值然后返回
            pendingHandlerCallbackHead = task;
        } else {
            // 非首次赋值的话，那么通过while循环找到队尾，然后将队尾的next指向赋上task对象
            // Find the tail of the linked-list.
            while (pending.next != null) {
                pending = pending.next; // 不停的寻找链表中的下一个结点
            }
            // 当pending.next为空说明已经找到了队尾结点，然后将队尾的next指向赋上task对象
            pending.next = task;
        }
    }	
	
2、其实初始化服务端Channel也做了蛮多的事情，事情再多也只是p.addLast(tempHandler)这句代码干的事情多；

3、主要完成了服务端Channel中管道对象pipeline添加handler的操作，添加过程中主要有以下几点：
	• 添加的过程中是由synchronized关键字来保证线程安全的；
	• 将传入的handler数组依次循环封装成AbstractChannelHandlerContext对象添加到管道锁维护的handler链表中；
	• 当未注册成功时pipeline还维护了一个用后后序触发调用newCtx的单向链表对象pendingHandlerCallbackHead；
	• 当注册成功后，后序会迭代pendingHandlerCallbackHead对象依次执行所有任务的run方法；
	• 当注册成功后，还会触发调用这些newCtx的一些方法，主要是newCtx的ChannelHandler.handlerAdded方法；
	
4、讲到这里，initAndRegister总算讲了一半了，接下来我们就要看看被实例化的服务端channel是如何注册到多路复用器上的；
```





### 4.11、config().group().register(channel)

``` 
1、源码：
	// MultithreadEventLoopGroup.java
    @Override
    public ChannelFuture register(Channel channel) {
        // next()对象其实是NioEventLoopGroup内部中的children[]属性中的其中一个，通过一定规则挑选一个NioEventLoop
        // 那么也就是说我们最终调用的是NioEventLoop来实现注册channel
        return next().register(channel);

        // 从另外一个层面来讲，我们要想注册一个Channel，那么就可以直接调用NioEventLoopGroup父类中的register(Channel)即可注册Channel，
        // 并且会按照一定的规则顺序通过next()挑选一个NioEventLoop并将Channel绑定到它上面
        // 如果NioEventLoopGroup为bossGroup的话，那么该方法注册的肯定是NioServerSocketChannel对象
        // 如果NioEventLoopGroup为workerGroup的话，那么该方法注册的肯定是ServerSocketChannel对象
    }

	// SingleThreadEventLoop.java
    @Override
    public ChannelFuture register(Channel channel) {
        // 当前this对象是属于children[]属性中的其中一个
        // 将传入的Channel与当前对象this一起封装成DefaultChannelPromise对象
        // 然后再调用当前对象的register(ChannelPromise)注册方法
        return register(new DefaultChannelPromise(channel, this));
    }

	// SingleThreadEventLoop.java
    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        // 校验当前传参是否为空，原则上既然是不可能为空的，因为上一个步骤是通过new出来的一个对象
        ObjectUtil.checkNotNull(promise, "promise");
        // promise.channel()其实就是上面new DefaultChannelPromise(channel, this)通过封装后又取出这个channel对象
        // promise.channel().unsafe()而每个Channel都有一个unsafe对象，对于NioServerSocketChannel来说NioMessageUnsafe=unsafe
        // 当前this对象是属于children[]属性中的其中一个
        promise.channel().unsafe().register(this, promise);
        return promise;
    }	

	// AbstractUnsafe.java
	@Override
	public final void register(EventLoop eventLoop, final ChannelPromise promise) {
		// eventLoop对象是属于children[]属性中的其中一个
		// 而当前类又是Channel的一个抽象类AbstractChannel，也是NioServerSocketChannel的父类
		if (eventLoop == null) {
			throw new NullPointerException("eventLoop");
		}
		if (isRegistered()) {
			promise.setFailure(new IllegalStateException("registered to an event loop already"));
			return;
		}
		if (!isCompatible(eventLoop)) {
			promise.setFailure(
					new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
			return;
		}

		// 这里的 this.eventLoop 就是Children[i]中的一个，也就是具体执行任务的线程封装对象
		AbstractChannel.this.eventLoop = eventLoop;

		if (eventLoop.inEventLoop()) { // 如果对象eventLoop中的线程对象和当前线程比对是一样的话
			register0(promise); // 那么则直接调用注册方法register0
		} else {
			try {
				// 比对的结果如果不一样，十有八九都是该eventLoop的线程还未启动，
				// 因此利用eventLoop的execute将register0(promise)方法作为任务添加到任务队列中，并启动线程来执行任务
				eventLoop.execute(new Runnable() {
					@Override
					public void run() {
						register0(promise);
					}
				});
				// 而服务端Channel的注册，走的是该else分支，因为线程都还没创建，eventLoop.inEventLoop()肯定就是false结果
			} catch (Throwable t) {
				logger.warn(
						"Force-closing a channel whose registration task was not accepted by an event loop: {}",
						AbstractChannel.this, t);
				closeForcibly();
				closeFuture.setClosed();
				safeSetFailure(promise, t);
			}
		}
	}
	
	// SingleThreadEventExecutor.java
    /**
     * 向任务队列中添加任务task。
     *
     * @param task
     */
    @Override
    public void execute(Runnable task) {
        if (task == null) { // 如果传入的task任务为空，则直接抛空指针异常，此方法严格控制传入参数必须非空
            throw new NullPointerException("task");
        }

        boolean inEventLoop = inEventLoop(); // 判断要添加的任务的这个线程，是不是和正在运行的nioEventLoop的处于同一个线程？
        if (inEventLoop) { // 如果是，则说明就是当前线程正在添加task任务，那么则直接调用addTask方法添加到队列中
            addTask(task); // 添加task任务
        } else {
            startThread(); // 如果不是当前线程，则看看实例化的对象nioEventLoop父类中state字段是否标识有新建线程，没有的话则利用线程池新创建一个线程，有的话则不用理会了
            addTask(task); // 添加task任务
            // 防止意外情况，还需要判断下是否被关闭掉，如果被关闭掉的话，则将刚刚添加的任务删除掉并采取拒绝策略直接抛出RejectedExecutionException异常
            if (isShutdown() && removeTask(task)) {
                reject(); // 拒绝策略直接抛出RejectedExecutionException异常
            }
        }

        // addTaskWakesUp：添加任务时需要唤醒标志，默认值为false，通过构造方法传进来的也是false
        // wakesUpForTask(task)：不是NonWakeupRunnable类型的task则返回true，意思就是只要不是NonWakeupRunnable类型的task，都需要唤醒阻塞操作
        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }
	
2、通过一路跟踪config().group().register(channel)该方法进去，最后会发现，源码会调用一个register0(promise)的代码来进行注册；

3、但是跳出来一看，细细回味config().group().register(channel)这段代码，可以得出这样的一个结论：
   若以后大家想注册channel的话，直接通过线程管理组调用register方法，传入想要注册的channel对象即可；
   
4、当然还有一点请大家留意，execute(Runnable task)可以随意调用添加任务，如果线程已启动则直接添加，未启动的话则先启动线程再添加任务；
   
5、那么我们还是先尽快进入register0(promise)看看究竟是如何注册channel的；
```





### 4.12、register0(promise)

``` 
1、源码：
	// AbstractUnsafe.java
	private void register0(ChannelPromise promise) {
		try {
			// check if the channel is still open as it could be closed in the mean time when the register
			// call was outside of the eventLoop
			if (!promise.setUncancellable() || !ensureOpen(promise)) {
				return;
			}
			boolean firstRegistration = neverRegistered;
			doRegister(); // 调用Channel的注册方法，让Channel的子类AbstractNioChannel来实现注册

			// 执行到此，说明Channel已经注册到了多路复用器上，并且也没有抛出什么异常，那么接下来就赋值变量表明已经注册成功
			neverRegistered = false;
			registered = true;

			// Ensure we call handlerAdded(...) before we actually notify the promise. This is needed as the
			// user may already fire events through the pipeline in the ChannelFutureListener.
			pipeline.invokeHandlerAddedIfNeeded(); // 会回调initAndRegister中init方法的p.addLast的initChannel回调

			safeSetSuccess(promise);
			pipeline.fireChannelRegistered();
			// Only fire a channelActive if the channel has never been registered. This prevents firing
			// multiple channel actives if the channel is deregistered and re-registered.
			if (isActive()) { // 检测Channel是否处于活跃状态，这里调用的是底层的socket的活跃状态
				if (firstRegistration) {
					pipeline.fireChannelActive(); // 这里也是注册成功后会仅仅只会被调用一次
				} else if (config().isAutoRead()) {
					// This channel was registered before and autoRead() is set. This means we need to begin read
					// again so that we process inbound data.
					//
					// See https://github.com/netty/netty/issues/4805
					beginRead(); // 设置Channel的读事件
				}
			}
		} catch (Throwable t) {
			// Close the channel directly to avoid FD leak.
			closeForcibly();
			closeFuture.setClosed();
			safeSetFailure(promise, t);
		}
	}

	// AbstractNioChannel.java
    @Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (;;) { // 自旋式的死循环，如果正常操作不出现异常的话，那么则会一直尝试将Channel注册到多路复用器selector上面
            try {
                // eventLoop()对象是属于children[]属性中的其中一个，children是NioEventLoop类型的对象
                // 而前面也了解到过，在实例化每个children的时候，会为每个children创建一个多路复用器selector与unwrappedSelector
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                // 如果将Channel注册到了多路复用器上的成功且没有抛什么异常的话，则返回跳出循环
                return;
            } catch (CancelledKeyException e) {
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }	
	
	// DefaultChannelPipeline.java
    final void invokeHandlerAddedIfNeeded() {
        assert channel.eventLoop().inEventLoop();
        if (firstRegistration) { // pipeline标识是否已注册，默认值为true
            firstRegistration = false; // 马上置位false，告诉大家该方法只会被调用一次
            // We are now registered to the EventLoop. It's time to call the callbacks for the ChannelHandlers,
            // that were added before the registration was done.
            // 到此为止，我们已经将Channel注册到了NioEventLoop的多路复用器上，那么接下来是时候回调Handler被添加进来
            callHandlerAddedForAllHandlers();
        }
    }

	// DefaultChannelPipeline.java
    private void callHandlerAddedForAllHandlers() {
        final PendingHandlerCallback pendingHandlerCallbackHead;
        synchronized (this) {
            assert !registered; // 测试registered是否为false，因为该方法已经表明只会被调用一次，所以这里就严格判断

            // This Channel itself was registered.
            registered = true; // 而且当registered设置为true后，就不会再改变该值的状态

            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            // Null out so it can be GC'ed.
            this.pendingHandlerCallbackHead = null;
        }

        // This must happen outside of the synchronized(...) block as otherwise handlerAdded(...) may be called while
        // holding the lock and so produce a deadlock if handlerAdded(...) will try to add another handler from outside
        // the EventLoop.
        PendingHandlerCallback task = pendingHandlerCallbackHead;
        // 通过while循环，单向链表一个个回调task的execute，该回调添加的就回调添加，该回调移除的则回调移除
        while (task != null) {
            task.execute();
            task = task.next;
        }
    }

2、看完register0(promise)是不是觉得，原来服务端channel的注册是这么简单，最后就是调用javaChannel().register(...)这个方法一下，然后就这么稀里糊涂的注册到多路复用器上了；

3、在注册完之际，还会找到之前的单向链表对象pendingHandlerCallbackHead，并且依依回调task.execute方法；

4、然后触发fireChannelRegistered注册成功事件，告知上层说我们的服务端channel已经注册成功了，大家请知悉一下；

5、最后通过beginRead设置服务端的读事件标志，就是说服务端的channel仅对读事件感兴趣；

6、至此initAndRegister这块算是讲完了，那么接下来就看看最后一个步骤绑定ip地址，完成通信前的最后一步；
```





### 4.13、doBind0(regFuture, channel, localAddress, promise)

``` 
1、源码：
	// AbstractBootstrap.java
    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        // 服务端启动最后一个步骤，绑完地址就可以正式进行通信了
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
					// 服务端channel直接调用bind方法进行绑定地址
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

	// AbstractChannel.java
    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.bind(localAddress, promise);
    }

	// DefaultChannelPipeline.java
    @Override
    public final ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

	// AbstractChannelHandlerContext.java
    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        if (isNotValidPromise(promise, false)) {
            // cancelled
            return promise;
        }

        final AbstractChannelHandlerContext next = findContextOutbound();
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            next.invokeBind(localAddress, promise);
        } else {
            safeExecute(executor, new Runnable() {
                @Override
                public void run() {
                    next.invokeBind(localAddress, promise);
                }
            }, promise, null);
        }
        return promise;
    }

	// AbstractChannelHandlerContext.java
    private void invokeBind(SocketAddress localAddress, ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).bind(this, localAddress, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            bind(localAddress, promise);
        }
    }

	// HeadContext.java
	@Override
	public void bind(
			ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
			throws Exception {
		unsafe.bind(localAddress, promise);
	}

	// AbstractUnsafe.java
	@Override
	public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
		assertEventLoop();

		if (!promise.setUncancellable() || !ensureOpen(promise)) {
			return;
		}

		// See: https://github.com/netty/netty/issues/576
		if (Boolean.TRUE.equals(config().getOption(ChannelOption.SO_BROADCAST)) &&
			localAddress instanceof InetSocketAddress &&
			!((InetSocketAddress) localAddress).getAddress().isAnyLocalAddress() &&
			!PlatformDependent.isWindows() && !PlatformDependent.maybeSuperUser()) {
			// Warn a user about the fact that a non-root user can't receive a
			// broadcast packet on *nix if the socket is bound on non-wildcard address.
			logger.warn(
					"A non-root user can't receive a broadcast packet if the socket " +
					"is not bound to a wildcard address; binding to a non-wildcard " +
					"address (" + localAddress + ") anyway as requested.");
		}

		boolean wasActive = isActive();
		try {
			doBind(localAddress);
		} catch (Throwable t) {
			safeSetFailure(promise, t);
			closeIfClosed();
			return;
		}

		if (!wasActive && isActive()) {
			invokeLater(new Runnable() {
				@Override
				public void run() {
					pipeline.fireChannelActive();
				}
			});
		}

		safeSetSuccess(promise);
	}

	// NioServerSocketChannel.java
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (PlatformDependent.javaVersion() >= 7) {
            javaChannel().bind(localAddress, config.getBacklog());
        } else {
            javaChannel().socket().bind(localAddress, config.getBacklog());
        }
    }

2、经过这么一路调用，其实最终会发现，绑定地址也是通过javaChannel().bind(...)这么简短的一句话就搞定了；
   而前面的注册到多路复用器上调用的是javaChannel().register(...)一句简短代码；
   从而可得出这么一个结论：只要关系到channel的注册绑定，最终核心底层都是调用这个channel的bind和register方法；

3、至此，服务端的启动流程算是完结了。。
```





## 五、总结
``` 
	最后我们来总结下，通过分析Netty的服务端启动，经过的流程如下：
	• 创建两个线程管理组，以及实例化每个线程管理组的子线程数组children[]；
	• 设置启动类参数，比如channel、localAddress、childHandler等参数；
	• 反射实例化NioServerSocketChannel，创建ChannelId、unsafe、pipeline等对象；
	• 初始化NioServerSocketChannel，设置attr、option，添加新的handler到服务端pipeline管道中；
	• 调用JDK底层做ServerSocketChannel注册到多路复用器上，并且注册成功后回调pipeline管道中的单向链表依次执行task任务；
	• 调用JDK底层做NioServerSocketChannel绑定端口，并触发active事件；
``` 



## 六、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!