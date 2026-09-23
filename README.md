# 网络五子棋

基于 JDK 21、Swing、Socket、多线程、JDBC 和 MySQL 实现的网络五子棋课程设计项目。

项目支持本地双人、人机对战和 Socket 联机对战，包含账号注册登录、在线用户大厅、随机匹配、指定用户名匹配、聊天、悔棋、对局回放、棋盘换肤、清屏动画和 MySQL 对局存档。

## 一、功能概览

- 15×15 标准图形化五子棋棋盘，鼠标点击交叉点落子
- 横、竖、正斜、反斜四个方向自动判断五连胜负
- 本地双人模式
- 玩家对电脑的人机模式，使用评分表法 AI
- 基于 TCP Socket 的双人联机对战
- 注册、登录和在线用户大厅
- 随机匹配和按用户名指定匹配
- 匹配等待 60 秒倒计时，支持取消匹配
- 联机聊天、联机悔棋和重新开始
- 对局过程保存到 `.gomoku` 回放文件并逐步骤回放
- 三种棋盘背景、清屏动画和落子重绘
- MySQL 自动建库建表，保存账号、对局、落子和聊天记录
- 提供客户端和服务器启动 BAT，支持打包为 Windows EXE

## 二、技术栈

| 技术 | 用途 |
| --- | --- |
| Java 21 | 项目开发语言和运行环境 |
| Swing / AWT / Java2D | 棋盘、棋子、窗口和动画绘制 |
| Socket | 客户端与服务器之间的网络通信 |
| 多线程 | 网络读写、AI 计算、数据库保存和计时器 |
| JDBC / MySQL | 账号、对局、落子和聊天数据存储 |
| Gradle | 离线构建和 EXE 打包任务 |
| jpackage | 生成 Windows 客户端和服务器 EXE |

统一包名为：

```text
io.github.tissyboxc.gomoku
```

主要子包包括 `ai`、`core`、`database`、`model`、`network`、`player`、`replay`、`server` 和 `ui`。

## 三、环境要求

- Windows 10 或 Windows 11
- JDK 21，建议配置 `JAVA_HOME`
- MySQL 8.0 或更高版本
- IntelliJ IDEA
- Gradle 9.4.1，本项目使用本地目录：

```text
D:\service\gradle-9.4.1
```

离线仓库目录为：

```text
D:\service\gradle-9.4.1\gradle_reop
```

项目已经将 MySQL JDBC 驱动放在 `libs` 目录，并使用项目内的 Gradle Wrapper JAR，因此不需要联网下载依赖。

## 四、数据库配置

默认配置文件：

```text
src/main/resources/database.properties
```

默认内容：

```properties
database.enabled=true
database.host=localhost
database.port=3306
database.name=gomoku
database.user=root
database.password=root
```

如果本机 MySQL 配置不同，修改用户名和密码即可。程序启动时会自动：

1. 加载 `com.mysql.cj.jdbc.Driver`。
2. 连接本机 MySQL。
3. 创建 `gomoku` 数据库。
4. 执行 `src/main/resources/sql/init.sql`。
5. 创建账号表、对局表、落子表和聊天表。

如果不希望使用数据库，可以设置为：

```properties
database.enabled=false
```

关闭数据库后，本地双人、人机和联机对战仍可使用，但对局记录不会入库。

## 五、构建过程

所有命令都在项目根目录执行：

```text
D:\service\AAA-JAVA\gomoku
```

### 1. 完整离线构建

```bat
D:\service\gradle-9.4.1\bin\gradle.bat --offline --no-daemon clean build
```

构建成功后会生成：

```text
build\libs\gomoku-1.0.0.jar
```

### 2. 只做编译检查

```bat
D:\service\gradle-9.4.1\bin\gradle.bat --offline --no-daemon compileJava
```

### 3. 生成客户端和服务器 EXE

```bat
D:\service\gradle-9.4.1\bin\gradle.bat --offline --no-daemon clean build packageExe packageServerExe
```

生成结果：

```text
build\package\Gomoku\Gomoku.exe
build\package-server\GomokuServer\GomokuServer.exe
```

也可以直接双击项目根目录的 `build-exe.bat` 完成同样的构建。

### 4. IDEA 构建

1. 用 IDEA 打开 `D:\service\AAA-JAVA\gomoku`。
2. 等待 IDEA 识别 `build.gradle`。
3. 将 Project SDK 设置为 JDK 21。
4. 使用 IDEA 的 Gradle 工具窗口执行 `build`。
5. 主类为 `io.github.tissyboxc.gomoku.Main`。

服务端运行参数：

```text
server 9527
```

## 六、启动方式

### 方式一：BAT 启动

先构建一次，然后双击：

- `start-server.bat`：启动服务器，默认监听 `9527`
- `start-client.bat`：启动图形客户端

### 方式二：EXE 启动

构建 EXE 后双击：

- 客户端：`build\package\Gomoku\Gomoku.exe`
- 服务器：`build\package-server\GomokuServer\GomokuServer.exe`

服务器 EXE 已带控制台窗口，关闭控制台即停止服务器。

### 方式三：命令行启动

服务器：

```bat
java -Dfile.encoding=UTF-8 -cp "build\libs\gomoku-1.0.0.jar;libs\*" io.github.tissyboxc.gomoku.Main server 9527
```

客户端：

```bat
java -Dfile.encoding=UTF-8 -cp "build\libs\gomoku-1.0.0.jar;libs\*" io.github.tissyboxc.gomoku.Main
```

