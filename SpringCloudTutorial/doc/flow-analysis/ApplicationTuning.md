# 原理剖析（第 013 篇）应用系统性能调优
-

## 一、大致介绍

``` 
1. 本人接手的一个打车系统，因为出现了一次响应十分缓慢的情况，因此才有了应用调优的篇章；
2、由于过程中可能没有阐述的太清楚，如想详细了解可以留言之类的，希望其中的点点滴滴对大家有所帮助；
```



## 二、调优背景
``` 
    在某一个月黑风高的夜晚，21点多以后，许许多多的小伙伴都相继下班了，然后大家开始习惯性的掏出自己心爱的手机，
点击了公司的打车应用App，大概在 21:00 ~ 21:30 期间还风平浪静的，但是就在 21:30 ~ 21:40 的样子，突然打车应用App
响应非常缓慢，App页面上的菊花一直转个不停，直到App请求超时菊花才消失, 然后用户还是不停的点击App，因为这个时候
小伙伴们都急着回家，都相继不停的请求下单，获取详情等页面操作，体验非常不友好；
    因为出现了这么一次情况，虽然没有给用户没有给公司带来什么损失，但是该现象从侧面已经反应出了系统某些方面的
问题，或许系统参数需要优化一番，或许系统设计交互需要优化一番，或许等等等的可能，才有了后续系统调优的历程。
```



## 三、计划分析
``` 
1、流程相关分析优化：看看哪些流程可以同步转异步处理，App哪些请求可以合并起来，Server哪些业务场景需要补偿机制等。

2、数据库相关分析优化：哪些Sql耗时较长，哪些方法可以去除事务且去除事务后的带来的问题场景分析，数据库连接池参数
                       是否合理，数据库本身相关参数的阈值情况的一些综合考虑；

3、内存使用情况分析优化：新老年代内存使用率及回收情况，CPU使用率，磁盘使用率，swap区使用情况, 线程dump，堆dump。

4、JVM参数分析调优：YGC的平均耗时，YGC的平均间隔，FGC的平均耗时，FGC的平均间隔等等，根据具体情况反映具体问题；

5、TCP/Tomcat参数分析调优：这个得根据实际压测情况来相应评估是否需要调整；
```



## 四、Linux命令相关查看指标

### 4.1 CPU 指标
``` 
1、	vmstat n m
	每n秒采集一次，一共采集m次，r：表示运行队列，r值一般负载超过了3就比较高，超过了5就高，超过了10就不正常了;
	bi和bo一般都要接近0，不然就是IO过于频繁 ）

	[root@svr01]$ vmstat 1 3
	procs -----------memory---------- ---swap-- -----io---- --system-- -----cpu-----
	r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
	0  0 206944 633564  29876 1252176    0    0    10    27    0    0  1  1 98  0  0
	0  0 206944 634232  29876 1252192    0    0     0     0  811 1504  1  1 98  0  0
	0  0 206944 634480  29876 1252264    0    0     0     0  951 1458  6  1 93  0  0
   
2、	uptime
	最近1分钟，5分钟，15分钟的系统平均负载， 
	<=3 则系统性能较好。
	<=4 则系统性能可以，可以接收。
	>5 则系统性能负载过重，可能会发生严重的问题，那么就需要扩容了，要么增加核
	
	[root@svr01]$ uptime
	21:27:44 up 207 days, 11:15, 1 user, load average: 26.45, 16.76, 7.50
	
3、	top
	主要看us和sy，其中us<=70，sy<=35，us+sy<=70说明状态良好，
	同时可以结合idle值来看，如果id<=70 则表示IO的压力较大。
```

### 4.2 Memory 指标
``` 
1、	vmstat		
	swpd：虚拟内存已使用的大小，如果大于0，表示你的机器物理内存不足了 
	每秒从磁盘读入虚拟内存的大小，如果这个值大于0，表示物理内存不够用或者内存泄露了，要查找耗内存进程解决掉。
	so：每秒虚拟内存写入磁盘的大小，如果这个值大于0，同上，单位为KB。 	

	[root@svr01]$ vmstat 1 3
	procs -----------memory---------- ---swap-- -----io---- --system-- -----cpu-----
	r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
	0  0 206944 633564  29876 1252176    0    0    10    27    0    0  1  1 98  0  0
	0  0 206944 634232  29876 1252192    0    0     0     0  811 1504  1  1 98  0  0
	0  0 206944 634480  29876 1252264    0    0     0     0  951 1458  6  1 93  0  0
```

