# autoresearch-crypto-java Wiki

> 自主加密货币量化交易策略研究框架 — Java 实现

## 项目简介

`autoresearch-crypto-java` 是 autoresearch-crypto 框架的 Java 版本，提供高性能的加密货币量化交易策略研究、回测和实盘交易能力。基于 Java 17 构建，使用 Maven 进行依赖管理。

## 核心能力

| 模块 | 说明 |
|------|------|
| **10 种内置策略** | 趋势跟踪、均值回归、剥头皮、网格、混合动量、自适应等 |
| **回测引擎** | 高保真模拟真实交易，支持手续费/滑点 |
| **实盘交易** | Nado DEX 和 OKX 双交易所连接器 |
| **ATLAS 进化** | 多 Agent 竞争性进化引擎 |
| **GEPA 反思** | 科学方法驱动的反思式学习引擎 |
| **市场机制检测** | 自动分类趋势/震荡/高波动状态 |
| **多时间框架分析** | 跨 15m/1h/4h/1d 集成信号 |
| **风险评分** | 多维度风险调整综合评分 |

## 技术栈

- **Java 17** + Maven
- **Jackson** — JSON 序列化
- **OkHttp** — HTTP 客户端
- **Commons Math 3** — 统计计算
- **SLF4J + Logback** — 日志
- **JUnit 5 + AssertJ** — 测试

## Wiki 目录

- [架构概览](Architecture.md)
- [快速开始](Getting-Started.md)
- [用户使用指南](User-Guide.md)
- [交易策略](Strategies.md)
- [回测引擎](Backtesting.md)
- [实盘交易](Live-Trading.md)
- [进化引擎](Evolution-Engines.md)
- [技术指标](Technical-Indicators.md)
- [市场机制检测](Market-Regime-Detection.md)
- [配置参考](Configuration-Reference.md)
- [API 参考](API-Reference.md)

## 项目结构

```
autoresearch-crypto-java/
├── pom.xml
└── src/
    ├── main/java/com/autoresearch/crypto/
    │   ├── backtest/        # 回测引擎
    │   ├── config/          # 全局配置
    │   ├── data/            # 数据管理
    │   ├── evolution/       # 进化引擎（ATLAS + GEPA）
    │   ├── indicator/       # 技术指标库
    │   ├── live/            # 实盘交易引擎
    │   ├── multitf/         # 多时间框架分析
    │   ├── regime/          # 市场机制检测
    │   ├── scoring/         # 风险评分
    │   ├── search/          # 策略搜索
    │   └── strategy/        # 策略框架
    │       └── impl/        # 10种策略实现
    └── test/                # 单元测试
```

## 免责声明

> 本项目仅供研究和教育用途。加密货币交易具有重大风险。过往表现不能保证未来收益。使用风险自负。