## 七、使用说明

### 1. 本地双人

点击“本地双人”，黑棋先手。两名玩家轮流点击棋盘交叉点落子，出现五连后自动显示胜负和获胜连线。

### 2. 人机对战

点击“人机对战”，玩家执黑先手，电脑执白。

电脑会优先选择直接获胜点，其次阻挡玩家的立即获胜点，最后使用棋型评分表在已有棋子附近选择最优位置。AI 计算在后台线程执行，不会阻塞 Swing 界面。

### 3. 注册和登录

1. 启动服务器。
2. 启动客户端，客户端会自动尝试连接 `127.0.0.1:9527`。
3. 在右侧“联机大厅”输入用户名和密码。
4. 第一次使用点击“注册”，注册成功后点击“登录”。
5. 登录成功后可在“在线用户”一栏查看当前大厅用户。

如果服务器未启动，客户端会显示连接失败，但不会卡死。启动服务器后点击“连接服务器”或“重连服务器”即可。

### 4. 随机匹配

登录后点击“随机匹配”，服务器把当前等待队列中的两名玩家配对。匹配等待时间为 60 秒，界面会显示倒计时。等待期间可点击“取消匹配”。

### 5. 指定用户名匹配

在“指定用户”输入对方用户名，点击“指定匹配”。对方会收到邀请，接受后双方进入同一房间。不能和自己匹配，也不支持离线用户。

### 6. 联机对战操作

- 棋盘由服务器作为权威状态。
- 轮到自己时点击交叉点落子。
- 右侧聊天框发送文字，消息会实时显示在双方聊天区。
- 点击“申请悔棋”发送请求，对方同意后回退一步。
- 点击“重新开始”发送重新开始请求。
- 对手离开房间时，客户端会结束当前联机对局。

### 7. 切换模式

切换到“本地双人”或“人机对战”不会退出登录，也不会关闭服务器连接。程序只会取消正在进行的匹配，并通知当前对手退出联机房间。切回“联机对战”后可直接重新匹配。

### 8. 更换背景和清屏

点击“更换背景”可以选择“原木”“青玉”“海蓝”三种棋盘主题。

点击“清屏动画”播放清屏扫光效果。联机模式下会发送重新开始请求，由双方确认。

### 9. 保存和打开回放

点击“保存回放”，文件保存到项目运行目录的 `replays` 文件夹，扩展名为 `.gomoku`。

点击“打开回放”选择文件，回放窗口支持：

- 第一步
- 上一步
- 下一步
- 最后一步
- 自动播放

## 八、数据库表说明

| 表名 | 用途 |
| --- | --- |
| `player_account` | 用户名、密码摘要、盐值和最近登录时间 |
| `game_record` | 对局编号、模式、双方名称、胜者、步数和时间 |
| `game_move` | 每一步的行列坐标、棋色和时间 |
| `chat_message` | 联机聊天发送者、内容和时间 |

密码使用 SHA-256 加随机盐保存，不保存明文密码。

## 九、项目目录

```text
gomoku
├─ build-exe.bat
├─ start-client.bat
├─ start-server.bat
├─ build.gradle
├─ gradle/wrapper
├─ libs
│  └─ mysql-connector-j-9.7.0.jar
├─ docs
│  ├─ 用户使用文档.md
│  ├─ 开发文档.md
│  └─ 功能架构图.md
├─ replays
│  └─ .gitkeep
└─ src/main
   ├─ java/io/github/tissyboxc/gomoku
   │  ├─ ai
   │  ├─ core
   │  ├─ database
   │  ├─ model
   │  ├─ network
   │  ├─ player
   │  ├─ replay
   │  ├─ server
   │  └─ ui
   └─ resources
      ├─ database.properties
      └─ sql/init.sql
```

## 十、常见问题

### 数据库初始化失败

检查 MySQL 服务是否启动、端口是否为 `3306`、用户名和密码是否正确，以及当前账号是否有创建数据库和表的权限。

### 提示找不到 MySQL JDBC 驱动

确认以下文件存在：

```text
libs\mysql-connector-j-9.7.0.jar
```

使用 EXE 时不需要额外安装驱动，驱动已经打包在 `app` 目录中。

### 客户端连接失败

确认服务器已启动并监听 `9527`，检查防火墙、IP 地址和端口是否可访问。两台电脑测试时，服务器地址填写运行服务器电脑的局域网 IP。

### 端口被占用

关闭占用 `9527` 的程序，或修改启动参数中的端口号，客户端连接时填写相同端口。

### 本地和远程 Git 提交冲突

不要把 `D:\service\AAA-JAVA` 当作 Git 根目录。项目 Git 仓库根目录应为：

```text
D:\service\AAA-JAVA\gomoku
```

构建产物、IDE 缓存、回放文件和 EXE 不应提交到 Git。

## 十一、离线构建说明

本项目禁止联网下载依赖，统一使用：

```text
D:\service\gradle-9.4.1\bin\gradle.bat --offline
```

`build.gradle` 使用项目内的 `libs` 目录直接加载 JDBC 驱动，不依赖 Maven 远程仓库。

常用命令：

```bat
D:\service\gradle-9.4.1\bin\gradle.bat --offline --no-daemon clean build
D:\service\gradle-9.4.1\bin\gradle.bat --offline --no-daemon packageExe packageServerExe
```

如果 Gradle 提示依赖未缓存，先检查本机 Gradle 离线仓库和项目 `libs` 目录，不要改为联网下载。