### 4.3 Disk 指标
``` 
1、	df
	Use%：已使用占比，Use% <= 90% 表示还勉强接受正常
	
	[root@svr01]$ df
	Filesystem           1K-blocks     Used Available Use% Mounted on
	/dev/mapper/VolGroup00-LVroot
						  17737040  4286920  12542448  26% /
	tmpfs                  1893300        0   1893300   0% /dev/shm
	/dev/sda1               194241   127341     56660  70% /boot
	/dev/mapper/VolGroup00-LVhome
							487652     2348    459704   1% /home
	/dev/mapper/VolGroup00-LVcloud
						   3030800   260440   2613076  10% /opt/cloud
	/dev/mapper/VolGroup00-LVtmp
						   8125880    18724   7687728   1% /tmp
	/dev/mapper/VolGroup00-LVvar
						  25671996   848996  23512280   4% /var
	/dev/mapper/VolGroup1-LVdata1
						  41149760 33707952   5344864  87% /wls/applogs	
```

### 4.４ Disk IO 指标
``` 
1、	sar -d 1 1
	查看磁盘报告 1 1 表示间隔1s，运行1次
	
	如果svctm的值与await很接近，表示几乎没有I/O等待，磁盘性能很好，如果await的值远高于svctm的值，
	则表示I/O队列等待太长，系统上运行的应用程序将变慢。
	
	如果%util接近100%，表示磁盘产生的I/O请求太多，I/O系统已经满负荷的在工作，该磁盘请求饱和，可能存在瓶颈。
	idle小于70% I/O压力就较大了，也就是有较多的I/O。											
													
	[root@svr01]$ sar -d 1 1
	Linux 2.6.32-642.6.2.el6.x86_64 (SHB-L0044551) 07/20/2018 _x86_64_ (1 CPU)

	03:00:23 PM DEV tps rd_sec/s wr_sec/s avgrq-sz avgqu-sz await svctm %util
	03:00:24 PM dev252-0 23.00 808.00 80.00 38.61 9.88 375.35 43.48 100.00
	03:00:24 PM dev252-16 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
	03:00:24 PM dev253-0 4.00 448.00 0.00 112.00 1.11 222.00 249.50 99.80
	03:00:24 PM dev253-1 50.00 400.00 0.00 8.00 24.40 523.20 20.00 100.00
	03:00:24 PM dev253-2 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
	03:00:24 PM dev253-3 3.00 32.00 0.00 10.67 0.99 242.33 331.67 99.50
	03:00:24 PM dev253-4 0.00 0.00 0.00 0.00 1.61 0.00 0.00 100.00
	03:00:24 PM dev253-5 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
	03:00:24 PM dev253-6 3.00 0.00 24.00 8.00 1.30 393.67 261.33 78.40

	Average: DEV tps rd_sec/s wr_sec/s avgrq-sz avgqu-sz await svctm %util
	Average: dev252-0 23.00 808.00 80.00 38.61 9.88 375.35 43.48 100.00
	Average: dev252-16 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
	Average: dev253-0 4.00 448.00 0.00 112.00 1.11 222.00 249.50 99.80
	Average: dev253-1 50.00 400.00 0.00 8.00 24.40 523.20 20.00 100.00
	Average: dev253-2 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
	Average: dev253-3 3.00 32.00 0.00 10.67 0.99 242.33 331.67 99.50
	Average: dev253-4 0.00 0.00 0.00 0.00 1.61 0.00 0.00 100.00
	Average: dev253-5 0.00 0.00 0.00 0.00 0.00 0.00 0.00 0.00
	Average: dev253-6 3.00 0.00 24.00 8.00 1.30 393.67 261.33 78.40
```

### 4.5 Network IO 指标
``` 
1、	netstat -nat |awk '{print $6}'|sort|uniq -c|sort -rn

	在不考虑系统负载、CPU、内存等情况下，netstat监控大量ESTABLISHED连接与Time_Wait连接

	[root@svr01]$ netstat -nat |awk '{print $6}'|sort|uniq -c|sort -rn
		265 TIME_WAIT
		 45 ESTABLISHED
		 38 CLOSE_WAIT
		 18 LISTEN
		  8 FIN_WAIT2
		  2 SYN_SENT
		  1 Foreign
		  1 established)
```


