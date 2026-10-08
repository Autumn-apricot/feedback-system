package com.dongqiuxing.feedback;

import com.dongqiuxing.feedback.entity.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 意见反馈系统 —— 接口集成测试（MockMvc）
 *
 * <p>测试范围：Controller 路由与视图跳转、表单参数绑定、Service 业务逻辑、
 * MyBatis-Plus 落库、Spring Cache 缓存与失效、RabbitMQ 消息发送。
 * 数据库换成 H2 内存库、缓存换成内存实现、MQ 发送端由 Mock 接管，
 * 因此无需启动任何外部中间件即可运行（详见 application-test.properties）。</p>
 *
 * <p>断言只针对「可观察的外部行为」——HTTP 状态码、重定向地址、视图名、模型数据、
 * 页面渲染结果、数据库最终状态、发出去了什么消息，不去断言内部实现细节。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("意见反馈系统 · 接口集成测试（MockMvc）")
class FeedbackApiIntegrationTest {

    private static final String CACHE_NAME = "feedbackList";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    /** 替换真实 RabbitMQ 客户端，避免测试时连接 Broker，同时可直接断言发出去的消息 */
    @MockBean
    private RabbitTemplate rabbitTemplate;

    /** 每个用例前清空数据表与缓存，保证用例之间互不影响、可独立重复执行 */
    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("delete from feedback");
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    // ------------------------------------------------------------------
    // 一、页面路由
    // ------------------------------------------------------------------

