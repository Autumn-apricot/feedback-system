package com.dongqiuxing.feedback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缺陷复现测试（真实 HTTP，RANDOM_PORT 启动内嵌 Tomcat）
 *
 * <p>编号约定：用例编号 DEF-xx（Defect Reproduction），缺陷编号 BUG-xx。
 * 一条缺陷可能由多条用例从不同角度复现（例如「必填校验缺失」用空值、全空格、
 * content 为空三条用例分别验证）。完整缺陷清单见 docs/测试报告.md。</p>
 *
 * <p>与 {@link FeedbackApiIntegrationTest} 的区别：这里不通过 MockMvc 模拟请求，
 * 而是真的把应用跑起来发 HTTP 请求，因此能观察到未经任何包装的真实响应——
 * 尤其是「没有全局异常处理时，服务端异常最终表现为 HTTP 500」这件事，
 * 用 MockMvc 很难稳定断言。</p>
 *
 * <p><b>重要：这些用例断言的是「系统当前的错误行为」</b>，用来把缺陷固化成可重复执行的证据，
 * 而不是断言正确的期望值。所以它们现在是绿的，恰恰说明缺陷确实存在。
 * 一旦按修复建议改完代码（加参数校验与全局异常处理、给 /list 加访问控制），
 * 这些用例会立刻变红——那时应当把它们反过来改写成「修复后的正确期望」，
 * 这正是这批用例的价值：它们把「缺陷是否真的修好了」变成了可自动验证的事。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("意见反馈系统 · 缺陷复现测试（真实 HTTP）")
class FeedbackDefectReproductionTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("delete from feedback");
    }

    // ------------------------------------------------------------------
    // BUG-01 缺少必填校验：空值 / 全空格 / 内容为空都能提交成功
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DEF-01【BUG-01】用户名为空仍能提交成功并入库（期望：应被必填校验拦截）")
    void 用户名为空仍能提交成功() {
        ResponseEntity<String> response = submit("", "空用户名也不该提交成功");

        assertThat(response.getStatusCode().isError())
                .as("期望服务端返回 4xx 并提示「姓名不能为空」，实际却接受了这次提交")
                .isFalse();
        assertThat(rowCountByContent("空用户名也不该提交成功"))
                .as("期望数据库不新增记录，实际却写进去了")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DEF-02【BUG-01】用户名全为空格仍能提交成功并入库（期望：应被当作空值拦截）")
    void 用户名全空格仍能提交成功() {
        ResponseEntity<String> response = submit("   ", "全空格用户名也不该提交成功");

        assertThat(response.getStatusCode().isError())
                .as("期望服务端返回 4xx，实际却接受了这次提交")
                .isFalse();
        assertThat(rowCountByContent("全空格用户名也不该提交成功")).isEqualTo(1);
    }

    @Test
    @DisplayName("DEF-03【BUG-01】意见内容为空仍能提交成功并入库（期望：应被必填校验拦截）")
    void 意见内容为空仍能提交成功() {
        ResponseEntity<String> response = submit("张三", "");

        assertThat(response.getStatusCode().isError())
                .as("期望服务端返回 4xx，实际却接受了这次提交")
                .isFalse();
        assertThat(rowCountByValue("username", "张三")).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // BUG-02 长度上限未校验，数据库异常直接暴露
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DEF-04【BUG-02】用户名 300 字符超过 varchar(50) → 返回 500 而不是友好提示")
    void 用户名超长返回500() {
        ResponseEntity<String> response = submit(repeat("测", 300), "用户名超长");

        assertThat(response.getStatusCode())
                .as("期望：被长度校验拦截并提示「姓名最多 50 个字符」；"
                        + "实际：数据库层异常无统一处理，直接返回 500")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("DEF-05【对照】content 为 TEXT 类型，5000 字符可正常提交（说明限长只针对 username）")
    void 意见内容超长可正常提交() {
        String longContent = repeat("很长的意见内容", 800);

        ResponseEntity<String> response = submit("张三", longContent);

        assertThat(response.getStatusCode().isError())
                .as("content 是 TEXT（上限远大于 varchar(50)），因此这里不该报错")
                .isFalse();
        assertThat(rowCountByContent(longContent)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // BUG-03 缺少全局异常处理，参数缺失也返回 500
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DEF-06【BUG-03】表单缺少 username 参数 → 返回 500 而不是友好提示")
    void 缺少必填参数返回500() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("content", "缺少 username 参数");

        ResponseEntity<String> response = restTemplate.postForEntity("/submit", form, String.class);

        assertThat(response.getStatusCode())
                .as("期望：参数缺失返回 400 且给出可读提示；实际：非空约束异常直接抛成 500")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ------------------------------------------------------------------
    // BUG-04 列表页没有访问控制
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DEF-07【BUG-04】未登录可直接访问 /list，看到全部意见原文（期望：需要登录或权限校验）")
    void 列表页无访问控制() {
        jdbcTemplate.update("insert into feedback(username, content, create_time) values (?,?,?)",
                "匿名员工", "这条内容本不该对所有人公开", Timestamp.valueOf(LocalDateTime.now()));

        ResponseEntity<String> response = restTemplate.getForEntity("/list", String.class);

        assertThat(response.getStatusCode())
                .as("期望：未登录时跳转登录页或返回 401/403")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("期望：敏感内容不应直接暴露，实际却在响应体里返回了")
                .contains("这条内容本不该对所有人公开");
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private ResponseEntity<String> submit(String username, String content) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("content", content);
        return restTemplate.postForEntity("/submit", form, String.class);
    }

    private Integer rowCountByContent(String content) {
        return jdbcTemplate.queryForObject(
                "select count(*) from feedback where content = ?", Integer.class, content);
    }

    private Integer rowCountByValue(String column, String value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from feedback where " + column + " = ?", Integer.class, value);
    }

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }
}
