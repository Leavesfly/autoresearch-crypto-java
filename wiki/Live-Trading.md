# 实盘交易

## 概述

实盘交易模块提供与真实交易所的集成能力，支持 Nado DEX 和 OKX 两个交易所。

## 架构

```
LiveTradingEngine (主循环控制器)
    ├── ExchangeConnector (接口)
    │   ├── NadoConnector (Nado DEX 实现)
    │   └── OkxConnector (OKX V5 API 实现)
    ├── BaseStrategy (交易策略)
    └── TradingState (状态持久化)
```

## LiveTradingEngine

### 交易循环

```
while (running) {
    1. 计算下一根K线结束时间
    2. 休眠等待
    3. 获取最新市场数据 (connector.fetchLatestData)
    4. 生成交易信号 (strategy.generateSignals)
    5. 执行信号 (executeSignal)
    6. 保存交易状态
}
```

### 风险管理

- **仓位大小**：使用 95% 可用资金开仓
- **止损**：默认 3%
- **止盈**：默认 5%
- **信号映射**：0=平仓, 1=持有, 2=做多, 3=做空

## ExchangeConnector 接口

```java
public interface ExchangeConnector {
    String exchangeName();
    MarketData fetchLatestData(String interval, int limit);
    double fetchCurrentPrice();
    double[] fetchBestBidAsk();
    boolean openLong(double price, double notional);
    boolean openShort(double price, double notional);
    boolean closePosition(double price);
    PositionInfo queryPosition();
}
```

## Nado DEX 连接器

### 认证

- 使用 SHA-256 签名：`SHA256(payload + apiSecret)`
- Header: `X-API-KEY`, `X-SIGNATURE`

### 下单策略

| 场景 | 订单类型 | 原因 |
|------|----------|------|
| 开仓 | `POST_ONLY` | Maker 费率更低 |
| 平仓 | `IOC` | 市价快速成交 |

### API 端点

| 功能 | 端点 |
|------|------|
| K线数据 | `GET /api/v1/klines` |
| 当前价格 | `GET /api/v1/ticker` |
| 下单 | `POST /api/v1/order` |
| 持仓查询 | `GET /api/v1/position` |

## OKX 连接器

### 认证

- HMAC-SHA256 签名：`HMAC(timestamp + METHOD + path + body, apiSecret)`
- Headers: `OK-ACCESS-KEY`, `OK-ACCESS-SIGN`, `OK-ACCESS-TIMESTAMP`, `OK-ACCESS-PASSPHRASE`

### 合约计算

下单时将名义金额转换为合约张数：
```
contractSize = notional / (price × 0.01)  // 假设面值 0.01 ETH
```

### K线周期映射

| 通用格式 | OKX 格式 |
|----------|----------|
| `1m` | `1m` |
| `5m` | `5m` |
| `1h` | `1H` |
| `4h` | `4H` |
| `1d` | `1D` |

> 注意：OKX 返回的 K 线数据按时间倒序，需要反转为正序。

## TradingState

持久化交易状态到 JSON 文件，包含：
- 当前持仓方向和数量
- 入场价格
- 累计已实现盈亏
- 交易历史记录

## 使用示例

```java
// 创建策略
BaseStrategy strategy = BacktestEngine.createStrategy("hybridmm");

// 创建连接器
ExchangeConnector connector = new NadoConnector(
    "https://api.nado.xyz",
    "your-api-key",
    "your-api-secret",
    "ETH-PERP"
);

// 启动实盘
LiveTradingEngine engine = new LiveTradingEngine(
    strategy, connector, 100.0, "5m", true
);
engine.start();

// 优雅停止
engine.stop();
```

## 注意事项

- 实盘交易前务必在回测中验证策略表现
- 建议先用小资金测试
- 确保网络稳定，避免信号延迟
- API 密钥请妥善保管，不要提交到版本控制