    @Test
    @DisplayName("IN-01 首页可访问，返回 index 视图且渲染出系统名称")
    void 首页可访问() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("企业内部意见反馈系统")));
    }

    @Test
    @DisplayName("IN-02 提交页可访问，并已向模型中注入空的表单对象")
    void 提交页可访问并注入表单对象() throws Exception {
        mockMvc.perform(get("/submit"))
                .andExpect(status().isOk())
                .andExpect(view().name("submit"))
                .andExpect(model().attributeExists("feedback"))
                .andExpect(model().attribute("feedback", new Feedback()))
                .andExpect(content().string(containsString("提交您的意见")));
    }

    @Test
    @DisplayName("IN-03 不存在的路径返回 404")
    void 不存在的路径返回404() throws Exception {
        mockMvc.perform(get("/not-exist-path"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // 二、意见提交（正常流程）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("IN-04 合法提交后重定向到列表页（Post/Redirect/Get）")
    void 合法提交后重定向到列表页() throws Exception {
        mockMvc.perform(post("/submit")
                        .param("username", "张三")
                        .param("content", "希望增加班车班次"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/list"));
    }

    @Test
    @DisplayName("IN-05 合法提交的数据被正确落库，且创建时间被自动补全")
    void 合法提交数据被正确落库() throws Exception {
        LocalDateTime before = LocalDateTime.now();

        mockMvc.perform(post("/submit")
                        .param("username", "张三")
                        .param("content", "希望增加班车班次"))
                .andExpect(status().is3xxRedirection());

        LocalDateTime after = LocalDateTime.now();

        Feedback saved = jdbcTemplate.queryForObject(
                "select id, username, content, create_time from feedback where username = ?",
                (rs, rowNum) -> {
                    Feedback f = new Feedback();
                    f.setId(rs.getInt("id"));
                    f.setUsername(rs.getString("username"));
                    f.setContent(rs.getString("content"));
                    f.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                    return f;
                },
                "张三");

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).as("主键应由数据库自增回填").isPositive();
        assertThat(saved.getContent()).isEqualTo("希望增加班车班次");
        assertThat(saved.getCreateTime())
                .as("创建时间应落在本次请求的时间窗口内")
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    @Test
    @DisplayName("IN-06 提交成功后会向 feedback.exchange 发出 JSON 通知消息")
    void 提交成功后发送MQ通知() throws Exception {
        mockMvc.perform(post("/submit")
                        .param("username", "李娜")
                        .param("content", "建议错峰就餐"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("feedback.exchange"), eq(""), payload.capture());

        String json = String.valueOf(payload.getValue());
        assertThat(json).as("通知体应为 JSON，且包含提交人与意见内容")
                .contains("\"username\":\"李娜\"")
                .contains("\"content\":\"建议错峰就餐\"")
                .contains("createTime");
    }

    @Test
    @DisplayName("IN-07 提交后列表页立即可见该条意见（缓存已被清除）")
    void 提交后列表页立即可见() throws Exception {
        // 先访问一次列表，让列表缓存建立起来
        mockMvc.perform(get("/list")).andExpect(status().isOk());

        mockMvc.perform(post("/submit")
                        .param("username", "王强")
                        .param("content", "办公区空调温度偏低"));

        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("王强")))
                .andExpect(content().string(containsString("办公区空调温度偏低")));
    }

    // ------------------------------------------------------------------
    // 三、列表查询与缓存
    // ------------------------------------------------------------------

    @Test
    @DisplayName("IN-08 列表页渲染出提交人、意见内容与提交时间")
    void 列表页渲染出完整字段() throws Exception {
        jdbcTemplate.update("insert into feedback(username, content, create_time) values (?,?,?)",
                "赵敏", "建议增加线上培训课程", Timestamp.valueOf(LocalDateTime.of(2026, 10, 8, 9, 30, 0)));

        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(view().name("list"))
                .andExpect(model().attributeExists("feedbacks"))
                .andExpect(content().string(containsString("赵敏")))
                .andExpect(content().string(containsString("建议增加线上培训课程")))
                .andExpect(content().string(containsString("2026-10-08 09:30:00")));
    }

    @Test
    @DisplayName("IN-09 无数据时列表页展示空状态而不是报错")
    void 无数据时展示空状态() throws Exception {
        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("还没有意见反馈")));
    }

    @Test
    @DisplayName("IN-10 列表查询命中缓存：绕过 Service 直接改库，缓存失效前页面看不到新数据")
    void 列表查询命中缓存() throws Exception {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).as("应存在名为 feedbackList 的缓存").isNotNull();

        // 第一次访问：回源数据库并写入缓存
        mockMvc.perform(get("/list")).andExpect(status().isOk());
        assertThat(cache.get(SimpleKey.EMPTY))
                .as("首次查询后应把列表写入缓存")
                .isNotNull();

        // 绕过 Service 直接插库（不经过 @CacheEvict），模拟「缓存还没过期但库已变」
        jdbcTemplate.update("insert into feedback(username, content, create_time) values (?,?,?)",
                "越权插入", "这条数据在缓存过期前不该出现", Timestamp.valueOf(LocalDateTime.now()));

        // 第二次访问：响应来自缓存，因此看不到刚插入的数据 —— 证明缓存确实生效
        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("这条数据在缓存过期前不该出现"))));

        // 提交一条意见，触发 @CacheEvict 清空列表缓存
        mockMvc.perform(post("/submit")
                        .param("username", "缓存测试")
                        .param("content", "触发缓存失效"))
                .andExpect(status().is3xxRedirection());
        assertThat(cache.get(SimpleKey.EMPTY))
                .as("提交后应清空列表缓存")
                .isNull();

        // 再次访问：回源数据库，两条数据都应出现
        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("这条数据在缓存过期前不该出现")))
                .andExpect(content().string(containsString("触发缓存失效")));
    }

    // ------------------------------------------------------------------
    // 四、数据边界（正常一侧）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("IN-11 用户名取上边界 50 个字符可正常提交")
    void 用户名取上边界50字符可提交() throws Exception {
        String fiftyChars = repeat("测", 50);

        mockMvc.perform(post("/submit")
                        .param("username", fiftyChars)
                        .param("content", "边界值测试"))
                .andExpect(status().is3xxRedirection());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from feedback where username = ?", Integer.class, fiftyChars);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("IN-12 意见内容超长（5000 字符）可正常提交，因为 content 是 TEXT 类型")
    void 意见内容超长可提交() throws Exception {
        String longContent = repeat("很长的意见内容", 800); // 约 5600 字符

        mockMvc.perform(post("/submit")
                        .param("username", "张三")
                        .param("content", longContent))
                .andExpect(status().is3xxRedirection());

        Integer len = jdbcTemplate.queryForObject(
                "select char_length(content) from feedback where username = ?", Integer.class, "张三");
        assertThat(len).isEqualTo(longContent.length());
    }

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }
}