## 五、一些关于统计的量化指标
```
注意：有些命令通用，有些是我根据系统的日志文件格式利用awk/sed两个命令结合写出来的。

1、netstat -nat |awk '{print $6}'|sort|uniq -c|sort -rn （ 查看TCP连接状态 ）

2、netstat -n|grep TIME_WAIT|awk '{print $5}'|sort|uniq -c|sort -rn|head -n20（ 查找较多time_wait连接 ）

3、netstat -anlp|grep tcp |awk '{print $5}' |awk -F':' '{print $1}' |uniq -c |sort -nr | head -n3（ 查出访问靠前的IP地址 ）

4、cat hmilyylimh_sql.log | awk '{print $6}' | awk -F'ms' '{print $1}' | awk -F'=' '{print $2 | "sort -r -n"  }' | head -n5（ 查询sql文件中耗时最高的前5个耗时数据值 ）

5、cat hmilyylimh_supp.log | awk '{print $10}' | awk -F'timeConsuming=' '{print $2 }' | awk -F'ms' '{print $1 | "sort -r -n" }' | head -n5（ 查看supp文件中耗时最高的前5个耗时数据值 ）

6、cat hmilyylimh_sql.log | grep 'sql:=' | awk '{print $5}' | uniq -c | sort -rn | head -n2（ 查询sql文件总共打印了多少条SQL日志 ）

7、cat hmilyylimh_sql.log | grep 'NormalTimeConsuming' | awk '{print $5}' | uniq -c | sort -rn | head -n2（ 查看sql文件成功执行了多少条SQL日志 ）

8、cat hmilyylimh_sql.log | grep 'BadTimeConsuming' | awk '{print $5}' | uniq -c | sort -rn | head -n2（ 查看sql文件失败或者异常执行了多少条SQL日志 ）

9、cat hmilyylimh_supp.log | grep 'sendReqSupp start'| awk '{print $6$7$8}' | uniq -c | sort -nr | head -n2（ 查询supp文件sendReqSupp start字符串出现的次数 ）

10、lsof -n | awk '{print $1,$2}' | sort | uniq -c | sort -nr | head -n10（ 统计持有各个进程持有句柄数最高的10个 ）

11、lsof -n | awk '{print $1,$2}' | sort | uniq -c | sort -nr | awk '{ sum+=$1 };END { print sum } '（ 计算所有进程持有句柄数的总和，ulimit -n命令查看最大句柄数 ）

12、lsof | awk 'NF == 9 { print $0}' | sort +6 -7nr | head -n10（ 查看系统打开的大文件列表 ）

13、top -b -n 1 | grep -E 'Cpu\(s\)|Mem|Swap'（ 一次性查出系统当前的CPU、内存、交换区的情况 ）

14、iostat -p sda | awk -F'Device' '{ print $1 }'（ 查看cpu的统计信息(平均值) ）

15、cat access_log.`date +%Y%m%d`.txt | awk '{print $6}' | uniq -c | sort -k2 -r | head -n10（ 统计每秒请求并发，按照时间降序排列 ）

16、cat access_log.`date +%Y%m%d`.txt | awk '{print $6}' | uniq -c | sort -rn | head -n10（ 统计每秒并发，按照并发量降序排列 ）

17、cat access_log.`date +%Y%m%d`.txt | awk '{ sum+=$NF }; END { print sum*2/8/1024/1024, "M" }'（ 查看访问hmilyylimh服务器每天的总流量 ）

18、cat gc.log | tail -n20|awk '{print $4}'| awk -F'->' '{print $1, $2, $3 }'| awk -F'(' '{print $1, $2, $3}' | awk -F')' '{print $1}' | awk -F'K' '{print $1/$3*100,  "% used  ->     "  ,$2/$3*100, "% used     " , 100-$2/$3*100, "% free      ",  $3/1024, "M total     ---    新生代" }'（ 查看gc指标，新生代最后n条记录的新生代内存变化率 ）

19、cat gc.log | tail -n20 | awk '{print $7}' | awk -F'->' '{print $1, $2, $3 }' | awk -F'(' '{print $1, $2, $3}' | awk -F')' '{print $1}' | awk -F'K' '{print $1/$3*100,  "% used  ->     "  ,$2/$3*100, "% used     " , 100-$2/$3*100, "% free      ",  $3/1024, "M total     ---    堆内存" }'（ 查看最后10条GC日志的堆内存已使用转化率 ）

20、cat /etc/sysctl.conf | grep 'tcp_'（ 查看TCP参数设置信息 ）

21、cat hmilyylimh.log | awk '{if($2>"15:17:00.236") print $0}' | grep "max_user_connections" | wc -l（ 查看具体时间点后某个字符串出现的次数 ）
```


