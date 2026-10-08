# 企业内部意见反馈系统

一个用于收集与展示企业内部意见的 Spring Boot 单体应用。核心不在功能有多复杂，而在于把
**Redis 缓存**与 **RabbitMQ 异步通知**这两件事做在了一个能跑通的完整业务闭环里——提交意见后
立刻能在列表页看到（缓存失效），同时下游通过 MQ 收到通知（异步解耦）。

> 技术栈：Spring Boot 2.7.18 · MyBatis-Plus 3.5.5 · MySQL 8 · Redis · RabbitMQ · Thymeleaf

---

## 功能

| 页面 | 路径 | 说明 |
| --- | --- | --- |
| 首页导航 | `GET /` | 系统入口，跳转提交页与列表页 |
| 意见提交 | `GET /submit` | 表单页，Thymeleaf `th:object` + `th:field` 双向绑定 |
| 提交处理 | `POST /submit` | 落库 → 发射 MQ 通知 → 清除列表缓存 → 重定向到列表页 |
| 意见列表 | `GET /list` | 统计条 + 意见表格，查询走 Redis 缓存 |

---

## 技术栈

| 分层 | 选型 | 说明 |
| --- | --- | --- |
| Web | Spring Boot Web | MVC 三层结构 |
| 视图 | Thymeleaf | 服务端渲染，三个页面 |
| 持久层 | MyBatis-Plus | `BaseMapper` 免写单表 SQL |
| 数据库 | MySQL 8 | `feedback_db.feedback` 单表 |
| 缓存 | Spring Cache + Redis | `@Cacheable` / `@CacheEvict`，TTL 5 分钟 |
| 消息 | Spring AMQP + RabbitMQ | Fanout 交换器 + 持久化队列 + `@RabbitListener` |

---

## 架构

```
                    ┌──────────────────────────────────────────┐
   浏览器  ────────► │  FeedbackController                       │
                    │  / , /submit(GET/POST) , /list            │
                    └───────────────┬──────────────────────────┘
                                    │
                                    ▼
                    ┌──────────────────────────────────────────┐
                    │  FeedbackServiceImpl                      │
                    │                                           │
                    │  submitFeedback()   ── @CacheEvict ──┐    │
                    │  getAllFeedbacks()  ── @Cacheable ──┐│    │
                    └──────┬──────────────────┬───────────┘│    │
                           │                  │            │    │
              ┌────────────▼──────┐   ┌───────▼──────┐   ┌─▼────▼─────┐
              │  MySQL            │   │  Redis       │   │ RabbitMQ   │
              │  feedback 表      │   │  feedbackList│   │ fanout     │
              └───────────────────┘   └──────────────┘   └─────┬──────┘
                                                               │
                                                     ┌─────────▼─────────┐
                                                     │ FeedbackNotice    │
                                                     │ Consumer          │
                                                     │ (@RabbitListener) │
                                                     └───────────────────┘
```

两条链路值得单独说：

- **读链路**：`/list` → `getAllFeedbacks()` 先看 Redis。命中就直接返回，方法体里的日志不会打印；
  未命中才回源 MySQL 并把结果写进 `feedbackList`，5 分钟后自动过期。
- **写链路**：`/submit` → 落库 → 发 MQ（Fanout 广播）→ `@CacheEvict` 清空列表缓存。第三步是
  关键：如果只写库不清缓存，用户提交完刷新列表会看不到自己的意见。

---

## 快速开始

### 1. 前置依赖

- JDK 8 及以上（实测 JDK 17 可正常运行）
- Maven 3.6+
- MySQL 8、Redis 6+、RabbitMQ 3.x

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

脚本会建库 `feedback_db`、建表 `feedback`，并插入 3 条初始数据方便看效果。

### 3. 确认 Redis 与 RabbitMQ 已启动

```bash
redis-cli ping          # 期望输出 PONG
# RabbitMQ 管理台默认在 http://localhost:15672 （guest / guest）
```

### 4. 按需覆盖连接配置（可选）

`src/main/resources/application.properties` 里所有连接信息都写成
`${环境变量:本地默认值}` 的形式，本地默认值是 `root / 123456`、`localhost`。
如果本机不同，不需要改代码，用环境变量覆盖即可：

```bash
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=你的密码

export REDIS_HOST=localhost
export RABBITMQ_HOST=localhost
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
```

### 5. 启动

```bash
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/feedback-system-1.0.0.jar
```

### 6. 访问

浏览器打开 <http://localhost:8080/>

---

## 界面

### 首页

![首页](docs/screenshots/01-home.png)

### 意见提交

![提交页](docs/screenshots/02-submit.png)

### 意见列表

![列表页](docs/screenshots/03-list.png)

---

## 测试

项目自带一套可以直接跑的自动化测试。克隆下来执行 `mvn test` 即可，
**不需要先装好并启动 MySQL、Redis、RabbitMQ**（GitHub Actions 上每次 push 也会自动跑一遍，见 `.github/workflows/maven.yml`）。

| 测试类 | 层次 | 用例数 | 说明 |
| --- | --- | --- | --- |
| `FeedbackApiIntegrationTest` | 接口集成 | 12 | MockMvc + H2，覆盖路由跳转、参数绑定、落库、MQ 消息、缓存命中与失效 |
| `FeedbackServiceImplTest` | 业务层单元 | 9 | Mockito 替身，验证创建时间补全、先落库后通知、MQ 异常被吞掉 |
| `FeedbackNoticeConsumerTest` | 消费者单元 | 4 | 畸形消息不能把异常抛给监听线程 |
| `FeedbackDefectReproductionTest` | 缺陷复现 | 7 | 真实 HTTP，把 4 处已知缺陷固化成可重复执行的证据 |

