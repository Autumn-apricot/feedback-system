# Postman 接口测试集合 · 使用说明

本目录存放「企业内部意见反馈系统」的 Postman 接口测试集合。

## 文件

| 文件 | 说明 |
| --- | --- |
| `feedback-system.postman_collection.json` | Postman Collection v2.1，含 16 条接口用例 |

## 导入方式

1. 打开 Postman → 左上角 **Import**
2. 选择本目录下的 `feedback-system.postman_collection.json`
3. 导入后在 Collection 的 **Variables** 页签确认 `baseUrl`（默认 `http://localhost:8080`）

## 前置条件

```bash
# 1. 启动中间件（MySQL / Redis / RabbitMQ，详见仓库根目录 README）
# 2. 启动应用
./mvnw spring-boot:run     # Windows: mvnw.cmd spring-boot:run
```

应用默认监听 `8080`；若改过端口，请同步修改集合变量 `baseUrl`。

## 用例分组

### 01 正常流程（4 条）
| 用例 | 请求 | 预期 |
| --- | --- | --- |
| TC-001 | `GET /` | 200，返回首页 HTML |
| TC-002 | `GET /submit` | 200，含 username / content 表单 |
| TC-003 | `POST /submit` | 302，Location → `/list` |
| TC-004 | `GET /list` | 200，列表含 TC-003 提交的内容 |

### 02 输入校验 · 缺陷复现（7 条）
> ⚠️ **这一组的断言写的是「应有行为」，因此断言失败 = 缺陷复现成功。**
> 请在 Collection Runner 结果中对照阅读，不要误判为「测试写错了」。

| 用例 | 缺陷 | 预期（应有行为） | 实测 |
| --- | --- | --- | --- |
| TC-005 | BUG-001 | 4xx 拒绝 | 302 入库 ✗ |
| TC-006 | BUG-001 | 4xx 拒绝 | 302 入库 ✗ |
| TC-007 | BUG-001 | 4xx 拒绝 | 302 入库 ✗ |
| TC-009 | BUG-002 | 4xx / 不泄露 SQL 异常 | 500 ✗ |
| TC-010 | BUG-003 | 4xx | 500 ✗ |
| TC-011 | BUG-003 | 4xx（415 / 400） | 500 ✗ |
| TC-012 | BUG-004 | 302 / 401 / 403 | 200 明文返回 ✗ |
| TC-008 | — | 302（上界值应通过） | 302 ✓ |

### 03 边界与异常（5 条）
| 用例 | 场景 | 预期 |
| --- | --- | --- |
| TC-008 | username = 50 字符（边界上界） | 302 成功 |
| TC-013 | content = 长文本 | 302 成功（text 类型上限 65535 字节） |
| TC-014 | 中文 + emoji 混排 | 302，列表页无乱码 |
| TC-015 | 访问不存在路径 | 404 |
| TC-016 | GET 访问 `/submit` | 200 表单页（设计如此，仅记录） |

## 缺陷清单（与 `docs/测试报告.md` 一致）

| 编号 | 描述 | 等级 |
| --- | --- | --- |
| BUG-001 | 必填项无校验，空值与纯空格均可入库 | 一般 |
| BUG-002 | `username` 超长（>50）返回 500 —— 字段为 `varchar(50)` | 严重 |
| BUG-003 | 参数缺失 / 非表单格式提交返回 500，无全局异常处理 | 严重 |
| BUG-004 | `/list` 无访问控制，未登录可看全部反馈 | 严重 |

## 两个容易踩的坑

1. **`content` 是 `text`（上限 65535 字节），`username` 是 `varchar(50)`。**
   写「超长输入」用例前必须先查表结构：`DESCRIBE feedback_db.feedback;`
   —— content 写 5000 字符不会报错，username 写 51 字符才会。

2. **内容是原样入库，不存在存储型 XSS。**
   Thymeleaf 用 `th:text` 默认转义，列表页输出的是 `&lt;script&gt;`。
   判断 XSS 风险要看**输出是否转义**，而不是输入是否含 `<script>`。