## 六、系统常用计数器命令
```
1、echo "<<<<<<<<<<<<<< 线程阻塞等待计数: "`less hmilyylimh_error.log | grep "with callerRunsPolicy" | wc -l`",   ""db事务嵌套锁AcquireLock计数: "`less hmilyylimh_error.log | grep "CannotAcquireLockException" | wc -l`",   ""创建事务异常计数: "`less hmilyylimh_error.log | grep "CannotCreateTransactionException" | wc -l`",    ""db连接池溢出计数: "`less hmilyylimh_error.log | grep "more than 'max_user_connections'" | wc -l`",    ""Pool Empty计数: "`less hmilyylimh_error.log | grep "Unable to fetch a connection" | wc -l`" >>>>>>>>>>>>>>"

2、echo "<<<<<<<<<<<<<< UnknownHostException计数: "`less hmilyylimh_error.log | grep "UnknownHostException" | wc -l`",   ""ConnectionPoolTimeout计数: "`less hmilyylimh_error.log | grep "ConnectionPoolTimeout" | wc -l`",   ""ConnectException计数: "`less hmilyylimh_error.log | grep "ConnectException" | wc -l`",   ""ConnectTimeoutException计数: "`less hmilyylimh_error.log | grep "ConnectTimeoutException" | wc -l`",   ""SocketTimeoutException计数: "`less hmilyylimh_error.log | grep "SocketTimeoutException" | wc -l`",   ""OtherException计数: "`less hmilyylimh_error.log | grep "OtherException" | wc -l`" >>>>>>>>>>>>>>"

3、echo "<<<<<<<<<<<<<< Sql耗时最高的前5个数值: "`cat hmilyylimh_sql.log | awk '{print $6}' | awk -F'ms' '{print $1}' | awk -F'=' '{print $2 | "sort -r -n"  }' | head -n5`",   ""Supp耗时最高等待前5个数值: "`cat hmilyylimh_supp.log | awk '{print $10}' | awk -F'timeConsuming=' '{print $2 }' | awk -F'ms' '{print $1 | "sort -r -n" }' | head -n5`" >>>>>>>>>>>>>>"

4、echo "<<<<<<<<<<<<<< Http请求耗时最高前10个数值: "`less hmilyylimh.log | grep "timeConsuming=" | awk '{print $9}' | awk -F'=' '{print $2}' | awk -F'ms' '{print $1 | "sort -r -n"  }' | head -n10`" >>>>>>>>>>>>>>"
```



## 七、流程相关分析优化
```
1. 通过 access_log.txt 日志分析，在特定时间段内，将请求至系统的 url 分组计数，最后会出一个根据url调用次数的排序；

2、针对请求次数数一数二的url接口，在分析完业务场景后，决定将高频率的接口优化成同步转异步；

3、然而想查看Server端每个Http请求的耗时时间，却发现没有相关功能点统计，于是乎又新增切面Aspect来统计Client请求至
   Server的耗时统计日志，然后通过 less,grep,awk 命令来分析耗时最高的url请求，后续可做成监控及时发现长耗时请求；

4、同样发现请求下游系统也没有相关耗时统计，因此依葫芦画瓢再次新增请求下游系统切面来统计耗时情况；

5、在后续的压测过程中，效果显著，单台服务器的TPS提升了3倍，而且在压测过程不断打出线程dump, 堆dump日志，然后
   重点将 hmilyylimh_20180716-143726.threads 放到网页中分析，结果发现一个比较严重的问题，Log4J 1.x blocked 
   问题,总计大概有90多个线程在等待同一把锁。于是乎将应用的 log4j 1.x 版本升级至 log4j 2.x 版本。但是呢，官方
   明确说明：Log4j 2.4 and greater requires Java 7, versions 2.0-alpha1 to 2.3 required Java 6。因此我们就只好
   采用了2.3版本。
   
6、后面还考虑了将请求下游系统的日志做成了异步打印，效果更佳；   
   
7、通过和前端沟通，将一些请求进行了进行了聚合处理返回给前端，在某种程度上友好的提升了交互体验；   

8、针对支付场景的支付状态同步新增了补偿机制，后台主动查询下游系统订单的支付状态，最大力度的保证用户看到的支付
   状态是准实时的；
   
9、针对Http的工具类，根据业务的实际情况定制一套超时参数，尽量减少Http连接长时间被Hold住不释放，必要时Http请求
   工具类做相应的尝试次数控制；   

10、最后的压测也比较简单，通过写程序操控状态扭转，将整个打车流程走完，程序只需要传入人数来压测系统最高负荷；   
   
   经过后续压测显示，日志锁显然不存在了，最后一次统计的数据得知TPS相比最初提升了至少5倍以上，由此可见流程的优化
和日志的优化对于系统的性能提升有很大的帮助；
```


