# 配置文件与现场测试

现在默认直接读取 YAML 配置文件。在 IDEA 中运行对应类即可，不需要设置系统 PATH、环境变量或 VM 启动参数。IDE 使用项目已有的 JDK 17/Maven 配置；工作目录保持项目根目录。

## 1. 启动后端：填写 application-local.yml

`Application.main` 启动整个后端，需要数据库与 JWT 配置。开发环境会自动读取项目根目录 `application-local.yml`，不再强制要求 `JWT_SECRET`、`DB_URL` 等环境变量。

本机文件已经准备好。新环境可复制根目录 `application-local.example.yml` 为 `application-local.yml`，直接填写：

| 配置项 | 填写内容 |
|---|---|
| `spring.datasource.url` | 自己实际的数据库地址与库名 |
| `spring.datasource.username` | 数据库账号 |
| `spring.datasource.password` | 数据库密码 |
| `jwt.secret` | 至少32字节、固定保存的私有随机密钥 |

本机已生成并写入随机 JWT 密钥，无需额外设置；模板中的数据库名 `smartwash_cloud` 是示例，需按实际数据库修改。此文件已被 Git 忽略，不提交真实密码和密钥。数据库仍需事先建立并按数据库说明准备表结构；应用不自动建库或执行迁移。

填写后，在 IDEA 中直接运行 `Application`。只做下面的 PLC 采集时，无需运行 `Application`，也不需要数据库或 JWT。

## 2. 现场测试：填写 field-config 下的两个文件

已准备以下本地文件：

| 文件 | 作用 |
|---|---|
| `field-config/field-test.yml` | 测试设备编号、采集时长、保存目录、测试库连接和接口账号 |
| `field-config/plc.yml` | PLC 地址、站号、点位地址、类型、字序、倍率、单位及业务编号 |

新环境可将 `tools/field/field-test.example.yml`、`src/main/resources/plc-points.example.yml` 分别复制到上述位置。所有相对路径以项目根目录为基准，真实文件和默认 `field-results/` 均被 Git 忽略。

点位文件已按最新PLC资料整理为48个读取项及4个写入配置，按系统、四台绞机、故障明细、远程写入分组。中文说明全部为 `#` 注释，不作为配置字段读入Java或采集文件。新状态、独立方向、编码器和轴计时可记录到采集文件，实时业务快照的相应转换仍需后续接入；高度/速度不能直接拿旧模板寄存器替代。

`field-test.yml` 的主要内容：

```yaml
field:
  device-id: plc-001
  duration-seconds: 60
  output-dir: field-results
  capture-dir: ""  # 历史验证/回放时填上一次采集的实际目录
  plc:
    config: field-config/plc.yml
  history:
    datasource:
      url: jdbc:mysql://127.0.0.1:3306/smartwash_field_test?serverTimezone=Asia/Shanghai
      username: root
      password: ""
  api:
    base-url: http://127.0.0.1:8080
    username: ""
    password: ""
    token: ""
```

按要运行的测试填写相关项即可。单独采集 PLC 时，只读取设备编号、采集时长、保存目录及点位文件，不要求填写接口账号和数据库密码。

## 3. 在 IDEA 直接运行对应测试类

**PLC采集也提供普通Java main入口：打开 `src/test/java/com/company/project/field/PlcFieldCapture.java`，点击 `public static void main(String[] args)` 左侧绿色三角形，选择 Run。** 参数自动读取前述两个配置文件，完成后输出保存目录；离线或无效点位会明确失败并保留记录。不需要先启动 `Application`。Java使用 `void main`，不使用 `int main`。

下面保留JUnit测试入口，便于运行测试和查看断言结果：

| 测试类 | 操作与结果 |
|---|---|
| `PlcFieldCaptureTest` | 连接真实 PLC，采集实时点位，保存 CSV 和完整数据帧 |
| `FieldHistoryPersistenceTest` | 读取已采集的 `realtime.ndjson`，验证现有历史保存器落库、读回、重复提交及事务回滚 |
| `FieldApiSmokeTest` | 对已启动后端检查登录、实时数据、参数读取、过程统计和可选的既有命令查询 |
| `PlcFieldReplayTest` | 读取现场 `snapshots.ndjson`，经过当前业务转换重新输出，供回来后复测 |

打开测试类，点击类或测试方法旁的绿色运行按钮即可。原先的 `field.plc.enabled`、`field.history.enabled` 等启用参数已移除，不用在运行配置里添加 `-D` 参数。

**现场第一步运行 `PlcFieldCapture.main`，或JUnit入口 `PlcFieldCaptureTest`：**

