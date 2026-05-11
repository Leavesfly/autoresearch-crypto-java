package com.autoresearch.crypto.strategy;

import com.autoresearch.crypto.data.MarketData;

import java.util.Map;

/**
 * 所有交易策略的抽象基类。
 */
public abstract class BaseStrategy {

    protected Map<String, Object> params;

    protected BaseStrategy(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * 从市场数据生成交易信号。
     *
     * @param data        OHLCV 市场数据
     * @param enableShort 是否允许做空
     * @return 信号代码数组（0=平仓，1=持仓，2=做多，3=做空）
     */
    public abstract int[] generateSignals(MarketData data, boolean enableShort);

    /**
     * 获取策略名称。
     */
    public abstract String name();

    /**
     * 获取当前参数。
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 更新参数。
     */
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    protected int getInt(String key, int defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    protected double getDouble(String key, double defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return defaultValue;
    }

    protected boolean getBool(String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }
}