## 八、数据库相关分析优化
```
1、通过收集产线出问题时刻的数据库指标( max_used_connections/max_user_connections/max_connections )，除了这三个
   参数意外，还收集了数据库连接超时时间，出问题时刻数据库实例前后几个小时的时序分析图；

2、在测试环境压测情况下，而后台应用的连接池的参数配置为：

   尝试步骤一：
   (max_connections=160, max_user_connections=80, 两台1核2G的服务器)
   tomcat.initialSize=25
   tomcat.validationInterval=25000
   tomcat.maxActive=85
   tomcat.minIdle=25
   tomcat.maxIdle=25
   结论：系统部署重启后直接启动不起来，报的什么错已经忘记了，然后继续调整连接池参数；
   
   
   尝试步骤二：
   (max_connections=160, max_user_connections=80, 两台1核2G的服务器)
   tomcat.initialSize=12
   tomcat.validationInterval=25000
   tomcat.maxActive=85
   tomcat.minIdle=12
   tomcat.maxIdle=12
   结论：系统能正常启动，相对于步骤一来说，连接池的合理配置非常重要，但是在压测情况下发生了一些异常；
   --- Cause: com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException: User hmily already has more than 
   'max_user_connections' active connections，于是乎继续后续步骤；
   
   
   尝试步骤三：
   (max_connections=160, max_user_connections=80, 两台1核2G的服务器)
   tomcat.initialSize=12
   tomcat.validationInterval=25000
   tomcat.maxActive=80
   tomcat.minIdle=12
   tomcat.maxIdle=12
   结论：相对于步骤二来说，异常还是有一些，数据库的 160 刚刚等于 tomcat.maxActive * 2 = 160，
   但是又引发了另外一个异常：
   Cause: org.apache.tomcat.jdbc.pool.PoolExhaustedException: [http-nio-0.0.0.0-40009-exec-91] Timeout: Pool
   empty. Unable to fetch a connection in 5 seconds, none available[size:40; busy:3; idle:37; lastwait:5000]；
   于是乎继续后续步骤；
   
   
   尝试步骤四：
   (max_connections=160, max_user_connections=80, 两台1核2G的服务器)
   tomcat.initialSize=12
   tomcat.validationInterval=25000
   tomcat.maxActive=80
   tomcat.minIdle=12
   tomcat.maxIdle=12
   结论：相对于步骤二来说，异常还是有一些，数据库的 160 刚刚等于 tomcat.maxActive * 2 = 160，
   但是还是会引发了另外一个异常：
   Cause: org.apache.tomcat.jdbc.pool.PoolExhaustedException: [http-nio-0.0.0.0-40009-exec-91] Timeout: Pool
   empty. Unable to fetch a connection in 5 seconds, none available[size:40; busy:3; idle:37; lastwait:5000]；
   于是乎继续后续步骤；
   
   在这一步，我们无法计算每个SQL的耗时情况，于是集成了SqlMapClientTemplate类，在SqlMapClientTemplate的扩展类
   中针对queryForObject、queryForList、update、delete主流的方法进行了before/after/finally增强处理打印SQL耗时；
   
   
   尝试步骤五：
   (max_connections=160, max_user_connections=80, 两台1核2G的服务器)
   tomcat.initialSize=12
   tomcat.validationInterval=25000
   tomcat.maxActive=40
   tomcat.minIdle=12
   tomcat.maxIdle=12
   结论：在步骤四中，通过将maxActive改回40, 且对耗时的SQL进行优化处理，同时对一些不常变化的SQL配置进行了缓存
   处理，这样一来在压测高并发的情况下，所有情况全部有所好转，一切正常自如；
   
   但是我远不满足于此，然后我又开始调整连接池参数配置；
   
   
   尝试步骤六：
   (max_connections=320, max_user_connections=160, 两台1核2G的服务器)
   tomcat.initialSize=12
   tomcat.validationInterval=25000
   tomcat.maxActive=80
   tomcat.minIdle=12
   tomcat.maxIdle=12
   结论：之前出现的异常也没有了，于是我综合以上我的各种尝试，实际测试总结了一些关于个人理解的参数配置；
   

3、小结：
   对于连接池这块，想必大家也同样都是从网上摘抄下来的配置，虽然调整后的配置不一定是最佳的，但是对整个应用没有
起到副作用的话，那就是对应用最适合的配置。而且大家在配置的时候，还得综合考虑服务器水平扩展后对数据库的一个压力
情况，调整的时候一定要参照数据库实例的参数配置来综合考虑衡量应用的参数配置。

	
   #初始化连接: 连接池启动时创建的初始化连接数量
   tomcat.initialSize=12      
   -- 原因：线程快照图最小连接数为22，目前由于业务量大，仅仅只是2台服务器，考虑到后续的业务还会继续扩大，
      因为该参数不适宜调整的太高，根据实际情况最后调整为12。

   #避免过度验证，保证验证不超过这个频率——以毫秒为单位
   tomcat.validationInterval=25000      
   -- 原因：数据库的连接超时时间是29秒，我们尽可能小于数据库的连接超时时间

   #最大活动连接: 连接池在同一时间能够分配的最大活动连接的数量,如果设置为非正数则表示不限制
   tomcat.maxActive=50      
   -- 原因：线程快照图的最大连接数为82，目前产线只有2台服务器，最大也就是 50 * 2 = 100，还远远小于 
   max_user_connections数值，最初是滴测试情况调整的为40，而产线的服务器性能绝对比测试环境好，综合评估定位50。

   #最小空闲连接: 连接池中容许保持空闲状态的最小连接数量, 低于这个数量将创建新的连接, 
   如果设置为0则不创建.默认与initialSize相同
   tomcat.minIdle=12      
   -- 原因：考虑和initialSize调整为一样的

   #最大空闲连接: 连接池中容许保持空闲状态的最大连接数量, 超过的空闲连接将被释放, 如果设置为负数表示不限制
   tomcat.maxIdle=12      
   -- 原因：考虑和minIdle一样
```



