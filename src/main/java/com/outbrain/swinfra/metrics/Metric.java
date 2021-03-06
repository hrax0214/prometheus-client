package com.outbrain.swinfra.metrics;

import com.outbrain.swinfra.metrics.data.MetricDataConsumer;
import com.outbrain.swinfra.metrics.utils.MetricType;

import java.util.List;

public interface Metric {
  MetricType getType();

  String getName();

  String getHelp();

  List<String> getLabelNames();

  void forEachMetricData(MetricDataConsumer consumer);
}