1. 在 `plc.yml` 中将 `cleaning.plc.enabled` 和指定设备的 `enabled` 设为 `true`，填写实际设备 `id`、PLC IP 和站号；`field-test.yml` 的 `device-id` 必须一致。
2. 保持 `writes-enabled: false`，只启用核对过的读取点。填写实际 Modbus 零基地址、功能码、类型、字序、倍率、偏移、有效极性、单位及业务编号。未知点位继续禁用，M/D 标签的数字不能直接当成协议地址。
3. 在 IDEA 运行 `PlcFieldCapture.main` 或 `PlcFieldCaptureTest`，对照 PLC 监视或现场仪表核对 CSV 的值和单位。未填写完整的模板会明确报配置错误。

默认采集60秒，沿用配置中的轮询间隔、整帧超时及驱动重连退避。控制台打印 `field-results/plc-<编号>/`：

| 文件 | 内容 |
|---|---|
| `manifest.json` | 本次点位映射、连接参数与采集说明 |
| `points.csv` | 逐帧逐点的地址、类型、字序、倍率、单位、值、在线与有效性 |
| `snapshots.ndjson` | 实际驱动解码后的完整 PLC 帧；不是原始 Modbus 报文字节 |
| `realtime.ndjson` | 经真实表示层、大厅、清洗业务转换得到的快照，保留缺测和有效性 |
| `summary.json` | 总帧数、完整/离线帧数、各点有效/无效次数和最大/最小/最后值 |

离线、启用点无效或未采到帧都会失败并保留记录。每帧写完即刷新文件；正常结束或可捕获异常会保存总结。带回整个目录，供离线排查。通过只证明已配置点位能读取，物理量正确性仍需现场对照仪表核验。

## 4. 历史保存、接口与离线回放

历史测试前，将 `field.capture-dir` 填为完整的现场目录，例如 `field-results/plc-实际编号`，并填写 `field.history.datasource` 的专用测试库连接。测试库需已具备三张 InnoDB 旧表：`cleaning_telemetry_batch`、`cleaning_telemetry_segment`、`cleaning_segment_measurement`。工具不建表、不迁移、不清库。

历史测试每100帧组批，经真实统计保存器和带事务代理的 DAO 写入。新连接读回核对身份、序号、时间、数值精度，再验证重复批次不增行、故障批次全部回滚。测试数据以唯一 `field-` 会话/批次保留，轮次为0、触发原因 `FIELD_CAPTURE`；总结在 `field-results/history-field-<编号>/summary.json`。旧 DAO 支持的索张力、单机速度/转矩及有效圆环高度进入旧表，其他点位仍保留在原文件。无有效测量时在连库前失败。

接口测试需要已运行且正在采集 PLC 的后端。在 `field.api` 中直接填写后端地址、账号密码，或填写访问 `token`。默认读取3次实时数据，检查鉴权、设备身份、在线状态、测量有效性、采样新鲜度及序号推进；结果在 `field-results/api-<时间>-<编号>/`。登录凭据不写报告，不提交急停/参数修改；`request-id` 留空则不查询命令。

回来后运行 `PlcFieldReplayTest`，只需填 `field.capture-dir`。它不连接 PLC/数据库，将当前业务转换结果写入新建的 `field-results/replay-<编号>/`，不覆盖原文件；空文件、损坏行、缺少身份/时间、序号重复/倒退、时间倒退和混入其他设备均会失败。结合现场原 `realtime.ndjson` 对照，再为发现的业务问题补具体断言。

当前生产代码仍不会从实时帧自动生成正式上行历史。`FIELD_CAPTURE` 只验证已有保存器；`statistics` 的 `scope=PROCESS` 也不是数据库落库证明。正式上行/轮次识别和新历史表生产保存链路另行接入。

## 5. 本机回归与可选命令行

普通 `mvn test` 默认排除上述四个现场入口，并关闭完整应用测试中的 PLC 后台采集和历史自动保存；IDE 明确运行现场测试或 Maven 指定该类才执行现场工具。`ApplicationTests`、`RealtimePushIntegrationTest` 使用完整应用环境，另需应用配置；本机离线回归脚本会排除它们及现场入口。

命令行仅为可选方式，参数也从同一配置文件读取：

```powershell
.\tools\field\Invoke-FieldTest.ps1 -Mode Capture
.\tools\field\Invoke-FieldTest.ps1 -Mode History
.\tools\field\Invoke-FieldTest.ps1 -Mode Api
.\tools\field\Invoke-FieldTest.ps1 -Mode Replay
.\tools\field\Invoke-FieldTest.ps1 -Mode Local
```

也可直接用 Maven，例如 `mvn '-Dtest=PlcFieldCaptureTest' test`。CLI 需要可执行的 Maven；IDE 使用自己已有的 JDK/Maven 配置，不要求给操作系统添加 PATH。既有脚本参数仍可作为可选覆盖，默认不覆盖 YAML 中的值。