## 九、内存使用情况分析优化
```
1、通过 awk 命令分析gc日志文件，发现老年代还有较多的空间可以利用，于是就是看看哪些是可以不经常变化的配置，尽量
   减少数据库IO的操作，最大程度先去读取缓存，缓存没有再去从数据库中加载数据。

2、在压测的过程中，尽量多一些headDump文件，然后通过 eclipse 工具来分析看看都有哪些大对象，也没有发现任何有疑点
   的地方，于是反查代码，在一些定时任务需要加载大量数据的地方，当加载出来的数据用完之后直接手动释放，尽快让垃圾
   回收期回收内存。
   
3、还有一点就是，在一些后台定时轮询的任务中，有些任务需要通过for循环来处理一些任务，这个时候我们可以每循环一次
   或者循环数次之后通过调用Thead.sleep(xxx)休眠一下，一是可以缓冲一下IO高密度的操作，还有就是让出CPU时间片，
   让有些紧急的任务可以优先获取CPU执行权。
   
4、做完这些后，压测后通过GC日志分析，老年代内存相比未优化前多了一些，但是性能吞吐率却大大的得到了提高，磁盘IO
   的TPS也在一定程度上降低了。
```



## 十、JVM参数分析调优
```
1、为了应对将来的高业务量，目前需要扩容服务器，将2台服务器扩容至4台服务器，然后将服务器由2核4G升级成为4核8G。
   因此在升级过程中对于参数的调整也存在了一定的迷惑期。
   
2、JVM参数的调整测试过程一两句话也说不清，这里我就讲解一下大致的思路：
	• YGC的平均耗时，YGC的平均间隔，可以通过GC日志完整分析出来，根据实际情况是否需要调整堆大小，年轻代占比，存活区占比；   
	• FGC的平均耗时，FGC的平均间隔，同样可以通过GC日志完整分析出来，重点关注FGC耗时，想办法调整堆大小，年轻代占比，存活区占比，垃圾回收器方式；
	• S2区的使用占比，如果S2占比为0，且YGC平均耗时也在40ms以内的话，也没有FGC，这也算是相对比较理想的情况；
	• S2区满的话，FGC频繁或者GC效果很差时建议调整堆大小，还得不到改善就要开发分析实际Heap消耗的对象占比了；	
	
3、最后附上我的4核8G的JVM参数情况如下：

   -Xms2048M -Xmx2048M -Xss256k 
   -XX:NewSize=512m -XX:MaxNewSize=512m -XX:SurvivorRatio=22 
   -XX:PermSize=256m -XX:MaxPermSize=512m 
   -XX:+UseParNewGC -XX:ParallelGCThreads=4 -XX:+ScavengeBeforeFullGC 
   -XX:+CMSScavengeBeforeRemark -XX:+UseCMSCompactAtFullCollection 
   -XX:CMSInitiatingOccupancyFraction=60 -XX:CMSInitiatingPermOccupancyFraction=70 
   -XX:+PrintGCApplicationConcurrentTime -XX:+PrintHeapAtGC -XX:+HeapDumpOnOutOfMemoryError 
   -XX:HeapDumpPath=logs/oom.log -XX:+UseConcMarkSweepGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps 
   -verbose:gc -Xloggc:logs/gc.log -Djava.net.preferIPv4Stack=true
   
4、GC优化的一点建议：
   	• Minor GC 执行非常迅速 ( 建议 50ms 以内 )
   	• Minor GC 没有频繁执行 ( 建议大约 10s 执行一次 )
   	• Full GC 执行非常迅速 ( 建议 1000ms 以内 )
   	• Full GC 没有频繁执行 ( 建议大约 10min 执行一次 )
```



