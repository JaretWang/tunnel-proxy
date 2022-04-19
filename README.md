# 工程简介
IP隧道服务，用于公司内部爬虫服务的请求代理IP的管理，分发和分配，优质ip筛选，并发数限制。



# 需求分析
现在购买的第三方代理服务，会通过第三方接口返回对应的代理的ip和port，进而实现爬虫服务使用不同的代理IP去爬取数据。
但是随着够买的第三方代理越来越多，有点难以管理，所以需要构建一个IP隧道服务，进而实现代理ip的统一管理，和动态分配。



# 核心思路分析
该隧道服务需要把公司内部的服务请求（connect请求），通过这个隧道服务转发出去，把请求的header line，header，body按照原封不动的形式，传输给第三方代理的ip，port，代理服务商就会帮我们去直接获取目标地址的资源，然后返回给我的隧道服务，最后再响应数据给公司的内部服务
> 注意:
遇到的所有请求肯定都是connect代理请求，这些connect请求本身肯定有proxy认证信息，有访问的目标域名，端口，header信息。
不能直接转发这个请求给第三方代理商，需要重新构造一个代理请求发送给代理商。因为认证信息不一样。



# 需求补充（以服务健壮性为重心）



## 负载均衡

1.首先需要部署多个服务实例，每个实例就是一条隧道，将隧道服务做成分布式的，当大量的请求过来后，我们可以借用nginx的负载均衡（或者LVS做TCP层面的负载均衡），将这些请求均匀地路由分配到不同的服务实例上。



## 单进程多隧道
在proxy-server前面加一层springboot(ps：自带web容器)进行管理, 包括创建以下管理信息：
- 需要创建多少个proxy-server实例（对应就是多少条隧道）

- 每个实例的名称，ip，端口，部署的机器位置，需要cpu的核数，内存的总量，网络带宽的总量

- 每个实例需要创建多少boss线程数，worker线程数，连接时的用户名密码，缓存的代理ip总数，每个ip的有效时间，定时任务更新IP池的cron表达式

  

## 隧道初始化
1.初始化建立隧道的时候，需要根据mysql的配置信息进行创建，但是创建出来的所有隧道服务实例消耗的总带宽，不能超过机器带宽的80%，剩下的做备用。
2.根据mysql隧道实例配置列表，创建一个proxy server，监听一个独立的端口
3.隧道动态分配: 后续考虑使用 xxl-job做任务调度，实现根据下发的创建任务进行一个新的隧道的创建

> 注意：1.隧道实例的个数不能随意增加，减少，因为运维分配的端口只有10个(21330-21339)  2.目前也暂不支持隧道的动态创建（根据下发的创建任务进行new一个新的隧道）



## 业务线程安全机制

1.原则上，每条隧道会有一个初始化的线程池，所有的业务请求都会放入队列中，考虑到请求的时效性，每个线程一般处理3个代理请求任务，一定不能让队列有任务积压。那么1000个并发请求，需要设置核心线程数约为350，最大线程数500。业务线程在处理每一个代理请求的时候，需要对慢请求做超时处理（规定3秒钟为超时），放入到一个超时任务队列中（具有优先级的那种），并用单独的线程进行重试，重试3次无果，即返回超时等待的结果。

**超时时间：**

**超时参数配置**。需要根据每个服务的需求，做动态配置，因为有一些视频传输的特殊场景。

**慢请求线程处理。**比方说有500个线程，其中有10%数量的线程，都在处理慢请求。如果这些慢请求的线程超过10%的比例，需要主动中断处理慢请求的线程。防止后续的请求任务积压在队列中，影响其他请求。

**超时时间的设置。**后端重试次数乘以后端的超时时间，不能超过前端总的超时时间。



## ip池更新机制
- [ ] 一般来说，默认会5 min 拉取一次IP，注入到ip池中, ip池的数量会根据mysql配置参数来决定，默认10个（动态配置）。
- [x] 当ip池的数量过低，低于了30%，需要提前触发ip的拉取，不受时间间隔限制。
- [x] 当ip池的数量过高（每个隧道对应ip池的ip个数不能超过10个），需要取消ip的拉取。

