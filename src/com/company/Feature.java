package com.company;

import java.util.HashMap;

/**
 * Created by wuxian on 16/4/6.
 * 没有选择extends HashMap是为了便于以后添加更多功能,甚至修改数据结构
 */
public class Feature {
    // Trie<String, Double> feature = new PatriciaTrie<>(CharSequenceKeyAnalyzer.INSTANCE);
    HashMap<String, Double> feature;

    public Feature() {
        feature = new HashMap<>();
    }

    public Feature(Feature f) {
        feature = new HashMap<>(f.feature.size());
        for (HashMap.Entry<String, Double> e : f.feature.entrySet()) {
            feature.put(e.getKey(), e.getValue());
        }
    }

    public Feature(int hashMapSize) {
        feature = new HashMap<>(hashMapSize);
    }

    final public void add(String format, String s1, String s2) {
        StringBuilder k = new StringBuilder(format);
        k.append(s1+"_"+s2);
        Double v = feature.getOrDefault(k.toString(), 0.0);
        feature.put(k.toString(), v+1);
    }

    final public Feature PlusAssign(Feature f) {
        for (HashMap.Entry<String, Double> e : f.feature.entrySet()) {
            double v = feature.getOrDefault(e.getKey(), 0.0);
            feature.put(e.getKey(), v+e.getValue());
        }
        return this;
    }
    final public Feature Plus(Feature f) {
        Feature tmp = new Feature(this);
        tmp.PlusAssign(f);
        return tmp;
    }

    final public Feature MinusAssign(Feature f) {
        for (HashMap.Entry<String, Double> e : f.feature.entrySet()) {
            double v = feature.getOrDefault(e.getKey(), 0.0);
            feature.put(e.getKey(), v-e.getValue());
        }
        return this;
    }
    final public Feature Minus(Feature f) {
        Feature tmp = new Feature(this);
        tmp.MinusAssign(f);
        return tmp;
    }

    final public Feature DotProductAssign(double p) {
        for (HashMap.Entry<String, Double> e : feature.entrySet()) {
            feature.put(e.getKey(), e.getValue()*p);
        }
        return this;
    }
    final public Feature DotProduct(double p) {
        Feature tmp = new Feature(this);
        tmp.DotProductAssign(p);
        return tmp;
    }

    final public double absSum() {
        double ret = 0.0;
        for (double v : feature.values()) {
            ret += Math.abs(v);
        }
        return ret;
    }

    final public double get(String k) {
        return feature.get(k);
    }

    final public double getOrDefault(String k, double v) {
        return feature.getOrDefault(k, v);
    }

    @Override
    public String toString() {
        String ret = "";
        for (HashMap.Entry<String, Double> e : feature.entrySet()) {
            ret += String.format("%s:%.0f ", e.getKey(), e.getValue());
        }
        return ret;
    }

}