## 十一、TCP/Tomcat参数分析调优
```
1、在测试压测过程中，由于很大提升系统应用的请求并发，因此需要调整Tomcat中相关的参数，maxThreads、acceptCount
   (最大线程数、最大排队数)，也就是说需要查看 tomcat的Connector、Executor 所有属性值，但产线本人并未做相应调整；

2、在压测过程中，发现大量的TIME-WAIT的情况，于是根据实际调整系统的TCP参数，在高并发的场景中，TIME-WAIT虽然会峰
   值爬的很高，但是降下来的时间也是非常快的，主要是需要快速回收或者重用TCP连接。
   
3、最后附上我的4核8G的TCP参数情况如下：
   vim /etc/sysctl.conf				
   #编辑文件，加入以下内容：				
   net.ipv4.tcp_syncookies = 1				
   net.ipv4.tcp_tw_reuse = 1				
   net.ipv4.tcp_tw_recycle = 1				
   net.ipv4.tcp_fin_timeout = 30				
   #然后执行 /sbin/sysctl -p 让参数生效。				   
```



## 十二、小结
```
   总而言之，经过这一系列的分析调优下来，对于系统来说，应用的系统性能提升相当可观，同时也在优化的过程中建立起了
一套自己独有的思考方式，虽说目前优化下来的这套配置并不是最完美的，但是它确实最适合系统的。
   以上这些数据值仅供参考，如果大家有更好的方式或者更优雅的调优方式，也欢迎大家一起来多多讨论。
```


## 十三、下载地址

[https://gitee.com/ylimhhmily/SpringCloudTutorial.git](https://gitee.com/ylimhhmily/SpringCloudTutorial.git)

SpringCloudTutorial交流QQ群: 235322432

SpringCloudTutorial交流微信群: [微信沟通群二维码图片链接](https://gitee.com/ylimhhmily/SpringCloudTutorial/blob/master/doc/qrcode/SpringCloudWeixinQrcode.png)

欢迎关注，您的肯定是对我最大的支持!!!