执行结果：**32 条用例全部通过**，核心业务类（Controller / Service / Consumer）行覆盖 100%。

测试是怎么做到不依赖中间件的——三样外部依赖各换成进程内等价实现，业务代码与 SQL 一行没改：

| 生产环境依赖 | 测试环境替代 |
| --- | --- |
| MySQL | H2 内存库（MySQL 兼容模式，表结构同构） |
| Redis | Spring Cache 内存实现（`RedisConfig` 标注了 `@Profile("!test")`） |
| RabbitMQ | `@MockBean RabbitTemplate`，监听容器 `auto-startup=false` |

覆盖率报告在 `mvn test` 后生成于 `target/site/jacoco/index.html`，同时归档在 [`docs/coverage/`](docs/coverage/index.html)。

延伸阅读：

- [**测试报告**](docs/测试报告.md) —— 测试范围、用例设计方法、执行结果、4 处缺陷的复现步骤与修复建议、覆盖率明细
- [**测试用例：意见提交模块**](docs/测试用例-意见提交模块.md) —— 20 条手工接口用例的完整数据与执行结论

> 关于缺陷：`FeedbackDefectReproductionTest` 里断言的是**系统当前的错误行为**，
> 所以它们现在是绿的，恰恰证明缺陷确实存在。修完代码后这些用例会立刻变红，
> 那时应把它们改写成「修复后的正确期望」，让它们从「证明缺陷存在」转为「防止缺陷回归」。

---

## 目录结构

```
feedback-system/
├── pom.xml
├── .github/workflows/maven.yml                  CI：push 后自动跑测试并归档报告
├── src/main/
│   ├── java/com/dongqiuxing/feedback/
│   │   ├── FeedbackApplication.java          启动类，@EnableCaching
│   │   ├── config/
│   │   │   ├── RedisConfig.java              RedisCacheManager + Jackson JSON 序列化
│   │   │   └── RabbitMQConfig.java           Fanout 交换器、持久化队列、绑定
│   │   ├── consumer/
│   │   │   └── FeedbackNoticeConsumer.java   @RabbitListener 消费通知
│   │   ├── controller/FeedbackController.java
│   │   ├── entity/Feedback.java
│   │   ├── mapper/FeedbackMapper.java        extends BaseMapper
│   │   └── service/
│   │       ├── FeedbackService.java
│   │       └── impl/FeedbackServiceImpl.java 缓存 + MQ 的核心逻辑
│   └── resources/
│       ├── application.properties
│       ├── sql/schema.sql                    MySQL 建表脚本
│       └── templates/{index,submit,list}.html
├── src/test/
│   ├── java/com/dongqiuxing/feedback/
│   │   ├── FeedbackApiIntegrationTest.java          接口集成测试（MockMvc）
│   │   ├── FeedbackDefectReproductionTest.java      缺陷复现测试（真实 HTTP）
│   │   ├── consumer/FeedbackNoticeConsumerTest.java
│   │   └── service/impl/FeedbackServiceImplTest.java
│   └── resources/
│       ├── application-test.properties      测试 profile：H2 + 内存缓存 + 不连 Broker
│       └── schema-h2.sql                    H2 建表脚本，与 MySQL 表结构同构
└── docs/
    ├── 测试报告.md
    ├── 测试用例-意见提交模块.md
    ├── coverage/                             JaCoCo 覆盖率报告（HTML）
    └── screenshots/
```

---

## 几个实现细节

### 缓存值改成可读 JSON

Spring Data Redis 默认的 `JdkSerializationRedisSerializer` 会把对象写成二进制，在 `redis-cli`
里 `get feedbackList::...` 得到的是一串看不懂的东西，排查缓存问题非常痛苦。所以
`RedisConfig` 里换成了 `Jackson2JsonRedisSerializer`：

```java
RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(5))
        .serializeKeysWith(... StringRedisSerializer ...)
        .serializeValuesWith(... jacksonSerializer ...)
        .disableCachingNullValues();
```

同时注册了 `JavaTimeModule`，否则实体里的 `LocalDateTime` 序列化会变成数组结构而报错。

### 缓存的粒度决定了失效方式

列表缓存以「整个 List」为一个 key，所以新增一条记录时没法增量更新，只能整体清空：

```java
@CacheEvict(value = "feedbackList", allEntries = true)
public Feedback submitFeedback(Feedback feedback) { ... }
```

### 通知失败不能影响提交

意见已经落库了，MQ 挂了不该让用户看到报错——通知属于「尽力而为」的附加动作，
所以在 `sendNotice()` 里单独 try-catch，异常只记日志：

```java
private void sendNotice(Feedback feedback) {
    try {
        ...
        rabbitTemplate.convertAndSend("feedback.exchange", "", json);
    } catch (Exception e) {
        log.error("意见通知发送失败，不影响提交结果", e);
    }
}
```

### 用 Fanout 而不是 Direct

通知是广播语义。将来要加「邮件通知」「数据统计」等订阅方时，只要新声明一个队列绑定到
`feedback.exchange` 就行，生产者的代码一行都不用改。

### 表单重定向避免重复提交

`POST /submit` 处理完返回 `redirect:/list`（Post/Redirect/Get），这样用户刷新列表页
不会把同一份意见再提交一遍。

### 怎么验证缓存真的生效了

连续访问两次 `/list`：第一次控制台会打印 `缓存未命中，回源查询数据库获取意见列表`，
第二次不会。也可以在 redis-cli 里执行 `keys *feedbackList*` 看到对应的 key。

---

## License

MIT