**补充：**

**为了服务稳定，ip池的数量，必须全满，不能是动态变化的。**

**ip池的数量x(24hx60min/ip存活时间（分钟）)，小于代理商的每天的IP数量限制。 所以 IP总消耗量不能超过代理商每天分配的数量**

```java
// ip_pool_num*((24h*60)/ip_ttl) < ip_total
// 现在暂时不用考虑这个问题，因为ZhiMaService的拉取速率不会超过每天的数量限制。
```



## ip自动检测
- [ ] ip利用率的检测：每个ip的使用率需要做统计，在从IP池提取ip去处理请求的时候，需要对ip的引用次数做记录，防止大量请求只打到某几个ip上，导致其他ip没有使用，或使用率很低。
- [ ] ip质量检测：每个IP都有一个失效时间，为了提高ip的利用率，我们需要设置一个ip监控线程，在ip失效的前5s检查出来，并进行剔除。某个ip在首次使用后，会超时或失败，需要重试，重试3次过后都不行，会记录失败次数，导致成功率很低。这时就需要通过ip打分机制，将这个ip的权重降低，同时使用率也需要减少。然后会生成一个ip质量有序列表，最后由ip池监控线程将列表末尾的20%剔除掉。（**ps: 需要根据实际执行的日志分析得出结果，再决定如何剔除，因为可能会出现一种极端情况，大量的ip都是低质量的ip，如果全部剔除就会影响服务**）
- [ ] ip打分机制：可以参考elasticsearch的权重打分机制。



## 流量控制
- [x] 需要对每个隧道的并发量做控制，都在1000左右。具体的请求数量限制，由服务器带宽，ip池数量，业务线程可用数量，综合决定，做一个动态的控制。(**ps: 每个隧道的并发量的控制，现在暂时设成固定的1000或者500**)
- [x] 所有的隧道消耗的带宽，加起来不能超过机器总带宽的80%。每条隧道初始化10MB（**ps：暂时不用考虑，因为部署的服务所在的机器，都是一些千兆网卡，公司内部服务的所有请求过来，基本上也不会超过其设定的值。**）



## 日志记录

日志需要清晰的记录转发过程：来源ip，来源请求类型，使用的哪个隧道服务，使用的哪个代理ip，请求的响应结果如何，请求耗时多长，请求超时或错误的具体原因信息。



# 服务部署

```shell
机器地址：61-> adx-crawl-007   172.18.211.168  120.25.162.186   16/32G
部署路径：/usr/local/htdocs/tunnel-proxy
日志路径：/data0/logs/tunnel-proxy
启动参数：-Dspring.profiles.active=product -Dserver.port=21330
端口使用：21330~21339   #这10个端口是规划给 tunnel-proxy 这个服务的
```



# 其他

win10 杀死端口占用的进程
查看所有进程：netstat -nao
查找指定端口进程： netstat -nao|find "8080" （这里的8080指要查找的端口号）
终止指定PID进程：taskkill /pid 8548 -F（这里的8548指要终止进程对应的PID号）



## 注意

1.根据MySQL初始化不同的隧道列表，每条隧道对应都有一个代理ip环形队列作为ip池（注意：芝麻代理的每个ip获取都是根据你的本机器的ip来计算的，
不同的机器ip请求获取的时候，代理IP的可用个数会对应减1，相同的ip获取的时候，只会当作一个）



# AB测试

ab -n 1000 -c 1 http://13.209.21.196:8080/trade-server/test/order/testQueue



# 问题记录

### 线上大量请求后，代理 ip 报出connect time out

- 第一步，使用代理ip和非代理ip的方式，在本地压测百度，新浪，查看区别
  本地直接请求百度：1000个线程，超过1000个会报socket closed，read timed out，EOFException异常

  代理ip请求百度：1000个线程就会报, connection timed out()

- 第二部，如果对比后发现代理IP有问题，就调整线程数，查看最大值是多少
- 第三步，使用代理ip下，每5秒，打印每个ip的使用次数，成功次数，失败次数，带宽大小



Apn-Proxy纯netty支持和Tunnel-Proxy业务线程池的增强，在相同并发下，进行压力测试

