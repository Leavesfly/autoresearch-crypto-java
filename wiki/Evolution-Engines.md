# 进化引擎

## 概述

框架提供两种互补的策略进化引擎：

- **ATLAS**：多 Agent 竞争性进化，通过变异/交叉/权重调整持续优化
- **GEPA**：科学方法驱动的反思式学习，通过假设→实验→反思循环改进

## ATLAS 进化引擎

### 核心理念

维护 4 个独立的交易代理（Agent），模拟"超级投资者"竞争环境。表现差的代理向表现好的代理学习，表现好的代理进行探索性变异。

### 默认代理

| 代理 | 角色 | 策略类型 |
|------|------|----------|
| Alpha | 趋势跟踪 | Trend |
| Beta | 均值回归 | MeanReversion |
| Gamma | 网格交易 | Grid |
| Delta | 事件驱动 | EventDriven |

### 进化流程

```
1. 按最近评分排序所有代理
2. 最差代理 → 向最好代理学习（交叉 + 小幅变异）
3. 最好代理 → 探索性变异（大幅变异寻找更优解）
4. 重新平衡各代理权重（Softmax）
5. 检查死亡代理（长期停滞）
```

### 变异机制

在参数边界内添加高斯噪声：

```
newValue = currentValue + gaussian(0, scale × (max - min))
newValue = clamp(newValue, min, max)
```

- **离散参数**（如 window、period）自动取整
- **参数边界**由 `PARAM_BOUNDS` Map 定义

### 交叉机制

最差代理以 `crossoverRate`（默认 30%）概率逐参数继承最好代理的值：

```java
for (param : worstParams) {
    if (random() < crossoverRate) {
        result.put(param, bestParams.get(param));
    } else {
        result.put(param, worstParams.get(param));
    }
}
```

### 权重调整

使用温度控制的 Softmax 将评分转换为权重：

```
weight_i = exp(score_i / temperature) / Σ exp(score_j / temperature)
```

- `temperature = 2.0`：较高温度 → 权重分布更均匀
- 降低温度 → 赢者通吃

### 关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `mutationScale` | 0.10 | 变异强度 |
| `crossoverRate` | 0.30 | 交叉率 |
| `temperature` | 2.0 | Softmax 温度 |

---

## GEPA 反思引擎

### 核心理念

遵循科学方法循环：**观察 → 假设 → 实验 → 反思**

通过记录实验结果、分析成功/失败模式，为代理生成智能化的参数调整建议。

### 假设生成策略

根据代理历史表现选择不同策略：

| 条件 | 策略 | 说明 |
|------|------|------|
| 无历史日志 | 默认假设 | 针对策略类型的预设调整 |
| 连续失败 ≥ 3 次 | 探索性假设 | 大幅度参数变化，跳出局部最优 |
| 有成功经验 | 增量假设 | 基于上次成功方向继续微调 |

### 假设结构

```java
record Hypothesis(
    String text,                    // 假设描述
    String agent,                   // 目标代理名称
    Map<String, Object> paramChanges, // 参数调整建议
    String rationale                // 调整理由
)
```

### 元反思机制

每 5 次实验执行一次元反思，分析整体改进率：

| 改进率 | 建议 |
|--------|------|
| < 20% | 切换策略类型或时间框架 |
| 20% ~ 60% | 平衡探索和利用 |
| > 60% | 继续沿当前方向小步探索 |

### 实验日志

`ExperimentLog` 记录每次实验的完整信息：
- 代理名称
- 参数变更（前/后）
- 评分变化（改善/恶化/持平）
- 假设文本和理由
- 时间戳

---

## Agent 类

```java
public class Agent {
    String name;                    // 代理名称
    String style;                   // 交易风格描述
    String strategyClassName;       // 策略类全名
    Map<String, Object> params;     // 当前参数
    List<Double> scoreHistory;      // 评分历史
    double weight;                  // 当前权重（默认 0.25）
    int generation;                 // 进化代数
}
```

关键方法：
- `recentScore(int window)` — 最近 N 轮的平均评分（默认 window=3）
- `copy()` — 深拷贝代理实例

### 死亡代理检测

当代理最近 N 次评分停滞（`max - min < epsilon`）时，判定为"死亡代理"，需要重新初始化或大幅变异。

---

## 两引擎协作

```
ATLAS (宏观进化)
  │ 提供评分排名和代理状态
  ▼
GEPA (微观反思)
  │ 生成智能假设和参数建议
  ▼
Agent 执行实验 → 记录结果 → 反馈给两引擎
```

- **ATLAS** 负责宏观层面的种群进化：谁学习谁、谁探索、权重如何分配
- **GEPA** 负责微观层面的参数调整：为什么调、调什么、调多少
